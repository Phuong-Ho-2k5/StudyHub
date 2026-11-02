import anyio
from .provider import ConceptProvider
from .prompt_builder import build_prompt
from app.generation.provider import ProviderError


class ConceptExtractionService:
    def __init__(self, settings):
        self.settings = settings

    async def extract(self, request):
        try:
            with anyio.fail_after(self.settings.timeout_seconds):
                async with ConceptProvider(self.settings) as provider:
                    messages = build_prompt(request)
                    count = await provider.count_input_tokens(messages)
                    if (count > self.settings.max_input_tokens or
                            count + self.settings.max_output_tokens > self.settings.context_window_tokens):
                        raise ProviderError(422, 'TOKEN_BUDGET_EXCEEDED')
                    result = await provider.generate(messages, self.settings.max_output_tokens)
                    if len(result.suggestions) > request.maxSuggestions:
                        raise ProviderError(502)
                    sources = {c.chunkId: c.content for c in request.contexts}
                    for suggestion in result.suggestions:
                        seen = set()
                        for evidence in suggestion.evidence:
                            if (evidence.chunkId in seen or evidence.chunkId not in sources or
                                    evidence.quote not in sources[evidence.chunkId]):
                                raise ProviderError(502)
                            seen.add(evidence.chunkId)
                    return result
        except TimeoutError:
            raise ProviderError(504) from None
