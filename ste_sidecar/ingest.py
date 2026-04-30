"""
ingest.py — runs once at container startup if chroma_db is empty.
Parses ISTQB STE syllabus by section, embeds into ChromaDB.
"""

import fitz
import re
import chromadb
from chromadb.utils import embedding_functions

SYLLABUS_PATH = "/app/ISTQB_STE_v1.0.1-Syllabus.pdf"
DB_PATH = "/app/chroma_db"
COLLECTION_NAME = "ste_syllabus"


def extract_text_by_page(pdf_path):
    doc = fitz.open(pdf_path)
    return [(i + 1, page.get_text()) for i, page in enumerate(doc)]


def chunk_by_section(pages):
    full_text = "\n".join(text for _, text in pages)
    matches = list(re.finditer(r"(?m)^(\d+\.\d+(?:\.\d+)?)\.\s*\n(.+?)$", full_text))

    chunks = []
    for i, match in enumerate(matches):
        raw_id = match.group(1).strip()
        section_title = match.group(2).strip()
        if any(x in section_title for x in ["Certified Tester", "© International", "Page "]):
            continue
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(full_text)
        text = full_text[start:end].strip()
        if len(text) < 150:
            continue
        chunks.append({
            "section_id": raw_id.rstrip("."),
            "title": section_title,
            "text": f"Section {raw_id} — {section_title}\n\n{text}",
        })
    return chunks


def already_ingested():
    try:
        client = chromadb.PersistentClient(path=DB_PATH)
        col = client.get_collection(COLLECTION_NAME)
        return col.count() > 0
    except Exception:
        return False


def ingest():
    if already_ingested():
        print("ChromaDB already populated — skipping ingest.")
        return

    print("Ingesting ISTQB STE syllabus...")
    pages = extract_text_by_page(SYLLABUS_PATH)
    chunks = chunk_by_section(pages)

    merged = {}
    for c in chunks:
        sid = c["section_id"]
        if sid in merged:
            merged[sid]["text"] += "\n\n" + c["text"]
        else:
            merged[sid] = c
    chunks = list(merged.values())

    ef = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="all-MiniLM-L6-v2"
    )
    client = chromadb.PersistentClient(path=DB_PATH)
    try:
        client.delete_collection(COLLECTION_NAME)
    except Exception:
        pass
    collection = client.create_collection(name=COLLECTION_NAME, embedding_function=ef)
    collection.add(
        ids=[c["section_id"] for c in chunks],
        documents=[c["text"] for c in chunks],
        metadatas=[{"section_id": c["section_id"], "title": c["title"]} for c in chunks],
    )
    print(f"Ingested {len(chunks)} sections into '{COLLECTION_NAME}'")


if __name__ == "__main__":
    ingest()
