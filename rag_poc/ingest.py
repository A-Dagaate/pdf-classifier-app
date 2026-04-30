"""
ingest.py
=========
Ingests sample documents into ChromaDB using sentence-transformers embeddings.
Run this once before query.py or evaluate.py.

USAGE:
    source venv/bin/activate
    python ingest.py
"""

import chromadb
from chromadb.utils import embedding_functions
import os

# ─── Sample documents ────────────────────────────────────────────────────────
# In production these come from the PDF Classifier uploads/ directory.
# For the PoC we use structured text so Q&A is meaningful.

DOCUMENTS = [
    {
        "id": "doc1_chunk1",
        "text": (
            "Test Automation Policy v2.1 — Section 1: Scope\n"
            "This policy applies to all software quality assurance activities within the "
            "engineering organization. Automated tests must be written for every new feature "
            "before it is merged to the main branch. The minimum acceptable code coverage "
            "threshold is 80%. Any pull request that reduces coverage below this threshold "
            "must be approved by the QA Lead before merging."
        ),
        "source": "test_automation_policy.pdf",
    },
    {
        "id": "doc1_chunk2",
        "text": (
            "Test Automation Policy v2.1 — Section 2: Test Types\n"
            "Unit tests validate individual functions in isolation and must run in under "
            "30 seconds total. Integration tests validate interactions between services "
            "and run in the nightly pipeline. End-to-end tests using Playwright cover "
            "the five critical user journeys and gate every production release. "
            "Performance tests using Locust run weekly against the staging environment."
        ),
        "source": "test_automation_policy.pdf",
    },
    {
        "id": "doc1_chunk3",
        "text": (
            "Test Automation Policy v2.1 — Section 3: Defect Management\n"
            "All defects discovered during testing must be logged in Jira within 24 hours. "
            "Critical defects (P1) block the current release and require a hotfix within "
            "48 hours. High defects (P2) must be resolved within the current sprint. "
            "Defect escape rate — defects found in production versus testing — is reviewed "
            "monthly and must remain below 5%."
        ),
        "source": "test_automation_policy.pdf",
    },
    {
        "id": "doc2_chunk1",
        "text": (
            "Cloud Infrastructure Guide — AWS Architecture Overview\n"
            "The production environment runs on AWS us-east-1 with multi-AZ failover. "
            "Application servers run on EC2 t3.medium instances behind an Application "
            "Load Balancer. The database tier uses RDS PostgreSQL 15 with automated "
            "backups retained for 30 days. All data at rest is encrypted using AWS KMS. "
            "CloudTrail is enabled on all accounts for audit logging."
        ),
        "source": "cloud_infra_guide.pdf",
    },
    {
        "id": "doc2_chunk2",
        "text": (
            "Cloud Infrastructure Guide — Security Controls\n"
            "Access to production is controlled via IAM roles with least-privilege "
            "permissions. No IAM user access keys are permitted in production; all access "
            "uses instance profiles or assumed roles. Security groups restrict inbound "
            "traffic to ports 443 and 22 (SSH limited to the VPN CIDR only). "
            "GuardDuty monitors for anomalous API calls and alerts the security team "
            "within 15 minutes of detection."
        ),
        "source": "cloud_infra_guide.pdf",
    },
    {
        "id": "doc2_chunk3",
        "text": (
            "Cloud Infrastructure Guide — Deployment Pipeline\n"
            "Deployments are triggered via GitHub Actions on merge to main. The pipeline "
            "runs unit tests, integration tests, and a SAST scan before packaging. "
            "Artifacts are stored in ECR and deployed to ECS Fargate using blue-green "
            "deployment with a 10-minute canary window. Rollback is automatic if the "
            "error rate exceeds 1% during the canary window."
        ),
        "source": "cloud_infra_guide.pdf",
    },
    {
        "id": "doc3_chunk1",
        "text": (
            "Software Requirements Specification — Document Management System v3.0\n"
            "The system shall allow authenticated users to upload PDF documents up to "
            "50MB in size. Documents must be classified within 60 seconds of upload. "
            "The system shall support the following document categories: Invoice, "
            "Contract, Resume, Technical Specification, and General. Classification "
            "confidence below 70% must trigger a manual review queue."
        ),
        "source": "srs_document_management.pdf",
    },
    {
        "id": "doc3_chunk2",
        "text": (
            "Software Requirements Specification — Non-Functional Requirements\n"
            "The system must maintain 99.9% uptime measured monthly. API response time "
            "for document upload must not exceed 2 seconds at the 95th percentile under "
            "a load of 100 concurrent users. All user data must be encrypted in transit "
            "using TLS 1.2 or higher. The system must comply with SOC 2 Type II "
            "requirements and undergo annual security audits."
        ),
        "source": "srs_document_management.pdf",
    },
    {
        "id": "doc3_chunk3",
        "text": (
            "Software Requirements Specification — Notification Requirements\n"
            "Users must receive an email notification within 5 minutes of document "
            "classification completion. Notification emails must include the document "
            "name, classification result, confidence score, and a direct link to the "
            "processed result. Failed classifications must trigger both a user "
            "notification and an internal alert to the on-call engineering team."
        ),
        "source": "srs_document_management.pdf",
    },
]

DB_PATH = "./chroma_db"
COLLECTION_NAME = "pdf_classifier_docs"

def ingest():
    print("Initialising ChromaDB...")
    client = chromadb.PersistentClient(path=DB_PATH)

    ef = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="all-MiniLM-L6-v2"
    )

    collection = client.get_or_create_collection(
        name=COLLECTION_NAME,
        embedding_function=ef,
    )

    existing = collection.get()["ids"]
    new_docs = [d for d in DOCUMENTS if d["id"] not in existing]

    if not new_docs:
        print(f"All {len(DOCUMENTS)} chunks already ingested. Nothing to do.")
        return

    collection.add(
        ids=[d["id"] for d in new_docs],
        documents=[d["text"] for d in new_docs],
        metadatas=[{"source": d["source"]} for d in new_docs],
    )

    print(f"Ingested {len(new_docs)} chunks into '{COLLECTION_NAME}'.")
    print(f"Total chunks in collection: {collection.count()}")


if __name__ == "__main__":
    ingest()
