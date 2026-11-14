package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.model.Product;
import com.beautyhaat.scraper.model.Tag;
import com.beautyhaat.scraper.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Writes SEO content with Gemini and draws the two product pictures. */
@Service
public class SeoContentService {

    private final ProductRepository repository;
    private final GeminiClient gemini;
    private final ImageService images;

    public SeoContentService(ProductRepository repository, GeminiClient gemini, ImageService images) {
        this.repository = repository;
        this.gemini = gemini;
        this.images = images;
    }

    public Product load(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    /** Asks Gemini for the SEO name, slug, descriptions and Bangla benefits, and saves them as a DRAFT. */
    public Product generateText(Long id) throws GeminiException, InterruptedException {
        Product p = load(id);
        GeminiClient.SeoContent c = gemini.generateSeo(factsOf(p));

        p.setSeoName(c.seoName());
        p.setSeoSlug(uniqueSlug(c.seoName(), p.getId()));
        p.setMetaDescription(c.metaDescription());
        p.setSeoDescription(c.description());
        p.setBenefitsBn(String.join("\n", c.benefitsBn()));
        p.setImageAlt(c.imageAlt());
        p.setSeoStatus("DRAFT");
        p.setSeoGeneratedAt(LocalDateTime.now());
        return repository.save(p);
    }

    /** Draws the two pictures from the product's own photo and its saved Bangla benefit lines. */
    public Product generateImages(Long id) throws IOException, InterruptedException {
        Product p = load(id);
        if (p.getSeoSlug() == null || p.getBenefitsBn() == null || p.getBenefitsBn().isBlank()) {
            throw new IOException("Generate the SEO text first - the Bangla benefit lines are needed for the picture");
        }
        BufferedImage photo = images.download(p.getImageUrl());
        List<String> lines = benefitLines(p.getBenefitsBn());
        ImageService.Result r = images.render(p.getSeoSlug(), p.getSeoName() != null ? p.getSeoName() : p.getName(), lines, photo);
        p.setProductImageFile(r.productFile());
        p.setBenefitImageFile(r.benefitFile());
        p.setImagesGeneratedAt(LocalDateTime.now());
        return repository.save(p);
    }

    /** Slug from the SEO name; a number is added if another product already uses it. */
    public String uniqueSlug(String text, Long productId) {
        String base = CatalogService.slugify(text);
        if (base.length() > 110) {
            base = base.substring(0, 110);
            int cut = base.lastIndexOf('-');
            if (cut > 40) base = base.substring(0, cut);
        }
        String slug = base;
        for (int n = 2; repository.existsBySeoSlugAndIdNot(slug, productId); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    public static List<String> benefitLines(String text) {
        return Arrays.stream(text.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static GeminiClient.ProductFacts factsOf(Product p) {
        List<String> tags = p.getTags().stream().map(Tag::getName).sorted(Comparator.comparing(String::toLowerCase)).toList();
        return new GeminiClient.ProductFacts(
                p.getName(),
                p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getSubCategory() == null ? null : p.getSubCategory().getName(),
                tags, p.getSize(), p.getSku(), p.getDescription());
    }
}
