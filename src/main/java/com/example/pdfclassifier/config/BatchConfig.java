package com.example.pdfclassifier.config;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocument.ProcessingStatus;
import com.example.pdfclassifier.repository.PdfDocumentRepository;
import com.example.pdfclassifier.service.MlClassificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.ResourceAccessException;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Batch job that classifies every PENDING document in one pass.
 *
 * Spring Batch 5 (Boot 3.x) removed JobBuilderFactory and StepBuilderFactory —
 * jobs and steps are now built directly, taking the JobRepository explicitly.
 * @EnableBatchProcessing is deliberately absent: on Boot 3 it switches OFF the
 * batch auto-configuration this class relies on.
 */
@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private final PdfDocumentRepository pdfDocumentRepository;
    private final MlClassificationService mlClassificationService;

    @Bean
    public RepositoryItemReader<PdfDocument> pdfDocumentReader() {
        RepositoryItemReader<PdfDocument> reader = new RepositoryItemReader<>();
        // Required by ItemStreamSupport so progress can be saved to the execution context
        reader.setName("pdfDocumentReader");
        reader.setRepository(pdfDocumentRepository);
        reader.setPageSize(10);
        // Resolves to Page<PdfDocument> findByProcessingStatus(status, Pageable)
        reader.setMethodName("findByProcessingStatus");
        reader.setArguments(List.of(ProcessingStatus.PENDING));
        reader.setSort(Map.of("id", Sort.Direction.ASC));
        return reader;
    }

    @Bean
    public ItemProcessor<PdfDocument, PdfDocument> pdfDocumentProcessor() {
        return document -> {
            String result = mlClassificationService.classify(
                    document.getFilePath(),
                    document.getDocumentQuality());
            document.setClassificationResult(result);
            document.setProcessingStatus(ProcessingStatus.COMPLETED);
            document.setProcessedDate(LocalDateTime.now());
            return document;
        };
    }

    @Bean
    public ItemWriter<PdfDocument> pdfDocumentWriter() {
        return chunk -> pdfDocumentRepository.saveAll(chunk);
    }

    @Bean
    public Step classifyStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager) {
        return new StepBuilder("classifyStep", jobRepository)
                .<PdfDocument, PdfDocument>chunk(10, transactionManager)
                .reader(pdfDocumentReader())
                .processor(pdfDocumentProcessor())
                .writer(pdfDocumentWriter())
                .faultTolerant()
                .retry(ResourceAccessException.class).retryLimit(3)
                .skip(FileNotFoundException.class).skipLimit(10)
                .build();
    }

    @Bean
    public Job classifyPdfJobs(JobRepository jobRepository, Step classifyStep) {
        return new JobBuilder("classifyPdfJobs", jobRepository)
                .start(classifyStep)
                .build();
    }
}
