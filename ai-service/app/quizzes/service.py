import anyio
from app.generation.provider import ProviderError
from .provider import QuizProvider
from .prompt_builder import build_prompt
from .schemas import QuizGenerationResponse


class QuizGenerationService:
    def __init__(self, settings):
        self.settings = settings

    async def generate(self, request):
        try:
            with anyio.fail_after(self.settings.timeout_seconds):
                async with QuizProvider(self.settings) as provider:
                    messages = build_prompt(request)
                    count = await provider.count_input_tokens(messages)
                    if (count > self.settings.max_input_tokens or
                            count + self.settings.max_output_tokens > self.settings.context_window_tokens):
                        raise ProviderError(422, 'TOKEN_BUDGET_EXCEEDED')
                    output = await provider.generate(messages, self.settings.max_output_tokens)
                    if output.status == 'INSUFFICIENT_CONTENT':
                        raise ProviderError(422, 'INSUFFICIENT_CONTENT')
                    if len(output.questions) != request.questionCount:
                        raise ProviderError(502)
                    sources = {c.chunkId: c.content for c in request.contexts}
                    for question in output.questions:
                        seen = set()
                        for e in question.evidence:
                            if e.chunkId in seen or e.chunkId not in sources or e.quote not in sources[e.chunkId]:
                                raise ProviderError(502)
                            seen.add(e.chunkId)
                    return QuizGenerationResponse(model=self.settings.model, questions=output.questions)
        except TimeoutError:
            raise ProviderError(504) from None
