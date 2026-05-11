# pdf-classifier-app

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-brightgreen?logo=springboot&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.13-blue?logo=python&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![Known Vulnerabilities](https://snyk.io/test/github/A-Dagaate/pdf-classifier-app/badge.svg)](https://snyk.io/test/github/A-Dagaate/pdf-classifier-app)

A **polyglot document-intelligence platform**: Spring Boot handles secure user auth, PDF upload, and ML classification — while a Python FastAPI sidecar provides a fully operational (not quite but I'm showing off here so bear with me) RAG pipeline that answers natural-language questions about a knowledge domain using ChromaDB vector search and Claude AI. Shows your 16 yr old 80% of what they need to regress an AI/ML pipeline. Won't get her a job but at least she won't be bored!

---

## Architecture

```
Browser
  │
  ▼
Spring Boot App  (port 8080)          Python RAG Sidecar  (port 8001)
┌──────────────────────────────┐      ┌─────────────────────────────────────┐
│  /pdf-classifier/*           │      │  FastAPI                            │
│                              │      │                                     │
│  Auth ── JWT + TOTP 2FA      │      │  GET /health                        │
│  PDF Upload + Tika validate  │      │  GET /query?q=...  ──► Claude AI    │
│  ML Classification (PDFBox)  │      │  GET /section/{id}                  │
│  Async Email Notifications   │      │  GET /explain/{n}                   │
│  Thymeleaf server-side UI    │      │  GET /quiz/k1                       │
│                              │      │  GET /quiz/k1/{term}                │
│  H2 / PostgreSQL / MySQL     │      │                                     │
│  Spring Data JPA             │      │  ChromaDB  ◄── sentence-transformers│
│  BCrypt + RFC-6238 TOTP      │      │  (vectors)      all-MiniLM-L6-v2    │
└──────────────────────────────┘      └─────────────────────────────────────┘
          │                                           │
          └──────────── docker-compose.yml ───────────┘
                        single  `docker compose up`
```

---

## Quick Start

### Docker (single command)

```bash
git clone https://github.com/A-Dagaate/pdf-classifier-app.git
cd pdf-classifier-app
cp .env.example .env          # set ANTHROPIC_API_KEY and optionally mail creds
docker compose up --build
```

| Service | URL |
|---|---|
| Spring Boot app | http://localhost:8080/pdf-classifier |
| RAG sidecar health | http://localhost:8001/health |
| K1 vocabulary quiz | http://localhost:8001/quiz/k1 |

### Local development

```bash
# Spring Boot (Java 17+, Maven 3.6+)
mvn spring-boot:run

# Python sidecar (Python 3.13+)
cd ste_sidecar
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
ANTHROPIC_API_KEY=sk-ant-... uvicorn main:app --port 8001
```

---

## Features

### Spring Boot Application

- **Two-Factor Authentication** — TOTP (RFC 6238), QR-code setup page, Google Authenticator / Authy compatible
- **JWT Session Management** — stateless tokens, BCrypt-hashed passwords (strength 12)
- **PDF Upload & Validation** — Apache Tika MIME detection, 10 MB limit, UUID-prefixed storage
- **ML Document Classification** — Apache PDFBox text and image extraction; pluggable classifier (rule-based baseline, ready for DL4J / TensorFlow Java / AWS Rekognition swap-in)
- **Async Email Notifications** — Spring Mail, processed file attached; `@Async` thread pool keeps uploads non-blocking
- **CSRF Protection** — Spring Security default policy, all state-mutating routes protected

### RAG Study Sidecar — ISTQB STE v1.0.1

A retrieval-augmented generation tutor wired to the ISTQB Security Test Engineer syllabus. The full pipeline runs inside Docker with no extra setup:

| Endpoint | Description |
|---|---|
| `GET /query?q=...` | Ask any question; returns Claude-generated answer + syllabus sources |
| `GET /section/{id}` | Raw syllabus chunk by section ID (e.g. `3.1.1`) |
| `GET /explain/{n}` | Exam-question deep-dive: what knowledge is being tested, how to answer |
| `GET /questions` | All 40 exam questions with Bloom level, section mapping, core concern |
| `GET /quiz/k1` | 36 K1 vocabulary MCQs generated from syllabus keywords |
| `GET /quiz/k1/{term}` | MCQ for a single term |

**RAG pipeline:**
```
ISTQB STE PDF
   │
   ├── PyMuPDF text extraction
   ├── Regex section chunker  (43 sections, deduped)
   ├── sentence-transformers  (all-MiniLM-L6-v2 embeddings)
   ├── ChromaDB PersistentClient  (persisted volume)
   └── Claude Haiku generation  (Anthropic API)
```

**Bloom-level heat map** of the 40 exam questions shows 17.5 % of marks concentrate in section 3.1.1 (Security Testing in an Organisation) — the sidecar directly targets that distribution.

---

## Security Scanning — Snyk

Dependency vulnerability scanning is wired into the Maven build via the Snyk plugin:

```bash
# One-time: authenticate with your Snyk account
npx snyk auth

# Scan dependencies
mvn snyk:test

# Monitor continuously (uploads snapshot to snyk.io)
mvn snyk:monitor
```

Set `SNYK_TOKEN` in your environment or `.env` file. The build does **not** fail without a token — scanning is opt-in locally and configurable as a CI gate.

[![Known Vulnerabilities](https://snyk.io/test/github/A-Dagaate/pdf-classifier-app/badge.svg)](https://snyk.io/test/github/A-Dagaate/pdf-classifier-app)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Web framework | Spring Boot 3.2, Spring MVC |
| Security | Spring Security, BCrypt, TOTP, JWT (JJWT 0.11.5) |
| Persistence | Spring Data JPA — H2 (dev) / PostgreSQL / MySQL |
| PDF processing | Apache PDFBox 3.0, Apache Tika 2.9 |
| RAG / AI | ChromaDB, sentence-transformers, Claude Haiku (Anthropic) |
| Containerisation | Docker, Docker Compose |
| Build | Maven 3.6+, Java 17 |
| Testing | JUnit 5, Mockito 5, WireMock 3 |
| Security scanning | Snyk Maven plugin |

---

## Project Structure

```
pdf-classifier-app/
├── src/main/java/com/example/pdfclassifier/
│   ├── config/         SecurityConfig
│   ├── controller/     MainController  (all routes)
│   ├── entity/         User, PdfDocument  (JPA)
│   ├── repository/     UserRepository, PdfDocumentRepository
│   ├── security/       CustomUserDetailsService, TwoFactorAuthenticationFilter
│   └── service/        UserService, TotpService, PdfProcessingService, EmailService
├── ste_sidecar/                    Python FastAPI RAG service
│   ├── main.py                     FastAPI app with lifespan ingest
│   ├── ingest.py                   PDF → ChromaDB ingestion pipeline
│   ├── query.py                    Retrieval + Claude generation
│   ├── generate_k1_suite.py        One-shot K1 MCQ batch job
│   ├── questions_dataset.json      40 exam questions with metadata
│   ├── k1_questions.json           36 generated K1 vocabulary MCQs
│   ├── ISTQB_STE_v1.0.1-Syllabus.pdf
│   └── Dockerfile
├── docker-compose.yml              Polyglot service orchestration
├── .env.example                    Environment variable template
└── pom.xml
```

---

## Environment Variables

| Variable | Default | Required for |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | RAG sidecar (Claude generation) |
| `APP_JWT_SECRET` | `devSecretKey...` | Change in production |
| `SPRING_MAIL_USERNAME` | — | Email notifications |
| `SPRING_MAIL_PASSWORD` | — | Email notifications |
| `SNYK_TOKEN` | — | Snyk vulnerability scanning |

Copy `.env.example` → `.env` before `docker compose up`.

---

## Running Tests

```bash
mvn test
```

Covers: UserService (unit, Mockito), MainController (MockMvc), TotpService (unit), PdfProcessingService (unit).

---

## Licence

MIT
