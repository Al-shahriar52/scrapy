package com.beautyhaat.scraper.repository;

import com.beautyhaat.scraper.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findBySlug(String slug);
    Optional<Tag> findFirstByNameIgnoreCase(String name);
}
