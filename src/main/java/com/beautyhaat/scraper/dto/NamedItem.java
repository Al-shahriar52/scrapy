package com.beautyhaat.scraper.dto;

/** A brand / category / tag as found on a page: display name plus the slug from its link. */
public record NamedItem(String name, String slug) {
}
