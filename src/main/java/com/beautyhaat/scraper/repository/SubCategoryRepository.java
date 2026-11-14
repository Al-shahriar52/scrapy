package com.beautyhaat.scraper.repository;

import com.beautyhaat.scraper.model.SubCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {
    Optional<SubCategory> findBySlug(String slug);
    Optional<SubCategory> findFirstByNameIgnoreCase(String name);
}
