package com.beautyhaat.scraper.service;

import java.time.Instant;

/** Live progress of the AI batch, polled by the frontend. */
public class AiStatus {

    private volatile String state = "IDLE"; // IDLE, RUNNING, DONE, STOPPED, FAILED
    private volatile int total;
    private volatile int done;
    private volatile int failed;
    private volatile int imagesDone;
    private volatile int imagesFailed;
    private volatile boolean withImages;
    private volatile String current = "";
    private volatile String message = "";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    void begin(int total, boolean withImages) {
        this.state = "RUNNING";
        this.total = total;
        this.done = 0;
        this.failed = 0;
        this.imagesDone = 0;
        this.imagesFailed = 0;
        this.withImages = withImages;
        this.current = "";
        this.message = "Started";
        this.startedAt = Instant.now();
        this.finishedAt = null;
    }

    void finish(String state, String message) {
        this.state = state;
        this.message = message;
        this.current = "";
        this.finishedAt = Instant.now();
    }

    void setCurrent(String c) { this.current = c; }
    void setMessage(String m) { this.message = m; }
    void textDone() { this.done++; }
    void textFailed() { this.failed++; }
    void imagesDone() { this.imagesDone++; }
    void imagesFailed() { this.imagesFailed++; }

    public String getState() { return state; }
    public int getTotal() { return total; }
    public int getDone() { return done; }
    public int getFailed() { return failed; }
    public int getImagesDone() { return imagesDone; }
    public int getImagesFailed() { return imagesFailed; }
    public boolean isWithImages() { return withImages; }
    public String getCurrent() { return current; }
    public String getMessage() { return message; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
