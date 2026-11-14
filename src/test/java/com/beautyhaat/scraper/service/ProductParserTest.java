package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.dto.ScrapedProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses HTML shaped like the live shop page (name repeated in the card, badge, "৳ old৳ new", size, "% OFF"),
 * with no whitespace between the spans to make sure the prices are still split correctly.
 */
class ProductParserTest {

    private static final String PAGE_URL = "https://shop.shajgoj.com/shop?page=3";

    private static final String HTML = """
            <html><body>
            <nav><a href="/product-category/skin">Skin</a><a href="/brand">Brands</a></nav>
            <ul>
              <li>
                <a href="/product/rajkonna-light-moisturizer-50gm">
                  <img alt="Rajkonna Light Moisturizer – 50gm" src="/_next/image?url=https%3A%2F%2Fcdn.example.com%2Fraj.jpg&w=384&q=75">
                  <h3>Rajkonna Light Moisturizer</h3><span>SALE</span><span>৳ 295.00</span><span>৳ 249.00</span><span>50gm</span><span>16% OFF</span>
                </a>
                <button>ADD TO CART</button>
              </li>
              <li>
                <a href="/product/yc-baby-bath-gel">
                  <img alt="Yc Baby Bath gel – 100ml" src="https://cdn.example.com/yc.png">
                  <h3>Yc Baby Bath gel</h3><span>SALE</span><span>৳ 260.00</span><span>৳ 75.00</span><span>100ml</span><span>CLEARANCE SALE</span><span>71% OFF</span>
                </a>
              </li>
              <li>
                <a href="/product/skin-cafe-brightening-serum?ref=x">
                  <img alt="Skin Cafe Brightening Serum" src="/assets/serum.jpg">
                  <h3>Skin Cafe Brightening Serum</h3><span>FREE SHIPPING</span><span>৳ 1,650.00</span><span>৳ 1,520.50</span><span>30 ml</span><span>8% OFF</span>
                </a>
              </li>
              <li>
                <a href="/product/freedom-sanitary-napkin">
                  <h3>Freedom Sanitary Napkin</h3><span>৳ 200.00</span>
                </a>
              </li>
              <li>
                <!-- same product linked twice (e.g. image link + title link) must only be stored once -->
                <a href="/product/freedom-sanitary-napkin"><span>Freedom Sanitary Napkin</span><span>৳ 200.00</span></a>
              </li>
            </ul>
            </body></html>
            """;

    private final ProductParser parser = new ProductParser();

    @Test
    void parsesAllProductsOnceEach() {
        List<ScrapedProduct> items = parser.parse(HTML, PAGE_URL);
        assertEquals(4, items.size());
        assertEquals(List.of("rajkonna-light-moisturizer-50gm", "yc-baby-bath-gel", "skin-cafe-brightening-serum", "freedom-sanitary-napkin"),
                items.stream().map(ScrapedProduct::slug).toList());
    }

    @Test
    void parsesSaleProduct() {
        ScrapedProduct p = parser.parse(HTML, PAGE_URL).get(0);
        assertEquals("Rajkonna Light Moisturizer", p.name());
        assertEquals(new BigDecimal("295.00"), p.regularPrice());
        assertEquals(new BigDecimal("249.00"), p.salePrice());
        assertEquals(16, p.discountPercent());
        assertEquals("50gm", p.size());
        assertEquals("SALE", p.badge());
        assertEquals("https://shop.shajgoj.com/product/rajkonna-light-moisturizer-50gm", p.productUrl());
        assertEquals("https://cdn.example.com/raj.jpg", p.imageUrl()); // unwrapped from /_next/image
    }

    @Test
    void parsesClearanceBadgeAndAbsoluteImage() {
        ScrapedProduct p = parser.parse(HTML, PAGE_URL).get(1);
        assertEquals("SALE, CLEARANCE SALE", p.badge());
        assertEquals(new BigDecimal("75.00"), p.salePrice());
        assertEquals(71, p.discountPercent());
        assertEquals("100ml", p.size());
        assertEquals("https://cdn.example.com/yc.png", p.imageUrl());
    }

    @Test
    void parsesThousandsSeparatorFreeShippingAndQueryString() {
        ScrapedProduct p = parser.parse(HTML, PAGE_URL).get(2);
        assertEquals("FREE SHIPPING", p.badge());
        assertEquals(new BigDecimal("1650.00"), p.regularPrice());
        assertEquals(new BigDecimal("1520.50"), p.salePrice());
        assertEquals("30 ml", p.size());
        assertEquals("skin-cafe-brightening-serum", p.slug());
        assertEquals("https://shop.shajgoj.com/assets/serum.jpg", p.imageUrl());
    }

    @Test
    void parsesSinglePriceProduct() {
        ScrapedProduct p = parser.parse(HTML, PAGE_URL).get(3);
        assertEquals(new BigDecimal("200.00"), p.regularPrice());
        assertEquals(new BigDecimal("200.00"), p.salePrice());
        assertNull(p.discountPercent());
        assertNull(p.badge());
        assertNull(p.size());
        assertNull(p.imageUrl());
    }

    @Test
    void ignoresNavigationLinksAndEmptyPages() {
        assertTrue(parser.parse("<html><body><a href=\"/product-category/skin\">Skin</a></body></html>", PAGE_URL).isEmpty());
        assertNotNull(parser.parse(HTML, PAGE_URL));
    }

    @Test
    void worksWhenCardRepeatsTheNameWithoutHeadings() {
        String html = """
                <a href="/product/oyumea-cotton-pad-90-pcs">Oyumea Cotton Pad - 90 pcs – 90pcs Oyumea Cotton Pad - 90 pcs – 90pcs Oyumea Cotton Pad - 90 pcs FREE SHIPPING ৳ 300.00৳ 175.00 90pcs 42% OFF</a>
                """;
        ScrapedProduct p = parser.parse(html, PAGE_URL).get(0);
        assertEquals("Oyumea Cotton Pad - 90 pcs – 90pcs", p.name());
        assertEquals("FREE SHIPPING", p.badge());
        assertEquals(new BigDecimal("175.00"), p.salePrice());
        assertEquals(42, p.discountPercent());
        assertEquals("90pcs", p.size());
    }
}
