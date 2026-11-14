package com.beautyhaat.scraper.service;

/** A failed Gemini call. {@link #kind()} tells the batch job whether it can carry on with the next product. */
public class GeminiException extends Exception {

    public enum Kind {
        /** Wrong or missing API key, or no permission: nothing will work until it is fixed. */
        AUTH,
        /** Free quota used up for today: stop and try again tomorrow. */
        DAILY_QUOTA,
        /** Too many requests per minute even after waiting. */
        RATE_LIMIT,
        /** Only this product failed (blocked answer, bad JSON, server hiccup). */
        OTHER
    }

    private final Kind kind;

    public GeminiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** True when continuing with the next product is pointless. */
    public boolean isFatal() {
        return kind == Kind.AUTH || kind == Kind.DAILY_QUOTA || kind == Kind.RATE_LIMIT;
    }
}
