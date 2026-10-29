"""Verify provenance against the contexts actually included in the prompt."""
from .schemas import Evidence, GenerationResult, RagContext
from .provider import ProviderError


def validate_evidence(result: GenerationResult, contexts: list[RagContext]) -> list[Evidence]:
    if (result.status == "ANSWERED") != bool(result.evidence):
        raise ProviderError(502)
    sources = {context.chunkId: context.content for context in contexts}
    seen = set()
    for evidence in result.evidence:
        if (evidence.chunkId in seen or evidence.chunkId not in sources
                or evidence.quote not in sources[evidence.chunkId]):
            raise ProviderError(502)
        seen.add(evidence.chunkId)
    return result.evidence
