package com.example.pdfclassifier.controller;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocumentContent;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.PdfDocumentRepository;
import com.example.pdfclassifier.service.DocumentContentService;
import com.example.pdfclassifier.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Viewing and serving stored documents.
 *
 * Split out from MainController rather than added to it: these endpoints return
 * binary bodies with their own caching and content-disposition concerns, which
 * sits awkwardly beside page controllers, and MainController already carries
 * every other route in the application.
 *
 * Every method resolves the document through requireOwnedDocument(...), which
 * answers 404 — not 403 — when the document belongs to someone else. Returning
 * 403 would confirm that an id exists, letting anyone enumerate how many
 * documents the system holds.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final PdfDocumentRepository pdfDocumentRepository;
    private final DocumentContentService documentContentService;
    private final UserService userService;

    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Long id, Model model) {
        PdfDocument document = requireOwnedDocument(id);

        model.addAttribute("document", document);
        model.addAttribute("hasPreview",
                documentContentService.findContent(id).map(c -> c.getPdfData() != null).orElse(false));

        return "document";
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> serveFile(@PathVariable Long id) {
        PdfDocument document = requireOwnedDocument(id);
        byte[] data = requireContent(id).getPdfData();
        if (data == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // inline so the browser's viewer renders it in the iframe rather than downloading
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + sanitiseFilename(document.getOriginalFilename()) + "\"");
        // A PDF is untrusted user content served from our own origin, and PDFs can
        // carry JavaScript. The sandbox directive keeps it from acting as same-origin.
        headers.set("Content-Security-Policy", "sandbox");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, max-age=300");

        return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/documents/{id}/thumbnail")
    public ResponseEntity<byte[]> serveThumbnail(@PathVariable Long id) {
        requireOwnedDocument(id);
        byte[] data = requireContent(id).getThumbnailData();
        if (data == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, max-age=300");

        return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * Load a document and confirm it belongs to the authenticated user.
     * Missing and not-yours are deliberately indistinguishable to the caller.
     */
    private PdfDocument requireOwnedDocument(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        User user = userService.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        Optional<PdfDocument> found = pdfDocumentRepository.findById(id);
        if (found.isEmpty()
                || found.get().getUser() == null
                || !found.get().getUser().getId().equals(user.getId())) {
            log.debug("Refusing document {} for user {}", id, auth.getName());
            throw new ResponseStatusException(NOT_FOUND);
        }

        return found.get();
    }

    private PdfDocumentContent requireContent(Long id) {
        return documentContentService.findContent(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    /** Strip anything that could break out of the Content-Disposition quoting. */
    private String sanitiseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.pdf";
        }
        return filename.replaceAll("[\"\\\\\\r\\n]", "_");
    }
}
