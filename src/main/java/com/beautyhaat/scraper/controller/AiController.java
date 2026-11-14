package com.beautyhaat.scraper.controller;

import com.beautyhaat.scraper.dto.ProductResponse;
import com.beautyhaat.scraper.dto.SeoUpdateRequest;
import com.beautyhaat.scraper.model.Product;
import com.beautyhaat.scraper.repository.ProductRepository;
import com.beautyhaat.scraper.service.AiJobService;
import com.beautyhaat.scraper.service.AiStatus;
import com.beautyhaat.scraper.service.CatalogService;
import com.beautyhaat.scraper.service.GeminiClient;
import com.beautyhaat.scraper.service.GeminiException;
import com.beautyhaat.scraper.service.SeoContentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiJobService jobs;
    private final SeoContentService seo;
    private final GeminiClient gemini;
    private final ProductRepository repository;

    public AiController(AiJobService jobs, SeoContentService seo, GeminiClient gemini, ProductRepository repository) {
        this.jobs = jobs;
        this.seo = seo;
        this.gemini = gemini;
        this.repository = repository;
    }

    /** Progress of the batch plus whether a Gemini key is set (the key itself is never sent out). */
    @GetMapping("/status")
    public Map<String, Object> status() {
        AiStatus s = jobs.status();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", gemini.isConfigured());
        out.put("model", gemini.model());
        out.put("state", s.getState());
        out.put("total", s.getTotal());
        out.put("done", s.getDone());
        out.put("failed", s.getFailed());
        out.put("withImages", s.isWithImages());
        out.put("imagesDone", s.getImagesDone());
        out.put("imagesFailed", s.getImagesFailed());
        out.put("current", s.getCurrent());
        out.put("message", s.getMessage());
        return out;
    }

    /** Names you can put in gemini.model. */
    @GetMapping("/models")
    public List<String> models() {
        try {
            return gemini.listModels();
        } catch (GeminiException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> startBatch(@RequestParam(defaultValue = "20") int limit,
                                          @RequestParam(defaultValue = "true") boolean images,
                                          @RequestParam(defaultValue = "false") boolean regenerate) {
        if (!gemini.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gemini API key is not set. Set the GEMINI_API_KEY environment variable and restart the app.");
        }
        if (limit < 1 || limit > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 2000");
        }
        int queued = jobs.start(limit, images, regenerate);
        if (queued < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An AI batch is already running");
        }
        return status();
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        jobs.stop();
        return status();
    }

    /** One product, now: writes the text (and pictures if images=true). Takes a few seconds. */
    @PostMapping("/products/{id}/generate")
    public ProductResponse generateOne(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean images) {
        guardNoBatch();
        try {
            seo.generateText(id);
            if (images) {
                try {
                    seo.generateImages(id);
                } catch (IOException e) {
                    // the text is saved; tell the user why the pictures are missing
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Text saved, but the pictures failed: " + e.getMessage());
                }
            }
            return ProductResponse.from(seo.load(id));
        } catch (GeminiException e) {
            throw new ResponseStatusException(statusFor(e), e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
    }

    /** Redraws both pictures from the saved Bangla lines (use after editing them). No Gemini call. */
    @PostMapping("/products/{id}/images")
    public ProductResponse redrawImages(@PathVariable Long id) {
        try {
            return ProductResponse.from(seo.generateImages(id));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
    }

    /** Saves your edits to the AI text; status APPROVED protects the product from being regenerated. */
    @PutMapping("/products/{id}/seo")
    public ProductResponse saveSeo(@PathVariable Long id, @Valid @RequestBody SeoUpdateRequest req) {
        Product p;
        try {
            p = seo.load(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        p.setSeoName(req.seoName().trim());
        String wantedSlug = req.seoSlug() == null || req.seoSlug().isBlank() ? req.seoName() : req.seoSlug();
        p.setSeoSlug(seo.uniqueSlug(wantedSlug, p.getId()));
        p.setMetaDescription(blankToNull(req.metaDescription()));
        p.setSeoDescription(blankToNull(req.seoDescription()));
        List<String> lines = req.benefitsBn() == null ? List.of()
                : req.benefitsBn().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        p.setBenefitsBn(lines.isEmpty() ? null : String.join("\n", lines));
        p.setImageAlt(blankToNull(req.imageAlt()));
        p.setSeoStatus(req.status() == null ? (p.getSeoStatus() == null ? "DRAFT" : p.getSeoStatus()) : req.status());
        if (p.getSeoGeneratedAt() == null) p.setSeoGeneratedAt(LocalDateTime.now());
        return ProductResponse.from(repository.save(p));
    }

    private void guardNoBatch() {
        if (jobs.isRunning()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A batch is running - wait for it to finish or stop it first");
        }
    }

    private static HttpStatus statusFor(GeminiException e) {
        return switch (e.kind()) {
            case AUTH -> HttpStatus.BAD_REQUEST;
            case DAILY_QUOTA, RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
