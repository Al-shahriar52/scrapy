package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.dto.ProductDetail;
import com.beautyhaat.scraper.dto.ScrapedProduct;
import com.beautyhaat.scraper.model.Category;
import com.beautyhaat.scraper.model.Product;
import com.beautyhaat.scraper.repository.ProductRepository;
import jakarta.annotation.PreDestroy;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Walks through the shop pages (?page=1, 2, 3 ...) in the background, saves/updates the products found, and
 * (optionally) opens each product's own page to read SKU, description, rating, brand, category and tags.
 * Runs one job at a time.
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private final ProductRepository repository;
    private final ProductParser parser;
    private final ProductDetailParser detailParser;
    private final CatalogService catalog;
    private final ScraperProperties props;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "shajgoj-scraper");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScrapeStatus status = new ScrapeStatus();
    private volatile boolean stopRequested;

    public ScraperService(ProductRepository repository, ProductParser parser, ProductDetailParser detailParser,
                          CatalogService catalog, ScraperProperties props) {
        this.repository = repository;
        this.parser = parser;
        this.detailParser = detailParser;
        this.catalog = catalog;
        this.props = props;
    }

    public ScrapeStatus status() {
        return status;
    }

    /**
     * @param fetchDetails   also open every product page for sku, description, rating, brand, category, tags
     * @param refreshDetails re-read the details of products that already have them
     * @return false if a job is already running
     */
    public boolean start(int startPage, Integer endPage, boolean fetchDetails, boolean refreshDetails) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        stopRequested = false;
        int end = endPage == null ? props.maxPages() : Math.min(endPage, props.maxPages());
        status.begin(startPage, end, fetchDetails);
        executor.submit(() -> run(startPage, end, fetchDetails, refreshDetails));
        return true;
    }

    public void stop() {
        stopRequested = true;
    }

    private void run(int startPage, int endPage, boolean fetchDetails, boolean refreshDetails) {
        Set<String> seenThisRun = new HashSet<>();
        String finalState = "DONE";
        String finalMessage = "Reached the last requested page";
        try {
            for (int page = startPage; page <= endPage; page++) {
                if (stopRequested) {
                    finalState = "STOPPED";
                    finalMessage = "Stopped by user before page " + page;
                    break;
                }
                status.setCurrentPage(page);
                List<ScrapedProduct> items = fetchPage(page);

                if (items.isEmpty()) {
                    finalMessage = page == startPage
                            ? "No products found on page " + page + ". The page may load products with JavaScript, or the layout changed."
                            : "Page " + page + " has no products - finished.";
                    if (page == startPage) finalState = "FAILED";
                    break;
                }
                int fresh = 0;
                for (ScrapedProduct item : items) {
                    if (seenThisRun.add(item.slug())) fresh++;
                }
                if (fresh == 0) {
                    finalMessage = "Page " + page + " only repeated earlier products - finished.";
                    break;
                }

                int[] counts = save(items, page);
                status.pageDone(counts[0], counts[1]);
                String msg = "Page " + page + ": " + items.size() + " products (" + counts[0] + " new, " + counts[1] + " updated)";
                status.setMessage(msg);
                log.info("Scraped {}", msg);

                if (fetchDetails) {
                    for (ScrapedProduct item : items) {
                        if (stopRequested) break;
                        boolean requested = scrapeDetails(item.slug(), refreshDetails);
                        if (requested) {
                            status.setMessage(msg + " | details: " + status.getDetailsDone() + " done, " + status.getDetailsFailed() + " failed");
                            Thread.sleep(props.delayMs());
                        }
                    }
                }

                if (page < endPage) {
                    Thread.sleep(props.delayMs());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finalState = "STOPPED";
            finalMessage = "Interrupted";
        } catch (Exception e) {
            log.error("Scrape failed", e);
            finalState = "FAILED";
            finalMessage = "Failed: " + e.getMessage();
        } finally {
            status.finish(finalState, finalMessage);
            running.set(false);
        }
    }

    // ---------------------------------------------------------------- listing pages

    private List<ScrapedProduct> fetchPage(int page) throws IOException, InterruptedException {
        String url = props.baseUrl() + props.shopPath() + "?page=" + page;
        String body = fetchHtml(url);
        return body == null ? List.of() : parser.parse(body, url);
    }

    /** @return the page HTML, or null when the page does not exist (404) */
    private String fetchHtml(String url) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Connection.Response res = Jsoup.connect(url)
                        .userAgent(props.userAgent())
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .timeout(props.timeoutMs())
                        .ignoreHttpErrors(true)
                        .execute();
                if (res.statusCode() == 404) {
                    return null;
                }
                if (res.statusCode() >= 400) {
                    throw new IOException("HTTP " + res.statusCode() + " for " + url);
                }
                return res.body();
            } catch (IOException e) {
                last = e;
                log.warn("Attempt {} failed for {}: {}", attempt, url, e.getMessage());
                Thread.sleep(2000L * attempt);
            }
        }
        throw last;
    }

    /** @return {inserted, updated} */
    private int[] save(List<ScrapedProduct> items, int page) {
        int inserted = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ScrapedProduct item : items) {
            Product p = repository.findBySlug(item.slug()).orElse(null);
            boolean isNew = p == null;
            if (isNew) {
                p = new Product();
                p.setSlug(item.slug());
            }
            p.setName(item.name());
            p.setProductUrl(item.productUrl());
            p.setImageUrl(item.imageUrl());
            p.setRegularPrice(item.regularPrice());
            p.setSalePrice(item.salePrice());
            p.setDiscountPercent(item.discountPercent());
            p.setSize(item.size());
            p.setBadge(item.badge());
            p.setSourcePage(page);
            p.setScrapedAt(now);
            repository.save(p);
            if (isNew) inserted++;
            else updated++;
        }
        return new int[]{inserted, updated};
    }

    // ---------------------------------------------------------------- product pages

    /**
     * Opens one product page and stores its details. A failure here is logged and counted but never stops the run.
     *
     * @return true if a request to the site was made (so the caller should pause before the next one)
     */
    private boolean scrapeDetails(String slug, boolean refresh) throws InterruptedException {
        Optional<Product> found = repository.findBySlug(slug);
        if (found.isEmpty()) return false;
        Product product = found.get();
        if (!refresh && product.getDetailsScrapedAt() != null) return false;

        String url = props.baseUrl() + "/product/" + slug;
        try {
            String html = fetchHtml(url);
            if (html == null) {
                log.warn("Product page not found: {}", url);
                status.detailFailed();
                return true;
            }
            applyDetail(product, detailParser.parse(html, url));
            repository.save(product);
            status.detailDone();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not read details of {}: {}", url, e.getMessage());
            status.detailFailed();
        }
        return true;
    }

    private void applyDetail(Product p, ProductDetail d) {
        p.setSku(d.sku());
        p.setDescription(d.description());
        p.setRating(d.rating());
        p.setNumReviews(d.numReviews());
        Category category = catalog.category(d.category());
        p.setCategory(category);
        p.setSubCategory(catalog.subCategory(d.subCategory(), category));
        p.setBrand(catalog.brand(d.brand()));
        p.setTags(catalog.tags(d.tags()));
        p.setCategoryPath(d.categoryPath());
        p.setDetailsScrapedAt(LocalDateTime.now());
    }

    @PreDestroy
    void shutdown() {
        stopRequested = true;
        executor.shutdownNow();
    }
}
