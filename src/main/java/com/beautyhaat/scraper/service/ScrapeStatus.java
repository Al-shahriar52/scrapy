package com.beautyhaat.scraper.service;

import java.time.Instant;

/** Live progress of the scraper, polled by the frontend. */
public class ScrapeStatus {

    private volatile String state = "IDLE"; // IDLE, RUNNING, DONE, STOPPED, FAILED
    private volatile int startPage;
    private volatile int endPage;
    private volatile int currentPage;
    private volatile int pagesDone;
    private volatile int inserted;
    private volatile int updated;
    private volatile boolean fetchDetails;
    private volatile int detailsDone;
    private volatile int detailsFailed;
    private volatile String message = "";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    void begin(int startPage, int endPage, boolean fetchDetails) {
        this.state = "RUNNING";
        this.startPage = startPage;
        this.endPage = endPage;
        this.currentPage = startPage;
        this.pagesDone = 0;
        this.inserted = 0;
        this.updated = 0;
        this.fetchDetails = fetchDetails;
        this.detailsDone = 0;
        this.detailsFailed = 0;
        this.message = "Started";
        this.startedAt = Instant.now();
        this.finishedAt = null;
    }

    void finish(String state, String message) {
        this.state = state;
        this.message = message;
        this.finishedAt = Instant.now();
    }

    void setCurrentPage(int p) { this.currentPage = p; }
    void pageDone(int ins, int upd) {
        this.pagesDone++;
        this.inserted += ins;
        this.updated += upd;
    }
    void detailDone() { this.detailsDone++; }
    void detailFailed() { this.detailsFailed++; }
    void setMessage(String m) { this.message = m; }

    public String getState() { return state; }
    public int getStartPage() { return startPage; }
    public int getEndPage() { return endPage; }
    public int getCurrentPage() { return currentPage; }
    public int getPagesDone() { return pagesDone; }
    public int getInserted() { return inserted; }
    public int getUpdated() { return updated; }
    public boolean isFetchDetails() { return fetchDetails; }
    public int getDetailsDone() { return detailsDone; }
    public int getDetailsFailed() { return detailsFailed; }
    public String getMessage() { return message; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
