FROM python:3.13-slim

WORKDIR /app

# System deps for pymupdf, chromadb (hnswlib needs g++ and cmake)
RUN apt-get update && apt-get install -y --no-install-recommends \
    g++ \
    cmake \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py ingest.py query.py generate_k1_suite.py questions_dataset.json k1_questions.json ISTQB_STE_v1.0.1-Syllabus.pdf ./

EXPOSE 8001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
