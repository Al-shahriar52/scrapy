package com.beautyhaat.scraper.dto;

import java.util.List;

/** Extra data read from a product's own page. Any field can be null / empty if the page does not show it. */
public record ProductDetail(
        String sku,
        String description,
        Double rating,
        Long numReviews,
        NamedItem brand,
        NamedItem category,
        NamedItem subCategory,
        List<NamedItem> tags,
        String categoryPath
) {
}
