import copy
import pytest
from pydantic import ValidationError
from app.quizzes.schemas import QuizGenerationRequest, QuizOutput


def question(**changes):
    return {'text': 'What does Atomicity ensure?', 'options': [
        {'text': text, 'correct': i == 0} for i, text in enumerate(['All or nothing', 'Sorting', 'Indexing', 'Caching'])],
        'evidence': [{'chunkId': 1, 'quote': 'Atomicity'}], **changes}


def payload(**changes):
    return {'questionCount': 1, 'contexts': [{'chunkId': 1, 'documentId': 11, 'documentTitle': 'Transactions',
        'chunkIndex': 0, 'content': 'Atomicity keeps changes atomic.', 'pageStart': 1, 'pageEnd': 1}], **changes}


def test_accepts_multiple_documents_and_strict_mcq():
    data = payload()
    data['contexts'].append({**data['contexts'][0], 'chunkId': 2, 'documentId': 12})
    assert len(QuizGenerationRequest.model_validate(data).contexts) == 2
    assert QuizOutput.model_validate({'status': 'GENERATED', 'questions': [question()]}).questions[0].options[0].correct


@pytest.mark.parametrize('count', [True, '1', 1.5, 0, 11, None])
def test_strict_count(count):
    with pytest.raises(ValidationError): QuizGenerationRequest.model_validate(payload(questionCount=count))


@pytest.mark.parametrize('change', [dict(text=' '), dict(text='x' * 501), dict(evidence=[]),
    dict(evidence=[{'chunkId': 1, 'quote': ' '}]), dict(ownerId=4),
    dict(options=[{'text': str(i), 'correct': False} for i in range(4)]),
    dict(options=[{'text': str(i), 'correct': True} for i in range(4)]),
    dict(options=[{'text': 'Same', 'correct': i == 0} for i in range(4)]),
    dict(options=[{'text': str(i), 'correct': i == 0} for i in range(3)]),
    dict(options=[{'text': 'x' * 501, 'correct': True}] + question()['options'][1:])])
def test_rejects_invalid_question(change):
    with pytest.raises(ValidationError): QuizOutput.model_validate({'status': 'GENERATED', 'questions': [question(**change)]})


def test_duplicate_questions_context_and_inconsistent_insufficient_output():
    for body in [{'status': 'GENERATED', 'questions': [question(), question(text=' WHAT DOES ATOMICITY ENSURE? ')]},
                 {'status': 'INSUFFICIENT_CONTENT', 'questions': [question()]},
                 {'status': 'GENERATED', 'questions': []}]:
        with pytest.raises(ValidationError): QuizOutput.model_validate(body)
    with pytest.raises(ValidationError): QuizGenerationRequest.model_validate(payload(contexts=payload()['contexts'] * 2))
    assert QuizOutput.model_validate({'status': 'INSUFFICIENT_CONTENT', 'questions': []}).questions == []
