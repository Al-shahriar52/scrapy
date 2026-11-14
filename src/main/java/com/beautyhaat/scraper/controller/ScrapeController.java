package com.beautyhaat.scraper.controller;

import com.beautyhaat.scraper.service.ScrapeStatus;
import com.beautyhaat.scraper.service.ScraperService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scrape")
public class ScrapeController {

    private final ScraperService scraper;

    public ScrapeController(ScraperService scraper) {
        this.scraper = scraper;
    }

    /**
     * Starts a background scrape. Leave endPage empty to continue until the site runs out of products.
     * details=true also opens every product page (sku, description, rating, brand, category, tags).
     * refresh=true re-reads details of products that already have them.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScrapeStatus start(@RequestParam(defaultValue = "1") int startPage,
                              @RequestParam(required = false) Integer endPage,
                              @RequestParam(defaultValue = "true") boolean details,
                              @RequestParam(defaultValue = "false") boolean refresh) {
        if (startPage < 1 || (endPage != null && endPage < startPage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page range");
        }
        if (!scraper.start(startPage, endPage, details, refresh)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A scrape is already running");
        }
        return scraper.status();
    }

    @GetMapping("/status")
    public ScrapeStatus status() {
        return scraper.status();
    }

    @PostMapping("/stop")
    public ScrapeStatus stop() {
        scraper.stop();
        return scraper.status();
    }
}
