import asyncio
import os
import pytest
from app.advice.schemas import AdviceRequest
from app.advice.service import AdviceService
from app.generation.settings import LlmSettings
from app.generation.provider import ProviderError
from test_advice_service import payload

pytestmark = pytest.mark.skipif(not os.getenv("ADVICE_SIMULATION_URL"), reason="Requires local Postman simulation")


def generate(marker="", **settings):
    config = LlmSettings(base_url=os.environ["ADVICE_SIMULATION_URL"] + "/openai/v1", api_key="simulation-key", **settings)
    return asyncio.run(AdviceService(config).generate(AdviceRequest.model_validate(payload(question=marker+" Tôi nên ôn gì?"))))


def test_real_http_adapter_accepts_grounded_and_learning_only():
    assert generate().status == "ANSWERED"
    assert generate("[learning-only]").status == "LEARNING_DATA_ONLY"
    assert generate("[insufficient]").status == "INSUFFICIENT_CONTEXT"


@pytest.mark.parametrize("marker,status", [("budget",422),("malformed",502),("unknown-fact",502),
    ("fake-id",502),("fake-quote",502),("unavailable",503),("rate-limit",503),("timeout",504)])
def test_real_http_failure_mapping(marker,status):
    with pytest.raises(ProviderError) as error: generate("["+marker+"]",timeout_seconds=.5 if marker=="timeout" else 10)
    assert error.value.status_code == status
