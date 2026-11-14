package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.dto.NamedItem;
import com.beautyhaat.scraper.model.Brand;
import com.beautyhaat.scraper.model.Category;
import com.beautyhaat.scraper.model.SubCategory;
import com.beautyhaat.scraper.model.Tag;
import com.beautyhaat.scraper.repository.BrandRepository;
import com.beautyhaat.scraper.repository.CategoryRepository;
import com.beautyhaat.scraper.repository.SubCategoryRepository;
import com.beautyhaat.scraper.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds an existing brand / category / sub-category / tag or creates it. Used by the scraper and by the edit form. */
@Service
public class CatalogService {

    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final SubCategoryRepository subCategories;
    private final TagRepository tags;

    public CatalogService(BrandRepository brands, CategoryRepository categories,
                          SubCategoryRepository subCategories, TagRepository tags) {
        this.brands = brands;
        this.categories = categories;
        this.subCategories = subCategories;
        this.tags = tags;
    }

    public Brand brand(NamedItem item) {
        if (item == null || isBlank(item.name())) return null;
        String slug = slugOrDerived(item);
        return brands.findBySlug(slug)
                .or(() -> brands.findFirstByNameIgnoreCase(item.name().trim()))
                .orElseGet(() -> brands.save(new Brand(item.name().trim(), slug)));
    }

    public Category category(NamedItem item) {
        if (item == null || isBlank(item.name())) return null;
        String slug = slugOrDerived(item);
        return categories.findBySlug(slug)
                .or(() -> categories.findFirstByNameIgnoreCase(item.name().trim()))
                .orElseGet(() -> categories.save(new Category(item.name().trim(), slug)));
    }

    public SubCategory subCategory(NamedItem item, Category parent) {
        if (item == null || isBlank(item.name())) return null;
        String slug = slugOrDerived(item);
        return subCategories.findBySlug(slug)
                .or(() -> subCategories.findFirstByNameIgnoreCase(item.name().trim()))
                .orElseGet(() -> subCategories.save(new SubCategory(item.name().trim(), slug, parent)));
    }

    public Tag tag(NamedItem item) {
        if (item == null || isBlank(item.name())) return null;
        String slug = slugOrDerived(item);
        return tags.findBySlug(slug)
                .or(() -> tags.findFirstByNameIgnoreCase(item.name().trim()))
                .orElseGet(() -> tags.save(new Tag(item.name().trim(), slug)));
    }

    public Set<Tag> tags(List<NamedItem> items) {
        Set<Tag> result = new LinkedHashSet<>();
        if (items != null) {
            for (NamedItem i : items) {
                Tag t = tag(i);
                if (t != null) result.add(t);
            }
        }
        return result;
    }

    /** Names typed in the edit form (no slug known): the slug is derived from the name. */
    public static NamedItem byName(String name) {
        return isBlank(name) ? null : new NamedItem(name.trim(), null);
    }

    public static String slugify(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            s = "x" + Integer.toHexString(name == null ? 0 : name.hashCode());
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private static String slugOrDerived(NamedItem item) {
        return isBlank(item.slug()) ? slugify(item.name()) : item.slug();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
