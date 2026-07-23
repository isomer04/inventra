package com.inventra.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for {@link LogSanitizer}, which prevents CWE-117 (CRLF log injection).
 *
 * <p>These tests verify that CR and LF characters are properly stripped from
 * user-controlled input before logging, preventing attackers from forging log
 * entries or hiding malicious activity.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogSanitizer Tests")
class LogSanitizerTest {

    @Test
    @DisplayName("should remove CR character (\\r)")
    void sanitize_whenInputContainsCR_thenRemovesCR() {
        String input = "Hello\rWorld";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("HelloWorld");
    }

    @Test
    @DisplayName("should remove LF character (\\n)")
    void sanitize_whenInputContainsLF_thenRemovesLF() {
        String input = "Hello\nWorld";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("HelloWorld");
    }

    @Test
    @DisplayName("should remove CRLF sequence (\\r\\n)")
    void sanitize_whenInputContainsCRLF_thenRemovesCRLF() {
        String input = "Hello\r\nWorld";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("HelloWorld");
    }

    @Test
    @DisplayName("should return [null] for null input")
    void sanitize_whenInputIsNull_thenReturnsNullPlaceholder() {
        String result = LogSanitizer.sanitize((String) null);
        assertThat(result).isEqualTo("[null]");
    }

    @Test
    @DisplayName("should handle empty string")
    void sanitize_whenInputIsEmpty_thenReturnsEmpty() {
        String input = "";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should remove multiple CRLF sequences")
    void sanitize_whenInputContainsMultipleCRLF_thenRemovesAll() {
        String input = "Line1\r\nLine2\r\nLine3\r\nLine4";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("Line1Line2Line3Line4");
    }

    @Test
    @DisplayName("should handle mixed CRLF in content")
    void sanitize_whenInputHasMixedContent_thenSanitizesCorrectly() {
        String input = "User: admin\r\nAction: DELETE\r\nTarget: database";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("User: adminAction: DELETETarget: database");
    }

    @Test
    @DisplayName("should return [null] when object overload receives null")
    void sanitize_whenObjectIsNull_thenReturnsNullPlaceholder() {
        String result = LogSanitizer.sanitize((Object) null);
        assertThat(result).isEqualTo("[null]");
    }

    @Test
    @DisplayName("should sanitize object's toString() output")
    void sanitize_whenObjectToStringContainsCRLF_thenSanitizes() {
        Object obj = new Object() {
            @Override
            public String toString() {
                return "Object\r\nwith\r\nnewlines";
            }
        };
        String result = LogSanitizer.sanitize(obj);
        assertThat(result).isEqualTo("Objectwithnewlines");
    }

    @Test
    @DisplayName("should handle string with only CR characters")
    void sanitize_whenInputIsOnlyCR_thenReturnsEmpty() {
        String input = "\r\r\r";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should handle string with only LF characters")
    void sanitize_whenInputIsOnlyLF_thenReturnsEmpty() {
        String input = "\n\n\n";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should handle string with only CRLF sequences")
    void sanitize_whenInputIsOnlyCRLF_thenReturnsEmpty() {
        String input = "\r\n\r\n\r\n";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should preserve text without CRLF unchanged")
    void sanitize_whenInputHasNoCRLF_thenReturnsUnchanged() {
        String input = "Clean log message without injection";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("should handle CRLF at start of string")
    void sanitize_whenCRLFAtStart_thenRemovesIt() {
        String input = "\r\nStartsWithNewline";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("StartsWithNewline");
    }

    @Test
    @DisplayName("should handle CRLF at end of string")
    void sanitize_whenCRLFAtEnd_thenRemovesIt() {
        String input = "EndsWithNewline\r\n";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("EndsWithNewline");
    }

    @Test
    @DisplayName("should handle mixed CR and LF (not as sequence)")
    void sanitize_whenMixedCRAndLFSeparately_thenRemovesBoth() {
        String input = "Text\rWith\nSeparate\rLine\nBreaks";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("TextWithSeparateLineBreaks");
    }

    @Test
    @DisplayName("should handle potential log injection attack")
    void sanitize_whenLogInjectionAttempt_thenNeutralizesAttack() {
        String maliciousInput = "normaldata\r\n2024-01-01 00:00:00 [INFO] FAKE_ENTRY User admin logged in";
        String result = LogSanitizer.sanitize(maliciousInput);
        assertThat(result).doesNotContain("\r").doesNotContain("\n");
        assertThat(result).isEqualTo("normaldata2024-01-01 00:00:00 [INFO] FAKE_ENTRY User admin logged in");
    }

    @Test
    @DisplayName("should handle performance with large strings containing many CRLF")
    void sanitize_whenLargeStringWithManyCRLF_thenPerformsSanitization() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            builder.append("Line").append(i).append("\r\n");
        }
        String input = builder.toString();

        long startTime = System.nanoTime();
        String result = LogSanitizer.sanitize(input);
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        assertThat(result).doesNotContain("\r").doesNotContain("\n");
        assertThat(durationMs).isLessThan(100);
    }

    @Test
    @DisplayName("should handle Unicode characters mixed with CRLF")
    void sanitize_whenUnicodeWithCRLF_thenPreservesUnicodeRemovesCRLF() {
        String input = "Hello 世界\r\nBonjour le monde\nHola mundo\r";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("Hello 世界Bonjour le mondeHola mundo");
    }

    @Test
    @DisplayName("should handle special characters mixed with CRLF")
    void sanitize_whenSpecialCharsWithCRLF_thenPreservesSpecialCharsRemovesCRLF() {
        String input = "Email: user@test.com\r\nPath: /var/log/app.log\nPrice: $100.00";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("Email: user@test.comPath: /var/log/app.logPrice: $100.00");
    }

    @Test
    @DisplayName("should handle object with simple toString()")
    void sanitize_whenObjectWithSimpleToString_thenSanitizes() {
        Integer number = 42;
        String result = LogSanitizer.sanitize(number);
        assertThat(result).isEqualTo("42");
    }

    @Test
    @DisplayName("should handle consecutive CR or LF characters")
    void sanitize_whenConsecutiveCRorLF_thenRemovesAll() {
        String input = "Text\r\r\rwith\n\n\nmultiple\r\n\r\nbreaks";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).isEqualTo("Textwithmultiplebreaks");
    }
}
