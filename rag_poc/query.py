"""
query.py
========
Retrieves relevant chunks from ChromaDB and generates an answer using Claude.
This is the RAG pipeline: Retrieve → Augment → Generate.

USAGE:
    source venv/bin/activate
    export ANTHROPIC_API_KEY=your_key
    python query.py "What is the minimum code coverage threshold?"
"""

import sys
import os
import chromadb
from chromadb.utils import embedding_functions
import anthropic

DB_PATH = "./chroma_db"
COLLECTION_NAME = "pdf_classifier_docs"
TOP_K = 3


def retrieve(question: str, top_k: int = TOP_K) -> list[dict]:
    client = chromadb.PersistentClient(path=DB_PATH)
    ef = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="all-MiniLM-L6-v2"
    )
    collection = client.get_collection(name=COLLECTION_NAME, embedding_function=ef)

    results = collection.query(query_texts=[question], n_results=top_k)
    chunks = []
    for i, doc in enumerate(results["documents"][0]):
        chunks.append({
            "text": doc,
            "source": results["metadatas"][0][i]["source"],
            "distance": results["distances"][0][i],
        })
    return chunks


def generate(question: str, contexts: list[dict]) -> str:
    context_block = "\n\n---\n\n".join(
        f"[Source: {c['source']}]\n{c['text']}" for c in contexts
    )
    prompt = (
        f"You are a helpful assistant answering questions based only on the provided context.\n\n"
        f"CONTEXT:\n{context_block}\n\n"
        f"QUESTION: {question}\n\n"
        f"Answer using only information from the context above. "
        f"If the context does not contain enough information, say so."
    )

    client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])
    message = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=512,
        messages=[{"role": "user", "content": prompt}],
    )
    return message.content[0].text


def rag_query(question: str, verbose: bool = True) -> dict:
    contexts = retrieve(question)
    answer = generate(question, contexts)

    if verbose:
        print(f"\nQUESTION: {question}")
        print(f"\nANSWER:\n{answer}")
        print(f"\nSOURCES USED:")
        for c in contexts:
            print(f"  - {c['source']} (distance: {c['distance']:.4f})")

    return {"question": question, "answer": answer, "contexts": contexts}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        question = "What is the minimum code coverage threshold?"
    else:
        question = " ".join(sys.argv[1:])

    if not os.environ.get("ANTHROPIC_API_KEY"):
        print("ERROR: ANTHROPIC_API_KEY environment variable not set.")
        sys.exit(1)

    rag_query(question)
