package com.beautyhaat.scraper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Last part of the product URL, e.g. "skin-cafe-brightening-serum". Used to avoid duplicates. */
    @Column(nullable = false, unique = true, length = 500)
    private String slug;

    @Column(nullable = false, length = 1000)
    private String name;

    @Column(length = 1000)
    private String productUrl;

    @Column(length = 2000)
    private String imageUrl;

    @Column(precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal salePrice;

    private Integer discountPercent;

    @Column(name = "pack_size", length = 100)
    private String size;

    /** e.g. "FREE SHIPPING", "SALE", "CLEARANCE SALE" */
    @Column(length = 100)
    private String badge;

    // ---------- details (read from the product's own page)

    @Lob
    private String description;

    @Column(length = 100)
    private String sku;

    private Double rating;

    private Long numReviews;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sub_category_id")
    private SubCategory subCategory;

    /** A product can carry many tags on Shajgoj (e.g. "Serums", "Free Delivery", "Glow Up Deals"). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "product_tags",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 50)
    private Set<Tag> tags = new HashSet<>();

    /** Full breadcrumb, e.g. "Skin > Shop By Concern > Skin Lightening". */
    @Column(length = 1000)
    private String categoryPath;

    private LocalDateTime detailsScrapedAt;

    // ---------- AI-written SEO content (Gemini)

    @Column(length = 300)
    private String seoName;

    @Column(length = 300)
    private String seoSlug;

    @Column(length = 400)
    private String metaDescription;

    @Lob
    private String seoDescription;

    /** Bangla benefit lines, one per line. */
    @Lob
    private String benefitsBn;

    @Column(length = 300)
    private String imageAlt;

    /** null = not generated yet, DRAFT = written by AI and waiting for review, APPROVED = reviewed by you. */
    @Column(length = 20)
    private String seoStatus;

    private LocalDateTime seoGeneratedAt;

    /** File names inside the images folder (served at /media/...). */
    @Column(length = 400)
    private String productImageFile;

    @Column(length = 400)
    private String benefitImageFile;

    private LocalDateTime imagesGeneratedAt;

    private Integer sourcePage;

    private LocalDateTime scrapedAt;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getRegularPrice() { return regularPrice; }
    public void setRegularPrice(BigDecimal regularPrice) { this.regularPrice = regularPrice; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Long getNumReviews() { return numReviews; }
    public void setNumReviews(Long numReviews) { this.numReviews = numReviews; }

    public Brand getBrand() { return brand; }
    public void setBrand(Brand brand) { this.brand = brand; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public SubCategory getSubCategory() { return subCategory; }
    public void setSubCategory(SubCategory subCategory) { this.subCategory = subCategory; }

    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }

    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }

    public LocalDateTime getDetailsScrapedAt() { return detailsScrapedAt; }
    public void setDetailsScrapedAt(LocalDateTime detailsScrapedAt) { this.detailsScrapedAt = detailsScrapedAt; }

    public String getSeoName() { return seoName; }
    public void setSeoName(String seoName) { this.seoName = seoName; }

    public String getSeoSlug() { return seoSlug; }
    public void setSeoSlug(String seoSlug) { this.seoSlug = seoSlug; }

    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }

    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }

    public String getBenefitsBn() { return benefitsBn; }
    public void setBenefitsBn(String benefitsBn) { this.benefitsBn = benefitsBn; }

    public String getImageAlt() { return imageAlt; }
    public void setImageAlt(String imageAlt) { this.imageAlt = imageAlt; }

    public String getSeoStatus() { return seoStatus; }
    public void setSeoStatus(String seoStatus) { this.seoStatus = seoStatus; }

    public LocalDateTime getSeoGeneratedAt() { return seoGeneratedAt; }
    public void setSeoGeneratedAt(LocalDateTime seoGeneratedAt) { this.seoGeneratedAt = seoGeneratedAt; }

    public String getProductImageFile() { return productImageFile; }
    public void setProductImageFile(String productImageFile) { this.productImageFile = productImageFile; }

    public String getBenefitImageFile() { return benefitImageFile; }
    public void setBenefitImageFile(String benefitImageFile) { this.benefitImageFile = benefitImageFile; }

    public LocalDateTime getImagesGeneratedAt() { return imagesGeneratedAt; }
    public void setImagesGeneratedAt(LocalDateTime imagesGeneratedAt) { this.imagesGeneratedAt = imagesGeneratedAt; }

    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }

    public LocalDateTime getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(LocalDateTime scrapedAt) { this.scrapedAt = scrapedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
