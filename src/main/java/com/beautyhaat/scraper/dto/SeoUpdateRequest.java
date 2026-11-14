package com.beautyhaat.scraper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** What you can change on the review screen. */
public record SeoUpdateRequest(
        @NotBlank @Size(max = 300) String seoName,
        @Size(max = 300) String seoSlug,
        @Size(max = 400) String metaDescription,
        @Size(max = 20000) String seoDescription,
        @Size(max = 8) List<@Size(max = 200) String> benefitsBn,
        @Size(max = 300) String imageAlt,
        @Pattern(regexp = "DRAFT|APPROVED") String status
) {
}
