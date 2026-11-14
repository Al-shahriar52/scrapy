package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.dto.NamedItem;
import com.beautyhaat.scraper.dto.ProductDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sample pages shaped like the live product page (breadcrumb in a <nav>, brand link, tag links, SKU text). */
class ProductDetailParserTest {

    private static final String URL = "https://shop.shajgoj.com/product/skin-cafe-brightening-serum";

    private static final String PLAIN_PAGE = """
            <html><head><meta name="description" content="Short meta description."></head><body>
            <header><a href="/brand">Brands</a><a href="/product-category/makeup">Makeup</a></header>
            <nav aria-label="breadcrumb">
              <a href="/">Home</a><a href="/shop">Shop</a>
              <a href="/product-category/skin">Skin</a>
              <a href="/product-category/shop-by-concern-1-skin">Shop By Concern</a>
              <a href="/product-category/skin-lightening">Skin Lightening</a>
              <span>Skin Cafe Brightening Serum</span>
            </nav>
            <h1>Skin Cafe Brightening Serum</h1>
            <a href="/brand/skin-cafe">No #12 Best Seller in skin cafe</a>
            <a href="/brand/skin-cafe">skin cafe</a>
            <div><span>SKU:</span><span>38466</span></div>
            <div class="product-tags">
              <a href="/product-category/skin">Skin</a><a href="/product-category/serums">Serums</a>
              <a href="/product-category/free-delivery">Free Delivery</a><a href="/product-category/glow-up-deals">Glow Up Deals</a>
            </div>
            <div id="description"><h3>Description</h3>
              <p>A light, fast-absorbing serum for a brighter, more even complexion.</p>
              <ul><li>2% Alpha Arbutin fades dark spots</li><li>Hyaluronic Acid keeps skin hydrated</li></ul>
            </div>
            <div>Reviews ()</div>
            <footer><a href="/product-category/makeup">MAKEUP</a><a href="/brand/mamaearth">NEW ARRIVAL</a></footer>
            </body></html>
            """;

    private static final String JSON_LD_PAGE = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"BreadcrumbList"},
              {"@type":"Product","name":"X","sku":"A-77","description":"<p>First para.</p><p>Second para.</p>",
               "brand":{"@type":"Brand","name":"Nivea"},
               "aggregateRating":{"@type":"AggregateRating","ratingValue":"4.6","reviewCount":"128"}}
            ]}
            </script></head><body>
            <nav><a href="/">Home</a><a href="/shop">Shop</a><a href="/product-category/skin">Skin</a></nav>
            <h1>X</h1>
            </body></html>
            """;

    private final ProductDetailParser parser = new ProductDetailParser();

    @Test
    void readsCategorySubCategoryAndPathFromBreadcrumb() {
        ProductDetail d = parser.parse(PLAIN_PAGE, URL);
        assertEquals(new NamedItem("Skin", "skin"), d.category());
        assertEquals(new NamedItem("Skin Lightening", "skin-lightening"), d.subCategory());
        assertEquals("Skin > Shop By Concern > Skin Lightening", d.categoryPath());
    }

    @Test
    void readsBrandPreferringTheShortLinkTextAndIgnoringFooter() {
        ProductDetail d = parser.parse(PLAIN_PAGE, URL);
        assertEquals(new NamedItem("skin cafe", "skin-cafe"), d.brand());
    }

    @Test
    void readsTagsWithoutHeaderFooterOrBreadcrumbLinks() {
        ProductDetail d = parser.parse(PLAIN_PAGE, URL);
        List<String> slugs = d.tags().stream().map(NamedItem::slug).toList();
        assertEquals(List.of("skin", "serums", "free-delivery", "glow-up-deals"), slugs);
        assertFalse(slugs.contains("makeup"));
        assertFalse(slugs.contains("skin-lightening"));
    }

    @Test
    void readsSkuAndDescriptionWithLineBreaks() {
        ProductDetail d = parser.parse(PLAIN_PAGE, URL);
        assertEquals("38466", d.sku());
        assertNotNull(d.description());
        assertTrue(d.description().contains("A light, fast-absorbing serum"));
        assertTrue(d.description().contains("\n2% Alpha Arbutin fades dark spots"));
    }

    @Test
    void ratingAndReviewsAreNullWhenPageShowsNone() {
        ProductDetail d = parser.parse(PLAIN_PAGE, URL);
        assertNull(d.rating());
        assertNull(d.numReviews());
    }

    @Test
    void prefersJsonLdWhenPresent() {
        ProductDetail d = parser.parse(JSON_LD_PAGE, URL);
        assertEquals("A-77", d.sku());
        assertEquals(4.6, d.rating());
        assertEquals(128L, d.numReviews());
        assertEquals("Nivea", d.brand().name());
        assertEquals("nivea", d.brand().slug());
        assertEquals("First para.\nSecond para.", d.description());
        assertEquals("Skin", d.category().name());
        assertNull(d.subCategory());
    }

    @Test
    void emptyPageGivesEmptyDetail() {
        ProductDetail d = parser.parse("<html><body><p>nothing</p></body></html>", URL);
        assertNull(d.sku());
        assertNull(d.brand());
        assertNull(d.category());
        assertTrue(d.tags().isEmpty());
    }
}
