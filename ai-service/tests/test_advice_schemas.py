import pytest
from pydantic import ValidationError
from app.advice.schemas import AdviceRequest, AdviceOutput
from test_advice_service import payload, output


@pytest.mark.parametrize("changes", [{"question": True}, {"question": " "}, {"question": "x"*2001},
    {"contexts": [payload()["contexts"][0]]*11}, {"learningEvidence": []}, {"unexpected": 1},
    {"learningEvidence": payload()["learningEvidence"]*2}])
def test_request_is_strict_and_bounded(changes):
    with pytest.raises(ValidationError): AdviceRequest.model_validate(payload(**changes))


@pytest.mark.parametrize("changes", [{"answer": " "}, {"answer": "x"*32769},
    {"status": "OTHER"}, {"extra": True}, {"evidence": [{"chunkId": True, "quote": "Atomicity"}]}])
def test_output_is_strict(changes):
    with pytest.raises(ValidationError): AdviceOutput.model_validate(output(**changes))
