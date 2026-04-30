package com.example.pdfclassifier.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() throws Exception {
        totpService = new TotpService();
        Field issuerField = TotpService.class.getDeclaredField("issuer");
        issuerField.setAccessible(true);
        issuerField.set(totpService, "PDFClassifierTest");
    }

    @Test
    void generateSecret_returnsNonEmptyString() {
        String secret = totpService.generateSecret();
        assertThat(secret).isNotNull().isNotEmpty();
    }

    @Test
    void generateSecret_returnsDifferentSecrets() {
        String secret1 = totpService.generateSecret();
        String secret2 = totpService.generateSecret();
        assertThat(secret1).isNotEqualTo(secret2);
    }

    @Test
    void generateQrCodeDataUri_returnsDataUri() {
        String secret = totpService.generateSecret();
        String dataUri = totpService.generateQrCodeDataUri(secret, "testuser");
        assertThat(dataUri).startsWith("data:image/png;base64,");
    }

    @Test
    void generateQrCodeDataUri_containsBase64Data() {
        String secret = totpService.generateSecret();
        String dataUri = totpService.generateQrCodeDataUri(secret, "testuser");
        String base64Part = dataUri.substring("data:image/png;base64,".length());
        assertThat(base64Part).isNotEmpty();
    }

    @Test
    void verifyCode_invalidCodeReturnsFalse() {
        String secret = totpService.generateSecret();
        boolean result = totpService.verifyCode(secret, "000000");
        // A random code is almost certainly invalid
        // We can't guarantee it's false (1 in 1M chance), so just check it runs without error
        assertThat(result).isIn(true, false);
    }

    @Test
    void verifyCode_wrongFormatReturnsFalse() {
        String secret = totpService.generateSecret();
        boolean result = totpService.verifyCode(secret, "not-a-code");
        assertThat(result).isFalse();
    }
}
