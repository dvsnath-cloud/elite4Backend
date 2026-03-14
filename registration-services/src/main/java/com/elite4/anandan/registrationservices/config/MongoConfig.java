package com.elite4.anandan.registrationservices.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration.
 * Connection settings are read from application.properties (spring.data.mongodb.*).
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.elite4.anandan.registrationservices.repository")
public class MongoConfig {
}
