package com.beautyhaat.scraper.controller;

import com.beautyhaat.scraper.dto.PageResponse;
import com.beautyhaat.scraper.dto.ProductResponse;
import com.beautyhaat.scraper.dto.ProductUpdateRequest;
import com.beautyhaat.scraper.dto.NamedItem;
import com.beautyhaat.scraper.model.Category;
import com.beautyhaat.scraper.model.Product;
import com.beautyhaat.scraper.repository.ProductRepository;
import com.beautyhaat.scraper.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Set<String> SORTABLE = Set.of("id", "name", "sku", "regularPrice", "salePrice",
            "discountPercent", "rating", "numReviews", "updatedAt");

    private final ProductRepository repository;
    private final CatalogService catalog;

    public ProductController(ProductRepository repository, CatalogService catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String dir) {

        String sortField = SORTABLE.contains(sortBy) ? sortBy : "id";
        Sort sort = "asc".equalsIgnoreCase(dir) ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);

        String term = q.trim();
        var result = term.isEmpty() ? repository.findAll(pageable) : repository.search(term, pageable);
        return PageResponse.of(result.map(ProductResponse::from));
    }

    @PutMapping("/{id}")
    @Transactional
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest req) {
        Product p = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        p.setName(req.name().trim());
        p.setRegularPrice(req.regularPrice());
        p.setSalePrice(req.salePrice());
        p.setDiscountPercent(req.discountPercent());
        p.setSize(blankToNull(req.size()));
        p.setBadge(blankToNull(req.badge()));
        p.setImageUrl(blankToNull(req.imageUrl()));
        p.setDescription(blankToNull(req.description()));
        p.setSku(blankToNull(req.sku()));
        p.setRating(req.rating());
        p.setNumReviews(req.numReviews());

        p.setBrand(catalog.brand(CatalogService.byName(req.brand())));
        Category category = catalog.category(CatalogService.byName(req.category()));
        p.setCategory(category);
        p.setSubCategory(catalog.subCategory(CatalogService.byName(req.subCategory()), category));

        List<NamedItem> tagItems = req.tags() == null ? List.of()
                : req.tags().stream().map(CatalogService::byName).filter(java.util.Objects::nonNull).toList();
        p.setTags(catalog.tags(tagItems));

        return ProductResponse.from(repository.save(p));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
