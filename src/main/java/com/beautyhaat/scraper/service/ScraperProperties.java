package com.beautyhaat.scraper.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scraper")
public record ScraperProperties(
        String baseUrl,
        String shopPath,
        long delayMs,
        int maxPages,
        int timeoutMs,
        String userAgent
) {
}
