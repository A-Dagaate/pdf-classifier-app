package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocumentContent;
import com.example.pdfclassifier.repository.PdfDocumentContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores uploaded PDFs in the database so they survive a redeploy.
 *
 * Container filesystems on Railway are ephemeral: ./uploads is wiped on every
 * deploy, which left previously uploaded documents listed on the dashboard with
 * their bytes gone. The on-disk copy is still written and still used — the ML
 * service and the PDFBox fallback both read the file by path during processing —
 * so this is a durability copy, not a replacement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentContentService {

    /** Newest N documents per user keep their bytes; older content rows are pruned. */
    static final int RETAINED_DOCUMENTS_PER_USER = 10;

    private static final int THUMBNAIL_MAX_WIDTH = 420;
    private static final int THUMBNAIL_RENDER_DPI = 72;

    private final PdfDocumentContentRepository contentRepository;

    /**
     * Persist the PDF bytes and a first-page thumbnail, then prune old content.
     *
     * Never throws: a preview is a nice-to-have, and failing an upload because a
     * thumbnail could not be rendered would trade a working feature for a broken one.
     *
     * REQUIRES_NEW is load-bearing, not decoration. The caller,
     * PdfProcessingService.saveUploadedFile, is itself @Transactional. Under the
     * default REQUIRED propagation this method would JOIN that transaction, so a
     * database failure here would mark the shared transaction rollback-only. The
     * catch below would swallow the exception, this method would return normally,
     * and the caller's commit would then fail with UnexpectedRollbackException —
     * the upload would fail anyway and the defensive catch would buy nothing.
     *
     * The trade-off accepted in exchange: this commits independently, so if the
     * caller's transaction later rolls back, a content row can survive pointing at
     * a document row that was never committed. That orphan is harmless — there is
     * no foreign key, and DocumentController answers 404 for a document that does
     * not exist — whereas the alternative loses the upload itself.
     *
     * Note this behaviour is not covered by DocumentContentServiceTest: Mockito
     * mocks have no transaction manager, so propagation cannot be observed there.
     * Verifying it needs an integration test with a real transactional context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(PdfDocument document, byte[] pdfBytes, String contentType) {
        try {
            PdfDocumentContent content = new PdfDocumentContent();
            content.setDocumentId(document.getId());
            content.setUserId(document.getUser().getId());
            content.setPdfData(pdfBytes);
            content.setThumbnailData(renderThumbnail(pdfBytes));
            content.setContentType(contentType != null ? contentType : "application/pdf");
            content.setStoredAt(LocalDateTime.now());

            contentRepository.save(content);
            pruneOldContent(document.getUser().getId());

        } catch (Exception e) {
            log.warn("Could not store preview content for document {}: {}",
                    document.getId(), e.getMessage());
        }
    }

    public Optional<PdfDocumentContent> findContent(Long documentId) {
        return contentRepository.findById(documentId);
    }

    /**
     * Keep only the newest RETAINED_DOCUMENTS_PER_USER content rows for a user.
     *
     * Document rows and their classification results are never touched — only the
     * heavy bytes are dropped. A public URL means strangers can upload, and the
     * free-tier database is small enough that unbounded growth would eventually
     * take the app down.
     */
    private void pruneOldContent(Long userId) {
        List<Long> ids = contentRepository.findDocumentIdsByUserNewestFirst(userId);
        if (ids.size() <= RETAINED_DOCUMENTS_PER_USER) {
            return;
        }

        List<Long> stale = ids.subList(RETAINED_DOCUMENTS_PER_USER, ids.size());
        contentRepository.deleteAllByIdInBatch(stale);
        log.info("Pruned {} stored PDF(s) for user {}, keeping the newest {}",
                stale.size(), userId, RETAINED_DOCUMENTS_PER_USER);
    }

    /** Render page 1 to a width-constrained PNG. Returns null if the PDF cannot be read. */
    private byte[] renderThumbnail(byte[] pdfBytes) {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            if (pdf.getNumberOfPages() == 0) {
                return null;
            }

            BufferedImage full = new PDFRenderer(pdf)
                    .renderImageWithDPI(0, THUMBNAIL_RENDER_DPI);
            BufferedImage scaled = scaleToWidth(full, THUMBNAIL_MAX_WIDTH);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "PNG", out);
            return out.toByteArray();

        } catch (Exception e) {
            log.warn("Thumbnail render failed: {}", e.getMessage());
            return null;
        }
    }

    private BufferedImage scaleToWidth(BufferedImage source, int maxWidth) {
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        int height = Math.max(1, (int) Math.round(
                source.getHeight() * (maxWidth / (double) source.getWidth())));

        BufferedImage scaled = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, maxWidth, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
