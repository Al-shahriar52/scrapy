package com.beautyhaat.scraper.dto;

import com.beautyhaat.scraper.model.Product;
import com.beautyhaat.scraper.model.Tag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** What the API returns for a product: relations are flattened to plain names. */
public record ProductResponse(
        Long id,
        String slug,
        String name,
        String productUrl,
        String imageUrl,
        BigDecimal regularPrice,
        BigDecimal salePrice,
        Integer discountPercent,
        String size,
        String badge,
        String description,
        String sku,
        Double rating,
        Long numReviews,
        String brand,
        String category,
        String subCategory,
        String categoryPath,
        List<String> tags,
        LocalDateTime detailsScrapedAt,
        LocalDateTime updatedAt,
        // ---- AI content
        String seoName,
        String seoSlug,
        String metaDescription,
        String seoDescription,
        List<String> benefitsBn,
        String imageAlt,
        String seoStatus,
        LocalDateTime seoGeneratedAt,
        String productImageUrl,
        String benefitImageUrl
) {
    public static ProductResponse from(Product p) {
        String version = p.getImagesGeneratedAt() == null ? "" : "?v=" + p.getImagesGeneratedAt().toEpochSecond(ZoneOffset.UTC);
        return new ProductResponse(
                p.getId(), p.getSlug(), p.getName(), p.getProductUrl(), p.getImageUrl(),
                p.getRegularPrice(), p.getSalePrice(), p.getDiscountPercent(), p.getSize(), p.getBadge(),
                p.getDescription(), p.getSku(), p.getRating(), p.getNumReviews(),
                p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getSubCategory() == null ? null : p.getSubCategory().getName(),
                p.getCategoryPath(),
                p.getTags().stream().map(Tag::getName).sorted(Comparator.comparing(String::toLowerCase)).toList(),
                p.getDetailsScrapedAt(), p.getUpdatedAt(),
                p.getSeoName(), p.getSeoSlug(), p.getMetaDescription(), p.getSeoDescription(),
                p.getBenefitsBn() == null ? List.of()
                        : Arrays.stream(p.getBenefitsBn().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                p.getImageAlt(), p.getSeoStatus(), p.getSeoGeneratedAt(),
                p.getProductImageFile() == null ? null : "/media/" + p.getProductImageFile() + version,
                p.getBenefitImageFile() == null ? null : "/media/" + p.getBenefitImageFile() + version);
    }
}
