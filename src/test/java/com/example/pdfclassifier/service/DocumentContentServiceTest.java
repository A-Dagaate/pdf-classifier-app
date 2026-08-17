package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocumentContent;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.PdfDocumentContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentContentServiceTest {

    @Mock
    private PdfDocumentContentRepository contentRepository;

    @InjectMocks
    private DocumentContentService documentContentService;

    private PdfDocument document;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(7L);

        document = new PdfDocument();
        document.setId(100L);
        document.setUser(user);
    }

    @Test
    void store_persistsBytesAndOwner() {
        when(contentRepository.findDocumentIdsByUserNewestFirst(7L)).thenReturn(List.of(100L));

        documentContentService.store(document, new byte[]{1, 2, 3}, "application/pdf");

        ArgumentCaptor<PdfDocumentContent> captor = ArgumentCaptor.forClass(PdfDocumentContent.class);
        verify(contentRepository).save(captor.capture());

        PdfDocumentContent saved = captor.getValue();
        assertThat(saved.getDocumentId()).isEqualTo(100L);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getPdfData()).containsExactly(1, 2, 3);
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getStoredAt()).isNotNull();
    }

    @Test
    void store_defaultsContentTypeWhenMissing() {
        when(contentRepository.findDocumentIdsByUserNewestFirst(7L)).thenReturn(List.of(100L));

        documentContentService.store(document, new byte[]{1}, null);

        ArgumentCaptor<PdfDocumentContent> captor = ArgumentCaptor.forClass(PdfDocumentContent.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void store_nonPdfBytes_stillStoredWithNullThumbnail() {
        // A thumbnail is a nice-to-have; failing to render one must not lose the upload.
        when(contentRepository.findDocumentIdsByUserNewestFirst(7L)).thenReturn(List.of(100L));

        documentContentService.store(document, "not a pdf".getBytes(), "application/pdf");

        ArgumentCaptor<PdfDocumentContent> captor = ArgumentCaptor.forClass(PdfDocumentContent.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getPdfData()).isNotEmpty();
        assertThat(captor.getValue().getThumbnailData()).isNull();
    }

    @Test
    void store_underRetentionLimit_prunesNothing() {
        List<Long> ids = LongStream.rangeClosed(1, DocumentContentService.RETAINED_DOCUMENTS_PER_USER)
                .boxed().toList();
        when(contentRepository.findDocumentIdsByUserNewestFirst(7L)).thenReturn(ids);

        documentContentService.store(document, new byte[]{1}, "application/pdf");

        verify(contentRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void store_overRetentionLimit_prunesOldestOnly() {
        // 13 stored, newest first. The newest 10 stay; ids 11, 12, 13 are dropped.
        List<Long> newestFirst = LongStream.rangeClosed(1, 13).boxed().toList();
        when(contentRepository.findDocumentIdsByUserNewestFirst(7L)).thenReturn(newestFirst);

        documentContentService.store(document, new byte[]{1}, "application/pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(contentRepository).deleteAllByIdInBatch(captor.capture());

        assertThat(captor.getValue()).containsExactly(11L, 12L, 13L);
    }

    @Test
    void store_repositoryFailure_doesNotPropagate() {
        // The upload has already succeeded by this point; losing the preview is
        // preferable to failing the request.
        when(contentRepository.save(any())).thenThrow(new RuntimeException("db down"));

        documentContentService.store(document, new byte[]{1}, "application/pdf");

        verify(contentRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void findContent_delegatesToRepository() {
        PdfDocumentContent content = new PdfDocumentContent();
        when(contentRepository.findById(100L)).thenReturn(Optional.of(content));

        assertThat(documentContentService.findContent(100L)).containsSame(content);
    }

    @Test
    void findContent_missing_returnsEmpty() {
        when(contentRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThat(documentContentService.findContent(999L)).isEmpty();
    }
}
