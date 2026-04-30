"""
generate_k1_suite.py
=====================
Extracts K1 vocabulary terms from the ISTQB STE syllabus, retrieves their
definitions via RAG, then uses Claude to generate one ISTQB-style MCQ per term.

Output: k1_questions.json  (generated once, served by the API at runtime)

USAGE:
    source venv/bin/activate
    export ANTHROPIC_API_KEY=sk-ant-...
    python3 generate_k1_suite.py

Takes ~2-3 minutes (one API call per term, ~35 terms).
"""

import os
import json
import re
import time
import fitz
import chromadb
from chromadb.utils import embedding_functions
import anthropic

SYLLABUS_PATH = "./ISTQB_STE_v1.0.1-Syllabus.pdf"
DB_PATH       = "./chroma_db"
COLLECTION    = "ste_syllabus"
OUTPUT_PATH   = "./k1_questions.json"

# ─── Terms to skip (too vague or abbreviations without standalone meaning) ───
SKIP = {"none", "(cwe)", "exposures (cve)"}

# ─── Helpers ──────────────────────────────────────────────────────────────────

def extract_terms():
    doc = fitz.open(SYLLABUS_PATH)
    full_text = "\n".join(page.get_text() for page in doc)

    kw_blocks = re.findall(
        r"Keywords\s*\n(.*?)\n\s*(?:Security Keywords|Learning Objectives)",
        full_text, re.DOTALL
    )
    sec_kw_blocks = re.findall(
        r"Security Keywords\s*\n(.*?)\n\s*(?:Learning Objectives)",
        full_text, re.DOTALL
    )
    terms = set()
    for block in kw_blocks + sec_kw_blocks:
        for t in block.replace("\n", ",").split(","):
            t = t.strip().lower()
            if t and t not in SKIP and len(t) > 2:
                terms.add(t)
    return sorted(terms)


def get_contexts(term: str, top_k: int = 2) -> list[dict]:
    ef = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="all-MiniLM-L6-v2"
    )
    client = chromadb.PersistentClient(path=DB_PATH)
    col = client.get_collection(name=COLLECTION, embedding_function=ef)
    results = col.query(query_texts=[f"definition of {term}"], n_results=top_k)
    return [
        {
            "text": results["documents"][0][i],
            "section_id": results["metadatas"][0][i]["section_id"],
            "title": results["metadatas"][0][i]["title"],
        }
        for i in range(len(results["documents"][0]))
    ]


def generate_mcq(term: str, contexts: list[dict]) -> dict | None:
    context_block = "\n\n---\n\n".join(
        f"[Section {c['section_id']} — {c['title']}]\n{c['text'][:800]}"
        for c in contexts
    )
    prompt = f"""You are writing K1-level ISTQB exam questions. K1 means pure recall — the candidate must remember the definition of a term.

Generate ONE multiple-choice question for the term: "{term}"

Rules:
- Question stem: "Which of the following BEST defines [term]?" or "What is [term]?"
- 4 options (a, b, c, d)
- Exactly ONE correct answer — the accurate definition from the syllabus context
- Three plausible distractors — wrong but believable (common confusions, related but incorrect concepts)
- Do NOT use "all of the above" or "none of the above"
- Keep each option to one sentence

Syllabus context:
{context_block}

Respond in this exact JSON format (no markdown, no extra text):
{{
  "term": "{term}",
  "question": "...",
  "options": {{"a": "...", "b": "...", "c": "...", "d": "..."}},
  "correct": "a",
  "explanation": "One sentence explaining why the correct answer is right."
}}"""

    client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])
    message = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=512,
        messages=[{"role": "user", "content": prompt}],
    )
    raw = message.content[0].text.strip()
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # Try to extract JSON block if Claude added surrounding text
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if match:
            return json.loads(match.group())
        print(f"  WARNING: could not parse JSON for term '{term}'")
        return None


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    terms = extract_terms()
    print(f"Found {len(terms)} K1 terms: {terms}\n")

    results = []
    for i, term in enumerate(terms):
        print(f"[{i+1}/{len(terms)}] Generating MCQ for: {term}")
        contexts = get_contexts(term)
        mcq = generate_mcq(term, contexts)
        if mcq:
            mcq["sources"] = [c["section_id"] for c in contexts]
            results.append(mcq)
        time.sleep(0.3)  # gentle rate limiting

    with open(OUTPUT_PATH, "w") as f:
        json.dump(results, f, indent=2)

    print(f"\nDone. {len(results)} K1 questions saved to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
