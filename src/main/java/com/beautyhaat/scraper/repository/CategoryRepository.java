package com.beautyhaat.scraper.repository;

import com.beautyhaat.scraper.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    Optional<Category> findFirstByNameIgnoreCase(String name);
}
