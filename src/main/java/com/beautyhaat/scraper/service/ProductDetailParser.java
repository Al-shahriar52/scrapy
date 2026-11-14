package com.beautyhaat.scraper.service;

import com.beautyhaat.scraper.dto.NamedItem;
import com.beautyhaat.scraper.dto.ProductDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the extra data from a product page (/product/{slug}).
 *
 * Order of preference for every field: structured data (JSON-LD) when the page has it, otherwise the visible
 * page: breadcrumb links (/product-category/...), the brand link (/brand/...), the tag links
 * (/product-category/... outside the breadcrumb), "SKU: ..." text and the description block.
 */
@Component
public class ProductDetailParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DESCRIPTION = 20000;

    private static final Pattern SKU = Pattern.compile("\\bSKU\\b\\s*[:#\\-]?\\s*([A-Za-z0-9][A-Za-z0-9._\\-]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RATING_OUT_OF_5 = Pattern.compile("(\\d(?:\\.\\d+)?)\\s*(?:out of|/)\\s*5\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVIEWS_PAREN = Pattern.compile("\\bReviews?\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVIEWS_COUNT = Pattern.compile("\\b(\\d+)\\s+(?:customer\\s+)?(?:reviews?|ratings?)\\b", Pattern.CASE_INSENSITIVE);

    public ProductDetail parse(String html, String pageUrl) {
        Document doc = Jsoup.parse(html, pageUrl);
        JsonNode ld = findProductJsonLd(doc);

        // The breadcrumb is read first: it often sits inside a <nav>, which is removed below.
        List<NamedItem> crumbs = new ArrayList<>();
        Element crumbRoot = findBreadcrumb(doc, crumbs);
        if (crumbRoot != null) {
            crumbRoot.remove();
        }

        String metaDescription = firstNonBlank(
                attr(doc, "meta[name=description]", "content"),
                attr(doc, "meta[property=og:description]", "content"));

        // Header, footer and menus link to every category and brand on the site; keep only the product area.
        doc.select("script, style, noscript, header, footer, nav").remove();
        String text = doc.body() == null ? "" : ProductParser.spacedText(doc.body());

        // ---- category / sub-category
        NamedItem category = crumbs.isEmpty() ? null : crumbs.get(0);
        NamedItem subCategory = crumbs.size() > 1 ? crumbs.get(crumbs.size() - 1) : null;
        String categoryPath = crumbs.isEmpty() ? null
                : String.join(" > ", crumbs.stream().map(NamedItem::name).toList());

        // ---- brand
        NamedItem brand = findBrand(doc);
        if (brand == null && ld != null) {
            String n = nameOf(ld.path("brand"));
            if (n != null) brand = new NamedItem(n, CatalogService.slugify(n));
        }

        // ---- tags
        List<NamedItem> tags = categoryLinks(doc);
        if (tags.size() > 40) tags = new ArrayList<>(tags.subList(0, 40));

        // ---- sku
        String sku = ld == null ? null : blankToNull(ld.path("sku").asText(""));
        if (sku == null) {
            Matcher m = SKU.matcher(text);
            if (m.find()) sku = m.group(1);
        }
        if (sku != null && sku.length() > 100) sku = sku.substring(0, 100);

        // ---- rating & reviews
        Double rating = null;
        Long numReviews = null;
        if (ld != null && ld.has("aggregateRating")) {
            JsonNode ar = ld.get("aggregateRating");
            rating = parseDouble(ar.path("ratingValue").asText(""));
            numReviews = parseLong(firstNonBlank(ar.path("reviewCount").asText(""), ar.path("ratingCount").asText("")));
        }
        if (rating == null) {
            Element ratingEl = doc.selectFirst("[itemprop=ratingValue]");
            if (ratingEl != null) {
                rating = parseDouble(firstNonBlank(ratingEl.attr("content"), ratingEl.text()));
            }
        }
        if (rating == null) {
            Matcher m = RATING_OUT_OF_5.matcher(text);
            if (m.find()) rating = parseDouble(m.group(1));
        }
        if (numReviews == null) {
            Matcher m = REVIEWS_PAREN.matcher(text);
            if (m.find()) {
                numReviews = parseLong(m.group(1));
            } else {
                m = REVIEWS_COUNT.matcher(text);
                if (m.find()) numReviews = parseLong(m.group(1));
            }
        }
        if (rating != null && (rating < 0 || rating > 5)) rating = null;

        // ---- description
        String description = ld == null ? null : blankToNull(htmlToText(ld.path("description").asText("")));
        if (description == null) {
            description = descriptionFromPage(doc);
        }
        if (description == null) {
            description = blankToNull(ProductParser.normalize(metaDescription));
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION);
        }

        return new ProductDetail(sku, description, rating, numReviews, brand, category, subCategory, tags, categoryPath);
    }

    // ---------------------------------------------------------------- JSON-LD

    private JsonNode findProductJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode hit = findProduct(JSON.readTree(script.data()));
                if (hit != null) return hit;
            } catch (Exception ignored) {
                // malformed JSON-LD: just ignore this block
            }
        }
        return null;
    }

    private JsonNode findProduct(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode n : node) {
                JsonNode hit = findProduct(n);
                if (hit != null) return hit;
            }
            return null;
        }
        if (!node.isObject()) return null;
        JsonNode type = node.path("@type");
        boolean isProduct = type.isTextual() ? "Product".equalsIgnoreCase(type.asText())
                : type.isArray() && containsText(type, "Product");
        if (isProduct) return node;
        return findProduct(node.path("@graph"));
    }

    private boolean containsText(JsonNode array, String value) {
        for (JsonNode n : array) {
            if (value.equalsIgnoreCase(n.asText())) return true;
        }
        return false;
    }

    private String nameOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String s = node.isObject() ? node.path("name").asText("") : node.asText("");
        return blankToNull(ProductParser.normalize(s));
    }

    // ---------------------------------------------------------------- breadcrumb, brand, tags

    /** Finds "Home > Shop > Skin > ... " by locating the "Shop" link and the container with category links around it. */
    private Element findBreadcrumb(Document doc, List<NamedItem> crumbsOut) {
        for (Element shop : doc.select("a[href]")) {
            if (!"shop".equalsIgnoreCase(ProductParser.normalize(shop.text()))) continue;
            if (!pathOf(shop.absUrl("href")).endsWith("/shop")) continue;
            if (shop.parents().stream().anyMatch(p -> "footer".equals(p.tagName()))) continue;

            Element container = shop.parent();
            for (int i = 0; i < 3 && container != null; i++, container = container.parent()) {
                String tag = container.tagName();
                if ("body".equals(tag) || "html".equals(tag) || "main".equals(tag)) break;
                List<NamedItem> found = categoryLinks(container);
                if (!found.isEmpty()) {
                    crumbsOut.addAll(found);
                    return container;
                }
            }
        }
        return null;
    }

    private List<NamedItem> categoryLinks(Element root) {
        Map<String, NamedItem> bySlug = new LinkedHashMap<>();
        for (Element a : root.select("a[href*=/product-category/]")) {
            String slug = lastSegmentAfter(a.absUrl("href"), "/product-category/");
            String name = ProductParser.normalize(a.text());
            if (slug == null || name.isEmpty()) continue;
            bySlug.putIfAbsent(slug, new NamedItem(name, slug));
        }
        return new ArrayList<>(bySlug.values());
    }

    /** First brand link on the page. If the same brand is linked twice (e.g. "Best Seller in X"), the shortest text wins. */
    private NamedItem findBrand(Document doc) {
        String chosenSlug = null;
        String chosenName = null;
        for (Element a : doc.select("a[href*=/brand/]")) {
            String slug = lastSegmentAfter(a.absUrl("href"), "/brand/");
            String name = ProductParser.normalize(a.text());
            if (slug == null || name.isEmpty()) continue;
            if (chosenSlug == null) {
                chosenSlug = slug;
                chosenName = name;
            } else if (chosenSlug.equals(slug) && name.length() < chosenName.length()) {
                chosenName = name;
            }
        }
        return chosenSlug == null ? null : new NamedItem(chosenName, chosenSlug);
    }

    // ---------------------------------------------------------------- description

    private String descriptionFromPage(Document doc) {
        Element best = null;
        int bestLen = 0;
        Elements candidates = doc.select("[itemprop=description], [id~=(?i)description], [class~=(?i)description]");
        for (Element e : candidates) {
            String t = ProductParser.spacedText(e);
            if (t.length() < 40 || t.contains("৳") || t.toUpperCase().contains("ADD TO CART")) continue;
            if (t.length() > bestLen) {
                best = e;
                bestLen = t.length();
            }
        }
        if (best == null) return null;
        String text = blockText(best);
        // drop a leading "Description" tab/heading label
        text = text.replaceFirst("(?i)^\\s*(product\\s+)?description\\s*\\n", "");
        return blankToNull(text);
    }

    /** Text with one line per paragraph / list item / heading, instead of one long run. */
    static String blockText(Element el) {
        Elements blocks = el.select("p, li, h1, h2, h3, h4, h5, h6, tr");
        if (blocks.isEmpty()) {
            return ProductParser.spacedText(el);
        }
        Set<String> lines = new LinkedHashSet<>();
        for (Element b : blocks) {
            String t = ProductParser.spacedText(b);
            if (!t.isEmpty()) lines.add(t);
        }
        return lines.isEmpty() ? ProductParser.spacedText(el) : String.join("\n", lines);
    }

    private String htmlToText(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.indexOf('<') < 0) return ProductParser.normalize(s);
        return blockText(Jsoup.parseBodyFragment(s).body());
    }

    // ---------------------------------------------------------------- helpers

    private static String pathOf(String url) {
        try {
            String p = URI.create(url).getPath();
            if (p == null) return "";
            return p.endsWith("/") && p.length() > 1 ? p.substring(0, p.length() - 1) : p;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** "https://x/product-category/skin/lightening?x=1" with marker "/product-category/" -> "lightening". */
    private static String lastSegmentAfter(String url, String marker) {
        if (url == null) return null;
        int i = url.indexOf(marker);
        if (i < 0) return null;
        String rest = url.substring(i + marker.length());
        int cut = rest.length();
        for (char c : new char[]{'?', '#'}) {
            int k = rest.indexOf(c);
            if (k >= 0) cut = Math.min(cut, k);
        }
        rest = rest.substring(0, cut);
        while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
        int slash = rest.lastIndexOf('/');
        if (slash >= 0) rest = rest.substring(slash + 1);
        return rest.isBlank() ? null : (rest.length() > 300 ? rest.substring(0, 300) : rest);
    }

    private static String attr(Document doc, String selector, String attribute) {
        Element e = doc.selectFirst(selector);
        return e == null ? null : e.attr(attribute);
    }

    private static Double parseDouble(String s) {
        try {
            return s == null || s.isBlank() ? null : Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
