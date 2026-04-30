"""
query.py — RAG retrieval and generation logic (no CLI, imported by main.py).
"""

import os
import json
import chromadb
from chromadb.utils import embedding_functions
import anthropic

DB_PATH = "/app/chroma_db"
COLLECTION_NAME = "ste_syllabus"
DATASET_PATH = "/app/questions_dataset.json"


def get_collection():
    ef = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="all-MiniLM-L6-v2"
    )
    client = chromadb.PersistentClient(path=DB_PATH)
    return client.get_collection(name=COLLECTION_NAME, embedding_function=ef)


def retrieve_by_question(question: str, top_k: int = 3) -> list[dict]:
    col = get_collection()
    results = col.query(query_texts=[question], n_results=top_k)
    return [
        {
            "text": results["documents"][0][i],
            "section_id": results["metadatas"][0][i]["section_id"],
            "title": results["metadatas"][0][i]["title"],
        }
        for i in range(len(results["documents"][0]))
    ]


def retrieve_by_section(section_id: str) -> dict | None:
    col = get_collection()
    results = col.get(ids=[section_id], include=["documents", "metadatas"])
    if not results["documents"]:
        return None
    return {
        "text": results["documents"][0],
        "section_id": results["metadatas"][0]["section_id"],
        "title": results["metadatas"][0]["title"],
    }


def generate(question: str, contexts: list[dict]) -> str:
    context_block = "\n\n---\n\n".join(
        f"[Section {c['section_id']} — {c['title']}]\n{c['text']}"
        for c in contexts
    )
    prompt = (
        "You are an expert ISTQB Security Test Engineer exam tutor. "
        "Answer the question below using ONLY the provided syllabus context. "
        "Be precise and explain WHY the correct answer is correct at the K2/K3 level. "
        "If the context does not contain enough information, say so.\n\n"
        f"SYLLABUS CONTEXT:\n{context_block}\n\n"
        f"QUESTION: {question}\n\nAnswer:"
    )
    client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])
    message = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=1024,
        messages=[{"role": "user", "content": prompt}],
    )
    return message.content[0].text


def explain_exam_question(q_number: int) -> dict:
    with open(DATASET_PATH) as f:
        dataset = json.load(f)
    match = next((q for q in dataset if q["q"] == q_number), None)
    if not match:
        return {"error": f"Question {q_number} not found"}

    section = retrieve_by_section(match["syllabus_section"])
    contexts = [section] if section else []
    contexts += retrieve_by_question(match["stem"], top_k=2)

    seen, unique = set(), []
    for c in contexts:
        if c["section_id"] not in seen:
            seen.add(c["section_id"])
            unique.append(c)

    prompt = (
        f"Exam Question {q_number} ({match['points']} pt, Select {match['select']})\n"
        f"Core concern: {match['core_concern']}\n"
        f"Bloom level: {match['bloom']}\n\n"
        f"Stem: {match['stem']}\n\n"
        "Explain what this question is testing, what knowledge from the syllabus "
        "section the candidate needs, and how to approach answering it correctly."
    )
    answer = generate(prompt, unique)
    return {
        "question": match,
        "sources": [{"section_id": c["section_id"], "title": c["title"]} for c in unique],
        "explanation": answer,
    }
