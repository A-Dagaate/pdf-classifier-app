"""
main.py — FastAPI sidecar for ISTQB STE study tool.
Runs ingest on startup, then serves RAG query endpoints.

Endpoints:
  GET  /health
  GET  /query?q=<question>
  GET  /section/{section_id}
  GET  /explain/{q_number}
  GET  /questions              — list all 40 questions metadata
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
import json
import random

from ingest import ingest
from query import retrieve_by_question, retrieve_by_section, explain_exam_question

DATASET_PATH   = "/app/questions_dataset.json"
K1_SUITE_PATH  = "/app/k1_questions.json"


@asynccontextmanager
async def lifespan(app: FastAPI):
    ingest()
    yield


app = FastAPI(
    title="ISTQB STE Study Tool",
    description="RAG-powered exam tutor for ISTQB Security Test Engineer v1.0.1",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok", "service": "ste-study"}


@app.get("/query")
def query(q: str = Query(..., description="Question to ask the syllabus")):
    contexts = retrieve_by_question(q)
    if not contexts:
        raise HTTPException(status_code=404, detail="No relevant sections found")
    from query import generate
    answer = generate(q, contexts)
    return {
        "question": q,
        "sources": [{"section_id": c["section_id"], "title": c["title"]} for c in contexts],
        "answer": answer,
    }


@app.get("/section/{section_id}")
def section(section_id: str):
    result = retrieve_by_section(section_id)
    if not result:
        raise HTTPException(status_code=404, detail=f"Section {section_id} not found")
    return result


@app.get("/explain/{q_number}")
def explain(q_number: int):
    result = explain_exam_question(q_number)
    if "error" in result:
        raise HTTPException(status_code=404, detail=result["error"])
    return result


@app.get("/questions")
def questions():
    with open(DATASET_PATH) as f:
        return json.load(f)


@app.get("/quiz/k1")
def quiz_k1(shuffle: bool = True):
    """Return all K1 vocabulary MCQs, optionally shuffled."""
    try:
        with open(K1_SUITE_PATH) as f:
            data = json.load(f)
    except FileNotFoundError:
        raise HTTPException(
            status_code=503,
            detail="K1 suite not yet generated. Run generate_k1_suite.py first."
        )
    if shuffle:
        random.shuffle(data)
    return {"count": len(data), "questions": data}


@app.get("/quiz/k1/{term}")
def quiz_k1_term(term: str):
    """Return the K1 MCQ for a specific term."""
    try:
        with open(K1_SUITE_PATH) as f:
            data = json.load(f)
    except FileNotFoundError:
        raise HTTPException(status_code=503, detail="K1 suite not yet generated.")
    match = next((q for q in data if q["term"].lower() == term.lower()), None)
    if not match:
        raise HTTPException(status_code=404, detail=f"Term '{term}' not found in K1 suite.")
    return match
