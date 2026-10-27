import math

from .settings import EmbeddingSettings


class EmbeddingInputError(ValueError):
    """Input cannot be embedded without silently truncating source text."""


def validate_vectors(vectors, count: int, dimensions: int):
    if not isinstance(vectors, list) or len(vectors) != count:
        raise ValueError("Embedding count mismatch")
    for vector in vectors:
        if not isinstance(vector, list) or len(vector) != dimensions:
            raise ValueError("Embedding dimension mismatch")
        if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in vector):
            raise ValueError("Embedding contains non-finite values")
        if not any(v != 0 for v in vector):
            raise ValueError("Embedding is a zero vector")


class E5Encoder:
    def __init__(self, settings: EmbeddingSettings):
        self.settings = settings
        self.tokenizer = None
        self.model = None

    def _load(self):
        import torch
        from transformers import AutoModel, AutoTokenizer

        torch.set_num_threads(4)
        self.tokenizer = AutoTokenizer.from_pretrained(
            self.settings.model, revision=self.settings.revision, trust_remote_code=False,
        )
        model = AutoModel.from_pretrained(
            self.settings.model, revision=self.settings.revision,
            trust_remote_code=False, use_safetensors=True,
        )
        if model.config.hidden_size != self.settings.dimensions:
            raise ValueError("Configured embedding dimensions do not match the model")
        self.model = model.to("cpu").eval()

    def embed(self, texts: list[str], input_type: str = "passage") -> list[list[float]]:
        import torch
        import torch.nn.functional as functional

        if input_type not in ("passage", "query"):
            raise EmbeddingInputError("Invalid embedding input type")
        if not 1 <= len(texts) <= 32 or any(not text.strip() or len(text) > 16384 for text in texts):
            raise EmbeddingInputError("Invalid embedding batch")
        if self.model is None:
            self._load()
        inputs = [input_type + ": " + text for text in texts]
        lengths = self.tokenizer(inputs, truncation=False, add_special_tokens=True)["input_ids"]
        if any(len(ids) > 512 for ids in lengths):
            raise EmbeddingInputError("Embedding input exceeds 512 tokens including prefix")
        batch = self.tokenizer(inputs, padding=True, truncation=False, return_tensors="pt")
        with torch.inference_mode():
            output = self.model(**batch).last_hidden_state
            mask = batch["attention_mask"].bool()
            pooled = output.masked_fill(~mask[..., None], 0.).sum(dim=1) / mask.sum(dim=1)[..., None]
            vectors = functional.normalize(pooled, p=2, dim=1).tolist()
        validate_vectors(vectors, len(texts), self.settings.dimensions)
        return vectors
