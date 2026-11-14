package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.model.Product;
import com.beautyhaat.scraper.repository.ProductRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the AI step for many products in the background, one at a time, at a pace the free Gemini tier allows.
 * Stops by itself when the key is wrong or the free quota is used up; products already done are skipped next time.
 */
@Service
public class AiJobService {

    private static final Logger log = LoggerFactory.getLogger(AiJobService.class);

    private final ProductRepository repository;
    private final SeoContentService seo;
    private final GeminiClient gemini;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-batch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AiStatus status = new AiStatus();
    private volatile boolean stopRequested;

    public AiJobService(ProductRepository repository, SeoContentService seo, GeminiClient gemini) {
        this.repository = repository;
        this.seo = seo;
        this.gemini = gemini;
    }

    public AiStatus status() {
        return status;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * @param limit      how many products to process in this run
     * @param withImages also draw the two pictures
     * @param regenerate include products that already have DRAFT content (approved ones are never touched)
     * @return number of products queued, or -1 if a job is already running
     */
    public int start(int limit, boolean withImages, boolean regenerate) {
        if (!running.compareAndSet(false, true)) {
            return -1;
        }
        List<Long> ids;
        try {
            PageRequest page = PageRequest.of(0, Math.max(1, limit));
            ids = regenerate ? repository.idsNotApproved(page) : repository.idsWithoutSeo(page);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        stopRequested = false;
        status.begin(ids.size(), withImages);
        if (ids.isEmpty()) {
            status.finish("DONE", "Nothing to do - every product already has AI content.");
            running.set(false);
            return 0;
        }
        executor.submit(() -> run(ids, withImages));
        return ids.size();
    }

    public void stop() {
        stopRequested = true;
    }

    private void run(List<Long> ids, boolean withImages) {
        String state = "DONE";
        String message = "Finished";
        try {
            for (int i = 0; i < ids.size(); i++) {
                if (stopRequested) {
                    state = "STOPPED";
                    message = "Stopped by user";
                    break;
                }
                Long id = ids.get(i);
                Product p = seo.load(id);
                status.setCurrent(p.getName());
                try {
                    seo.generateText(id);
                    status.textDone();
                } catch (GeminiException e) {
                    if (e.isFatal()) {
                        state = "FAILED";
                        message = e.getMessage();
                        break;
                    }
                    log.warn("AI text failed for product {}: {}", id, e.getMessage());
                    status.textFailed();
                    status.setMessage("Skipped \"" + shorten(p.getName()) + "\": " + e.getMessage());
                    Thread.sleep(gemini.delayMs());
                    continue;
                }

                if (withImages) {
                    try {
                        seo.generateImages(id);
                        status.imagesDone();
                    } catch (java.io.IOException e) {
                        log.warn("Pictures failed for product {}: {}", id, e.getMessage());
                        status.imagesFailed();
                        status.setMessage("Text saved, pictures failed for \"" + shorten(p.getName()) + "\": " + e.getMessage());
                    }
                }
                if (i < ids.size() - 1) {
                    Thread.sleep(gemini.delayMs());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state = "STOPPED";
            message = "Interrupted";
        } catch (Exception e) {
            log.error("AI batch failed", e);
            state = "FAILED";
            message = "Failed: " + e.getMessage();
        } finally {
            status.finish(state, message);
            running.set(false);
        }
    }

    private static String shorten(String s) {
        return s == null ? "" : (s.length() > 50 ? s.substring(0, 50) + "…" : s);
    }

    @PreDestroy
    void shutdown() {
        stopRequested = true;
        executor.shutdownNow();
    }
}
