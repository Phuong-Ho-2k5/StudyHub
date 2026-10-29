import anyio
from .prompt_builder import build_prompt
from .provider import LlmProvider, ProviderError
from .schemas import RagAnswerResponse, INSUFFICIENT_ANSWER
from .citations import validate_evidence


class RagService:
    def __init__(self, settings): self.settings = settings

    async def answer(self, request):
        try:
            # Total deadline covers token counting and generation, not each call separately.
            with anyio.fail_after(self.settings.timeout_seconds):
                async with LlmProvider(self.settings) as provider:
                    bundle = await build_prompt(request.question, request.contexts, self.settings, provider)
                    result = await provider.generate(bundle.messages, self.settings.max_output_tokens)
                    evidence = validate_evidence(result, bundle.included_contexts)
                    return RagAnswerResponse(status=result.status,
                        answer=INSUFFICIENT_ANSWER if result.status == "INSUFFICIENT_CONTEXT" else result.answer,
                        model=result.model, promptVersion=bundle.prompt_version, evidence=evidence)
        except TimeoutError:
            raise ProviderError(504) from None
