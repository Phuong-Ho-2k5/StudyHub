import json
import pytest

from app.generation.provider import LlmProvider, ProviderError
from app.generation.settings import LlmSettings


def response(content=None, **overrides):
    return {"status": "completed", "model": "gpt-4.1-mini-2025-04-14", "output": [
        {"type": "message", "role": "assistant", "status": "completed", "content": [
            {"type": "output_text", "text": json.dumps(content or {"status": "ANSWERED", "answer": "Atomicity"})}
        ]}], **overrides}


def test_normalizes_provider_snapshot_to_configured_alias():
    result = LlmProvider(LlmSettings(api_key="test-key")).parse_response(response())
    assert result.status == "ANSWERED" and result.answer == "Atomicity"
    assert result.model == "gpt-4.1-mini"


@pytest.mark.parametrize("body", [None, {}, response(status="incomplete"), response(model="different"),
    response({"status": "OTHER", "answer": "a"}), response({"status": "ANSWERED", "answer": " "}),
    response({"status": "ANSWERED", "answer": "a", "citations": [999]}),
    response(output=[{"type": "message", "content": [{"type": "refusal", "refusal": "private"}]}]),
    response(output=[{"type": "function_call", "name": "execute"}]),
    response(output=[{"type": "message", "content": [{"type": "output_text", "text": "invalid"}]}])])
def test_invalid_truncated_refused_or_unexpected_output_is_gateway_error(body):
    with pytest.raises(ProviderError) as error:
        LlmProvider(LlmSettings(api_key="test-key")).parse_response(body)
    assert error.value.status_code == 502
    assert "private" not in str(error.value)


@pytest.mark.parametrize("updates", [dict(api_key=""), dict(provider="other"), dict(timeout_seconds=0),
    dict(base_url="http://external.example/v1"), dict(base_url="https://user:password@example.com"),
    dict(max_input_tokens=1048576), dict(max_output_tokens=0)])
def test_invalid_configuration_is_rejected(updates):
    with pytest.raises(ValueError):
        LlmSettings(**{**dict(api_key="test-key"), **updates}).validate()


def test_settings_do_not_expose_credentials_in_repr():
    assert "private-key" not in repr(LlmSettings(api_key="private-key"))
