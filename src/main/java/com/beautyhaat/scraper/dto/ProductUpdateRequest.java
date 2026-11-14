package com.beautyhaat.scraper.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Brand, category, sub-category and tags are sent by name; they are created if they do not exist yet. */
public record ProductUpdateRequest(
        @NotBlank @Size(max = 1000) String name,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal regularPrice,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
        @Min(0) @Max(100) Integer discountPercent,
        @Size(max = 100) String size,
        @Size(max = 100) String badge,
        @Size(max = 2000) String imageUrl,
        @Size(max = 20000) String description,
        @Size(max = 100) String sku,
        @DecimalMin("0.0") @DecimalMax("5.0") Double rating,
        @Min(0) Long numReviews,
        @Size(max = 300) String brand,
        @Size(max = 300) String category,
        @Size(max = 300) String subCategory,
        @Size(max = 40) List<@Size(max = 300) String> tags
) {
}
