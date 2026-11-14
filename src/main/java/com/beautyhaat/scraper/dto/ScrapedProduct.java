package com.beautyhaat.scraper.dto;

import java.math.BigDecimal;

/** One product card as read from a shop page (before it is saved). */
public record ScrapedProduct(
        String slug,
        String name,
        String productUrl,
        String imageUrl,
        BigDecimal regularPrice,
        BigDecimal salePrice,
        Integer discountPercent,
        String size,
        String badge
) {
}
