import anyio
from app.generation.provider import ProviderError
from .provider import AdviceProvider
from .prompt_builder import build_prompt
from .schemas import AdviceResponse, INSUFFICIENT_ANSWER


class AdviceService:
    def __init__(self, settings):
        self.settings = settings

    async def generate(self, request):
        try:
            with anyio.fail_after(self.settings.timeout_seconds):
                async with AdviceProvider(self.settings) as provider:
                    bundle = await build_prompt(request, self.settings, provider)
                    result = await provider.generate(bundle.messages, self.settings.max_output_tokens)
                    ids = result.usedLearningEvidenceIds
                    if len(ids) != len(set(ids)) or not set(ids).issubset(bundle.included_fact_ids):
                        raise ProviderError(502)
                    chunks = {c.chunkId: c for c in bundle.included_contexts}
                    seen = set()
                    for evidence in result.evidence:
                        if (evidence.chunkId in seen or evidence.chunkId not in chunks
                                or evidence.quote not in chunks[evidence.chunkId].content):
                            raise ProviderError(502)
                        seen.add(evidence.chunkId)
                    if ((result.status == "ANSWERED" and (not ids or not result.evidence))
                            or (result.status == "LEARNING_DATA_ONLY" and (not ids or result.evidence))
                            or (result.status == "INSUFFICIENT_CONTEXT" and (ids or result.evidence))):
                        raise ProviderError(502)
                    return AdviceResponse(**{**result.model_dump(), "answer": INSUFFICIENT_ANSWER
                        if result.status == "INSUFFICIENT_CONTEXT" else result.answer.strip()}, model=self.settings.model)
        except TimeoutError:
            raise ProviderError(504) from None
