package com.beautyhaat.scraper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path imagesDir;

    public WebConfig(@Value("${images.dir:./data/images}") String imagesDir) throws IOException {
        this.imagesDir = Path.of(imagesDir).toAbsolutePath();
        Files.createDirectories(this.imagesDir);
    }

    /** Lets the page be opened from another local port (e.g. IntelliJ's preview server) and still call the API. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    /** Generated pictures are served at /media/{file}. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imagesDir.toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/media/**").addResourceLocations(location).setCachePeriod(3600);
    }
}
