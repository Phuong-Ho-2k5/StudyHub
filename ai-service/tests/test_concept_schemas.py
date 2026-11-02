import pytest
from pydantic import ValidationError
from app.concepts.schemas import ConceptExtractionRequest, ConceptOutput


def payload():
    return {"maxSuggestions": 5, "contexts": [{"chunkId": 1, "documentId": 2,
        "documentTitle": "Database", "chunkIndex": 0, "content": "Atomicity keeps a transaction atomic.",
        "pageStart": 1, "pageEnd": 1}]}


def suggestion(**changes):
    return {"name": "Atomicity", "description": "A transaction is atomic.",
        "evidence": [{"chunkId": 1, "quote": "Atomicity"}], **changes}


@pytest.mark.parametrize("change", [{"maxSuggestions": True}, {"maxSuggestions": "5"},
    {"maxSuggestions": 0}, {"maxSuggestions": 11}, {"maxSuggestions": None},
    {"contexts": []}, {"model": "override"}])
def test_rejects_invalid_request(change):
    with pytest.raises(ValidationError):
        ConceptExtractionRequest.model_validate({**payload(), **change})


def test_rejects_duplicate_ids_and_mixed_documents():
    for second in [payload()["contexts"][0], {**payload()["contexts"][0], "chunkId": 3, "documentId": 4}]:
        with pytest.raises(ValidationError):
            ConceptExtractionRequest.model_validate({**payload(), "contexts": payload()["contexts"] + [second]})


@pytest.mark.parametrize("change", [{"name": "\u00a0"}, {"name": "x" * 151},
    {"description": " "}, {"description": "x" * 1001}, {"evidence": []},
    {"evidence": [{"chunkId": 1, "quote": " "}]}, {"confidence": 100}])
def test_rejects_invalid_suggestion(change):
    with pytest.raises(ValidationError):
        ConceptOutput.model_validate({"suggestions": [suggestion(**change)]})


def test_empty_result_is_valid_but_duplicate_names_are_not():
    assert ConceptOutput(suggestions=[]).suggestions == []
    with pytest.raises(ValidationError):
        ConceptOutput(suggestions=[suggestion(), suggestion(name=" Atomicity ")])
