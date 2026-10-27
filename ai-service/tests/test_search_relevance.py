import json
import os
from pathlib import Path
from time import perf_counter

import pytest


@pytest.mark.skipif(os.getenv("RUN_EMBEDDING_TESTS")!="1",reason="Real E5 relevance evaluation is opt-in")
def test_vietnamese_questions_find_labeled_sources_in_top_five():
    from app.embedding.encoder import E5Encoder
    from app.embedding.settings import EmbeddingSettings
    data=json.loads((Path(__file__).resolve().parents[2]/"infra/testing/search/relevance-fixtures.json").read_text(encoding="utf-8"))
    encoder=E5Encoder(EmbeddingSettings())
    start=perf_counter()
    passages=encoder.embed([doc["text"] for doc in data["documents"]])
    loaded=perf_counter()
    queries=encoder.embed([q["query"] for q in data["questions"]],input_type="query")
    embedded=perf_counter()
    misses=[]
    for question,vector in zip(data["questions"],queries):
        # The adapter L2-normalizes vectors; cosine similarity is their dot product.
        order=sorted(range(len(passages)),key=lambda i:sum(a*b for a,b in zip(vector,passages[i])),reverse=True)[:5]
        ids=[data["documents"][i]["id"] for i in order]
        if question["expectedDocumentId"] not in ids:misses.append(question["expectedDocumentId"])
    hits=len(queries)-len(misses)
    print(f"Vietnamese E5 hit@5={hits}/{len(queries)}; load+passages={loaded-start:.2f}s; warm_queries={embedded-loaded:.2f}s; misses={misses}")
    assert hits>=8,misses
