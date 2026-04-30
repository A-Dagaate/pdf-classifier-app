"""
evaluate.py
===========
Runs RAGAs evaluation over the golden dataset.
Metrics: faithfulness, answer_relevancy, context_recall, context_precision.

USAGE:
    source venv/bin/activate
    export ANTHROPIC_API_KEY=your_key
    python evaluate.py

THRESHOLDS (flag below these):
    faithfulness       >= 0.75
    answer_relevancy   >= 0.75
    context_recall     >= 0.70
    context_precision  >= 0.70
"""

import os
import json
from datasets import Dataset
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_recall, context_precision
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from langchain_anthropic import ChatAnthropic
from langchain_community.embeddings import HuggingFaceEmbeddings
from query import retrieve, generate

GOLDEN_DATASET_PATH = "./golden_dataset.json"

THRESHOLDS = {
    "faithfulness": 0.75,
    "answer_relevancy": 0.75,
    "context_recall": 0.70,
    "context_precision": 0.70,
}


def build_eval_dataset() -> Dataset:
    with open(GOLDEN_DATASET_PATH) as f:
        golden = json.load(f)

    rows = []
    print(f"Running RAG pipeline for {len(golden)} questions...\n")
    for item in golden:
        q = item["question"]
        print(f"  Q: {q}")
        contexts = retrieve(q)
        answer = generate(q, contexts)
        rows.append({
            "question": q,
            "answer": answer,
            "contexts": [c["text"] for c in contexts],
            "ground_truth": item["ground_truth"],
        })

    return Dataset.from_list(rows)


def run_evaluation():
    if not os.environ.get("ANTHROPIC_API_KEY"):
        print("ERROR: ANTHROPIC_API_KEY environment variable not set.")
        return

    llm = LangchainLLMWrapper(
        ChatAnthropic(
            model="claude-haiku-4-5-20251001",
            api_key=os.environ["ANTHROPIC_API_KEY"],
        )
    )
    embeddings = LangchainEmbeddingsWrapper(
        HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")
    )

    dataset = build_eval_dataset()

    print("\nRunning RAGAs evaluation...\n")
    results = evaluate(
        dataset=dataset,
        metrics=[faithfulness, answer_relevancy, context_recall, context_precision],
        llm=llm,
        embeddings=embeddings,
    )

    print("\n" + "=" * 50)
    print("RAGAS EVALUATION RESULTS")
    print("=" * 50)

    scores = results.to_pandas()
    summary = {
        "faithfulness": scores["faithfulness"].mean(),
        "answer_relevancy": scores["answer_relevancy"].mean(),
        "context_recall": scores["context_recall"].mean(),
        "context_precision": scores["context_precision"].mean(),
    }

    failures = []
    for metric, score in summary.items():
        threshold = THRESHOLDS[metric]
        status = "PASS" if score >= threshold else "FAIL"
        if status == "FAIL":
            failures.append(metric)
        print(f"  {metric:<22} {score:.4f}   [{status}] (threshold: {threshold})")

    print("=" * 50)
    if failures:
        print(f"\nDRIFT ALERT: {len(failures)} metric(s) below threshold: {', '.join(failures)}")
        print("Investigate: prompt changes, retrieval config, or model updates.")
    else:
        print("\nAll metrics within acceptable range. No drift detected.")

    # Save results for CI artefact
    output = {
        "scores": summary,
        "failures": failures,
        "per_question": scores[["question", "faithfulness", "answer_relevancy",
                                 "context_recall", "context_precision"]].to_dict(orient="records"),
    }
    with open("eval_results.json", "w") as f:
        json.dump(output, f, indent=2)
    print("\nDetailed results saved to eval_results.json")


if __name__ == "__main__":
    run_evaluation()
