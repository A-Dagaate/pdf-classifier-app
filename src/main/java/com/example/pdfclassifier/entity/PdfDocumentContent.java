package com.example.pdfclassifier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Durable copy of an uploaded PDF, plus a rendered first-page thumbnail.
 *
 * Deliberately NOT mapped as an association on PdfDocument. The dashboard loads
 * documents with findByUser(...) as a List, so any association here — even a lazy
 * one — risks dragging megabytes of PDF into memory on a page that only renders
 * filenames. Keeping this table reachable solely through its own repository makes
 * that mistake impossible rather than merely unlikely.
 *
 * The primary key IS the owning document's id; there is at most one content row
 * per document.
 *
 * Note the absence of @Lob: on PostgreSQL, Hibernate maps @Lob byte[] to an `oid`
 * large object, which must be streamed inside a transaction and breaks simple
 * reads. A plain byte[] maps to VARBINARY, which PostgreSQL renders as `bytea`
 * and H2 renders as VARBINARY — portable across both, which the test suite needs.
 */
@Entity
@Table(name = "pdf_document_content")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfDocumentContent {

    /** Same value as the owning PdfDocument's id. */
    @Id
    @Column(name = "document_id")
    private Long documentId;

    /** Denormalised so pruning can be done without joining back to documents. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pdf_data", length = 10 * 1024 * 1024)
    private byte[] pdfData;

    /** First page rendered to PNG. Null when rendering failed. */
    @Column(name = "thumbnail_data", length = 2 * 1024 * 1024)
    private byte[] thumbnailData;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "stored_at")
    private LocalDateTime storedAt;
}
