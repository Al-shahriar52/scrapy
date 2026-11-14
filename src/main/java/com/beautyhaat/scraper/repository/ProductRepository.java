package com.beautyhaat.scraper.repository;

import com.beautyhaat.scraper.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySeoSlugAndIdNot(String seoSlug, Long id);

    /** Products that have no AI content yet. */
    @Query("select p.id from Product p where p.seoGeneratedAt is null order by p.id")
    List<Long> idsWithoutSeo(Pageable pageable);

    /** Everything except products you have already approved. */
    @Query("select p.id from Product p where p.seoStatus is null or p.seoStatus <> 'APPROVED' order by p.id")
    List<Long> idsNotApproved(Pageable pageable);

    /** Matches the product name, SKU, brand name or category name. */
    @Query("""
            select p from Product p
            left join p.brand b
            left join p.category c
            where lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.sku) like lower(concat('%', :q, '%'))
               or lower(b.name) like lower(concat('%', :q, '%'))
               or lower(c.name) like lower(concat('%', :q, '%'))
            """)
    Page<Product> search(@Param("q") String q, Pageable pageable);
}
