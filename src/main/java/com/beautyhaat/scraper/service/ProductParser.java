package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.dto.ScrapedProduct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the HTML of a Shajgoj shop page into a list of products.
 *
 * Every product card contains a link to /product/{slug}. The card text looks like:
 *   "{name} ... SALE ৳ 295.00 ৳ 249.00 50gm 16% OFF"
 * so the parser works from that text rather than from fragile CSS class names.
 */
@Component
public class ProductParser {

    private static final String LINK_SELECTOR = "a[href*=\"/product/\"]";
    private static final Pattern PRICE = Pattern.compile("৳\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern DISCOUNT = Pattern.compile("(\\d{1,3})\\s*%\\s*OFF", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEARANCE = Pattern.compile("CLEARANCE\\s+SALE", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_TO_CART = Pattern.compile("ADD\\s+TO\\s+CART", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_SALE = Pattern.compile("\\bsale\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_IMAGE_URL = Pattern.compile("[?&]url=([^&]+)");

    public List<ScrapedProduct> parse(String html, String pageUrl) {
        Document doc = Jsoup.parse(html, pageUrl);
        Map<String, ScrapedProduct> found = new LinkedHashMap<>();

        for (Element link : doc.select(LINK_SELECTOR)) {
            String productUrl = stripQuery(link.absUrl("href"));
            String slug = slugOf(productUrl);
            if (slug == null || found.containsKey(slug)) {
                continue;
            }
            Element card = findCard(link);
            if (card == null) {
                continue;
            }
            ScrapedProduct product = parseCard(card, slug, productUrl, pageUrl);
            if (product != null) {
                found.put(slug, product);
            }
        }
        return new ArrayList<>(found.values());
    }

    // ---------------------------------------------------------------- card

    /** The link itself if it holds a price, otherwise a close parent that holds only this one product. */
    private Element findCard(Element link) {
        Element el = link;
        for (int depth = 0; depth < 3 && el != null; depth++) {
            if (PRICE.matcher(spacedText(el)).find()) {
                return el;
            }
            Element parent = el.parent();
            if (parent == null || "body".equals(parent.tagName())) {
                return null;
            }
            Set<String> slugs = new LinkedHashSet<>();
            for (Element a : parent.select(LINK_SELECTOR)) {
                String s = slugOf(stripQuery(a.absUrl("href")));
                if (s != null) slugs.add(s);
            }
            if (slugs.size() > 1) {
                return null; // parent holds several products, don't mix them up
            }
            el = parent;
        }
        return null;
    }

    private ScrapedProduct parseCard(Element card, String slug, String productUrl, String pageUrl) {
        String text = spacedText(card);

        // ---- prices
        List<BigDecimal> prices = new ArrayList<>();
        int firstPriceStart = -1;
        int lastPriceEnd = -1;
        Matcher pm = PRICE.matcher(text);
        while (pm.find()) {
            if (firstPriceStart < 0) firstPriceStart = pm.start();
            lastPriceEnd = pm.end();
            prices.add(new BigDecimal(pm.group(1).replace(",", "")));
        }
        if (prices.isEmpty()) {
            return null;
        }
        BigDecimal regular = prices.get(0);
        BigDecimal sale = prices.size() > 1 ? prices.get(1) : prices.get(0);
        if (sale.compareTo(regular) > 0) {
            BigDecimal tmp = regular;
            regular = sale;
            sale = tmp;
        }

        String before = text.substring(0, firstPriceStart).trim();
        String after = text.substring(lastPriceEnd).trim();

        // ---- name
        Set<String> variants = new LinkedHashSet<>();
        for (Element h : card.select("h1,h2,h3,h4,h5,h6")) {
            addIfUseful(variants, spacedText(h));
        }
        for (Element img : card.select("img[alt]")) {
            addIfUseful(variants, normalize(img.attr("alt")));
        }
        for (Element t : card.select("[class*=title]")) {
            addIfUseful(variants, spacedText(t));
        }
        String name = variants.isEmpty() ? firstRepeatedChunk(before) : variants.iterator().next();
        if (name == null || name.isBlank()) {
            return null;
        }

        // ---- badge ("FREE SHIPPING" / "SALE") sits between the name and the price
        String leftover = before;
        Set<String> toStrip = new LinkedHashSet<>(variants);
        toStrip.add(name);
        for (String v : new ArrayList<>(toStrip)) {
            int dash = v.indexOf(" – ");
            if (dash > 0) toStrip.add(v.substring(0, dash));
        }
        List<String> ordered = new ArrayList<>(toStrip);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        for (String v : ordered) {
            leftover = leftover.replace(v, " ");
        }
        List<String> badges = new ArrayList<>();
        String leftoverLower = leftover.toLowerCase();
        if (leftoverLower.contains("free shipping")) {
            badges.add("FREE SHIPPING");
        } else if (WORD_SALE.matcher(leftover).find()) {
            badges.add("SALE");
        }
        if (CLEARANCE.matcher(after).find()) {
            badges.add("CLEARANCE SALE");
        }
        String badge = badges.isEmpty() ? null : String.join(", ", badges);

        // ---- discount
        Integer discount = null;
        Matcher dm = DISCOUNT.matcher(after);
        if (dm.find()) {
            discount = Integer.parseInt(dm.group(1));
        } else if (regular.compareTo(sale) > 0 && regular.signum() > 0) {
            discount = regular.subtract(sale)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(regular, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        // ---- size: whatever is left after the prices once discount/labels are removed
        String size = DISCOUNT.matcher(after).replaceAll(" ");
        size = CLEARANCE.matcher(size).replaceAll(" ");
        size = ADD_TO_CART.matcher(size).replaceAll(" ");
        size = normalize(size);
        if (size.isEmpty()) {
            size = null;
        } else if (size.length() > 100) {
            size = size.substring(0, 100);
        }

        return new ScrapedProduct(
                slug,
                truncate(name, 1000),
                productUrl,
                imageOf(card, pageUrl),
                regular,
                sale,
                discount,
                size,
                badge
        );
    }

    // ---------------------------------------------------------------- image

    private String imageOf(Element card, String pageUrl) {
        for (Element img : card.select("img")) {
            String raw = firstNonBlank(img.attr("data-src"), img.attr("data-lazy-src"), img.attr("src"), firstSrcset(img.attr("srcset")));
            if (raw == null || raw.startsWith("data:") || raw.toLowerCase().contains(".svg")) {
                continue;
            }
            return resolveImage(raw, pageUrl);
        }
        return null;
    }

    private String resolveImage(String raw, String pageUrl) {
        try {
            URI abs = URI.create(pageUrl).resolve(raw.trim());
            if (abs.getPath() != null && abs.getPath().startsWith("/_next/image")) {
                Matcher m = NEXT_IMAGE_URL.matcher(raw);
                if (m.find()) {
                    String original = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
                    return URI.create(pageUrl).resolve(original).toString();
                }
            }
            return abs.toString();
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private String firstSrcset(String srcset) {
        if (srcset == null || srcset.isBlank()) return null;
        String first = srcset.split(",")[0].trim();
        int space = first.indexOf(' ');
        return space > 0 ? first.substring(0, space) : first;
    }

    // ---------------------------------------------------------------- helpers

    /** Text of an element with a space between every text node, so "৳ 295.00৳ 249.00" never glues together. */
    static String spacedText(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode tn) {
                    String t = tn.text().trim();
                    if (!t.isEmpty()) sb.append(t).append(' ');
                }
            }

            @Override
            public void tail(Node node, int depth) {
            }
        }, el);
        return normalize(sb.toString());
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.replace(' ', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void addIfUseful(Set<String> set, String value) {
        String v = normalize(value);
        if (v.length() >= 3) set.add(v);
    }

    /** Fallback: cards repeat the name ("X X X"), so return the first chunk that is repeated right after itself. */
    private String firstRepeatedChunk(String s) {
        for (int k = 3; k <= s.length() / 2; k++) {
            String p = s.substring(0, k).trim();
            if (!p.isEmpty() && s.startsWith(p + " " + p)) {
                return p;
            }
        }
        return s;
    }

    private static String stripQuery(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        int cut = (q >= 0 && h >= 0) ? Math.min(q, h) : Math.max(q, h);
        return cut >= 0 ? url.substring(0, cut) : url;
    }

    private static String slugOf(String productUrl) {
        if (productUrl == null) return null;
        int idx = productUrl.indexOf("/product/");
        if (idx < 0) return null;
        String slug = productUrl.substring(idx + "/product/".length());
        while (slug.endsWith("/")) slug = slug.substring(0, slug.length() - 1);
        if (slug.isBlank() || slug.contains("/")) return null;
        return slug.length() > 500 ? slug.substring(0, 500) : slug;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
