package com.elite4.anandan.registrationservices.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application configuration for Jackson ObjectMapper
 * Includes support for Java 8 date/time types (Instant, LocalDate, etc.)
 */
@Configuration
public class ApplicationConfig {

    /**
     * Configure ObjectMapper bean for JSON serialization/deserialization
     * Includes JavaTimeModule for proper handling of Instant and other JSR-310 types
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register module for Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());
        // Disable writing dates as timestamps - serialize as ISO-8601 strings instead
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}



