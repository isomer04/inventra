package com.inventra.api.config;

import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration — sets serialization defaults for the whole application.
 *
 * Enable HTML-safe character escaping so that
 * user-supplied strings containing less-than, greater-than, ampersand, and
 * single-quote are Unicode-escaped in JSON output. This is a belt-and-suspenders
 * control on top of Angular's built-in template escaping. Protects future REST
 * consumers (mobile apps, server-side renderers) that may not escape JSON values
 * before inserting them into HTML.
 *
 * Reference: https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html
 */
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper with HTML-safe CharacterEscapes applied to the
     * underlying JsonFactory. Spring Boot 4 / Jackson 3.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()   // registers JavaTimeModule, etc.
                .build();
        // CharacterEscapes persists for all serialization done by this mapper.
        mapper.getFactory().setCharacterEscapes(new HtmlCharacterEscapes());
        return mapper;
    }

    /**
     * Custom CharacterEscapes that HTML-encodes the four characters meaningful in
     * HTML contexts: ampersand, less-than, greater-than, and single-quote.
     *
     * Jackson emits the standard numeric Unicode escape sequence for each flagged
     * character (ESCAPE_STANDARD), producing JSON strings that browsers will not
     * interpret as HTML regardless of rendering context.
     */
    static class HtmlCharacterEscapes extends CharacterEscapes {

        private final int[] asciiEscapes;

        HtmlCharacterEscapes() {
            asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
            asciiEscapes['<']  = CharacterEscapes.ESCAPE_STANDARD;
            asciiEscapes['>']  = CharacterEscapes.ESCAPE_STANDARD;
            asciiEscapes['&']  = CharacterEscapes.ESCAPE_STANDARD;
            asciiEscapes['\''] = CharacterEscapes.ESCAPE_STANDARD;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes.clone();
        }

        @Override
        public com.fasterxml.jackson.core.SerializableString getEscapeSequence(int ch) {
            return null; // Use Jackson built-in numeric Unicode escape for flagged chars
        }
    }
}
