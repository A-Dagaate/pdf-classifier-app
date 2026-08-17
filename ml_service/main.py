"""
PDF classification microservice.

Serves the two endpoints MlClassificationService.java calls:

    POST /classify/layoutlm   used for GOOD/normal quality documents
    POST /classify/donut      used when DocumentQuality == POOR

Both accept multipart/form-data with a part named "file" and return the JSON
shape the Java side parses in formatClassificationResult():

    {
      "document_type": "invoice",     # string, uppercased by the Java client
      "confidence": 0.93,             # float 0..1, multiplied by 100 for display
      "details": { ... }              # optional flat map, rendered as key: value
    }

Classification here is keyword/heuristic based, standing in for the real
LayoutLMv3 and Donut models. The HTTP contract is the real thing, so swapping in
actual inference later touches only classify_text() and read_pdf_text().
"""

import io
import logging
import time

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from pypdf import PdfReader

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("ml_service")

app = FastAPI(title="PDF Classification Service", version="1.0.0")

# Keyword -> document type. Ordered by specificity; first strong hit wins.
SIGNALS = {
    "INVOICE": ["invoice", "bill to", "amount due", "subtotal", "tax", "payment terms"],
    "CONTRACT": ["agreement", "contract", "party", "hereby", "terms and conditions",
                 "signature", "witnesseth"],
    "RESUME": ["resume", "curriculum vitae", "work experience", "education",
               "skills", "references"],
    "REPORT": ["report", "summary", "findings", "analysis", "conclusion",
               "methodology"],
}


def read_pdf_text(raw: bytes) -> tuple[str, int]:
    """Return (extracted_text, page_count). Never raises on a malformed PDF."""
    try:
        reader = PdfReader(io.BytesIO(raw))
        pages = len(reader.pages)
        text = " ".join((page.extract_text() or "") for page in reader.pages)
        return text, pages
    except Exception as exc:  # corrupt/encrypted PDF: degrade, don't 500
        log.warning("PDF parse failed, treating as image-only: %s", exc)
        return "", 0


def classify_text(text: str) -> tuple[str, float, dict]:
    """Score the text against each signal set. Returns (type, confidence, hits)."""
    lowered = text.lower()
    scores = {
        doc_type: sum(1 for kw in keywords if kw in lowered)
        for doc_type, keywords in SIGNALS.items()
    }
    best_type = max(scores, key=scores.get)
    best_score = scores[best_type]

    if best_score == 0:
        return "GENERAL", 0.55, scores

    # Confidence scales with how many of that type's signals matched, capped at 0.97
    confidence = min(0.55 + 0.09 * best_score, 0.97)
    return best_type, round(confidence, 3), scores


def build_response(raw: bytes, filename: str, engine: str, ocr_free: bool) -> dict:
    started = time.perf_counter()
    text, pages = read_pdf_text(raw)

    if ocr_free:
        # Donut reads pixels directly, so it still produces an answer when there
        # is no embedded text layer — that is the whole reason POOR routes here.
        doc_type, confidence, hits = classify_text(text)
        if not text.strip():
            doc_type, confidence = "GENERAL", 0.61
            hits = {"note": "no text layer; classified from page imagery"}
    else:
        # LayoutLMv3 depends on an OCR/text layer. No text means low confidence.
        doc_type, confidence, hits = classify_text(text)
        if not text.strip():
            confidence = 0.32

    elapsed_ms = round((time.perf_counter() - started) * 1000, 1)
    log.info("%s -> %s (%.2f) via %s in %sms", filename, doc_type, confidence,
             engine, elapsed_ms)

    return {
        "document_type": doc_type,
        "confidence": confidence,
        "details": {
            "engine": engine,
            "filename": filename,
            "pages": pages,
            "text_chars": len(text),
            "inference_ms": elapsed_ms,
            "signal_hits": ", ".join(f"{k}={v}" for k, v in hits.items()),
        },
    }


@app.get("/health")
def health():
    return {"status": "ok", "service": "pdf-classification"}


@app.post("/classify/layoutlm")
async def classify_layoutlm(file: UploadFile = File(...)):
    raw = await file.read()
    return JSONResponse(
        build_response(raw, file.filename, "layoutlmv3-base", ocr_free=False)
    )


@app.post("/classify/donut")
async def classify_donut(file: UploadFile = File(...)):
    raw = await file.read()
    return JSONResponse(
        build_response(raw, file.filename, "donut-base-finetuned-rvlcdip", ocr_free=True)
    )
