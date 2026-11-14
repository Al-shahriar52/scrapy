package com.beautyhaat.scraper.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes the two product pictures with plain Java2D (no AI image model needed):
 *  1. {slug}.jpg              - the real product photo, centred on a clean white square
 *  2. {slug}-benefits-bn.jpg  - product photo + the Bangla benefit lines drawn as real text
 *
 * Bangla is drawn with the bundled Noto Sans Bengali font, so the spelling is always exactly what the AI wrote
 * (image-generation models often garble Bangla letters).
 */
@Service
public class ImageService {

    public record Result(String productFile, String benefitFile) {
    }

    private static final Color BRAND = new Color(0xB3366B);
    private static final Color INK = new Color(0x1F2328);
    private static final Color MUTED = new Color(0x8A6F7B);
    private static final int MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024;

    private final Path dir;
    private final String footerText;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Font bengaliBold;
    private Font bengaliMedium;

    public ImageService(@Value("${images.dir:./data/images}") String dir,
                        @Value("${images.footer-text:BeautyHaat}") String footerText) {
        this.dir = Path.of(dir).toAbsolutePath();
        this.footerText = footerText;
    }

    public Path directory() {
        return dir;
    }

    // ---------------------------------------------------------------- public API

    /** Downloads the product photo (PNG / JPEG / GIF). */
    public BufferedImage download(String imageUrl) throws IOException, InterruptedException {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IOException("This product has no image URL");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                .header("Accept", "image/png,image/jpeg,image/*;q=0.8")
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() >= 400) {
            throw new IOException("Image download failed: HTTP " + res.statusCode());
        }
        try (InputStream in = res.body()) {
            byte[] data = in.readNBytes(MAX_DOWNLOAD_BYTES + 1);
            if (data.length > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Image is larger than 15 MB");
            }
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (img == null) {
                throw new IOException("Unsupported image format (WebP/AVIF are not supported): " + imageUrl);
            }
            return img;
        }
    }

    public Result render(String slug, String productName, List<String> benefitsBn, BufferedImage photo) throws IOException {
        Files.createDirectories(dir);
        String safe = slug.replaceAll("[^a-zA-Z0-9\\-]", "-");
        String productFile = safe + ".jpg";
        String benefitFile = safe + "-benefits-bn.jpg";
        writeJpeg(productImage(photo), dir.resolve(productFile));
        writeJpeg(benefitImage(productName, benefitsBn, photo), dir.resolve(benefitFile));
        return new Result(productFile, benefitFile);
    }

    // ---------------------------------------------------------------- image 1: product on white

    BufferedImage productImage(BufferedImage photo) {
        int size = 1000;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(out);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        drawFit(g, photo, 50, 50, 900, 900);
        g.dispose();
        return out;
    }

    // ---------------------------------------------------------------- image 2: Bangla benefits

    BufferedImage benefitImage(String productName, List<String> benefits, BufferedImage photo) throws IOException {
        loadFonts();
        final int W = 1200;
        final int H = 1200;
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(out);
        FontRenderContext frc = g.getFontRenderContext();

        // background
        g.setPaint(new GradientPaint(0, 0, new Color(0xFFF8F3), 0, H, new Color(0xFCE4EE)));
        g.fillRect(0, 0, W, H);

        // header band with the Bangla title "উপকারিতা" (benefits)
        g.setColor(BRAND);
        g.fillRect(0, 0, W, 170);
        drawCentered(g, "উপকারিতা", bengaliBold.deriveFont(84f), Color.WHITE, W / 2f, 170 / 2f, frc);

        // product name (Latin script), max two lines
        List<TextLayout> nameLines = fitName(productName == null ? "" : productName.trim(), 1000, frc);
        float y = 200;
        g.setColor(INK);
        for (TextLayout t : nameLines) {
            float x = (W - (float) t.getBounds().getWidth()) / 2f;
            t.draw(g, x, y + t.getAscent());
            y += t.getAscent() + t.getDescent() + 6;
        }

        // product photo on a white rounded card
        int box = 360;
        int boxX = (W - box) / 2;
        int boxY = (int) y + 14;
        g.setColor(new Color(0, 0, 0, 20));
        g.fill(new RoundRectangle2D.Float(boxX + 4, boxY + 8, box, box, 44, 44));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(boxX, boxY, box, box, 44, 44));
        drawFit(g, photo, boxX + 20, boxY + 20, box - 40, box - 40);

        // benefits card
        int cardX = 70;
        int cardY = boxY + box + 26;
        int cardW = W - 140;
        int cardH = H - 100 - cardY;
        g.setColor(new Color(0, 0, 0, 16));
        g.fill(new RoundRectangle2D.Float(cardX + 3, cardY + 6, cardW, cardH, 44, 44));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 44, 44));

        List<String> items = new ArrayList<>();
        if (benefits != null) {
            for (String b : benefits) {
                if (b != null && !b.isBlank()) items.add(b.trim());
            }
        }
        drawBenefits(g, items, cardX, cardY, cardW, cardH, frc);

        // footer
        if (footerText != null && !footerText.isBlank()) {
            drawCentered(g, footerText, new Font(Font.SANS_SERIF, Font.BOLD, 30), MUTED, W / 2f, H - 50, frc);
        }
        g.dispose();
        return out;
    }

    private void drawBenefits(Graphics2D g, List<String> items, int cardX, int cardY, int cardW, int cardH, FontRenderContext frc) {
        int pad = 44;
        float usableH = cardH - 2f * pad;
        for (float size = 48f; size >= 28f; size -= 2f) {
            float iconD = size * 0.95f;
            float gap = 22f;
            float textW = cardW - 2f * pad - iconD - gap;
            float gapBetween = size * 0.5f;

            List<List<TextLayout>> wrapped = new ArrayList<>();
            float total = 0;
            for (String item : items) {
                List<TextLayout> lines = wrap(item, bengaliMedium.deriveFont(size), new Font(Font.SANS_SERIF, Font.BOLD, (int) size), textW, frc);
                wrapped.add(lines);
                total += blockHeight(lines);
            }
            total += gapBetween * Math.max(0, items.size() - 1);

            if (total <= usableH || size <= 28f) {
                float y = cardY + pad + Math.max(0, (usableH - total) / 2f);
                for (List<TextLayout> lines : wrapped) {
                    float h = blockHeight(lines);
                    float iconY = y + (lineHeight(lines.get(0)) - iconD) / 2f;
                    drawCheck(g, cardX + pad, iconY, iconD);
                    float ty = y;
                    for (TextLayout t : lines) {
                        g.setColor(INK);
                        t.draw(g, cardX + pad + iconD + gap, ty + t.getAscent());
                        ty += lineHeight(t);
                    }
                    y += h + gapBetween;
                }
                return;
            }
        }
    }

    private static float lineHeight(TextLayout t) {
        return t.getAscent() + t.getDescent() + t.getLeading() + 4f;
    }

    private static float blockHeight(List<TextLayout> lines) {
        float h = 0;
        for (TextLayout t : lines) h += lineHeight(t);
        return h;
    }

    private void drawCheck(Graphics2D g, float x, float y, float d) {
        g.setColor(BRAND);
        g.fill(new Ellipse2D.Float(x, y, d, d));
        Path2D check = new Path2D.Float();
        check.moveTo(x + d * 0.26, y + d * 0.53);
        check.lineTo(x + d * 0.43, y + d * 0.70);
        check.lineTo(x + d * 0.75, y + d * 0.33);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(3f, d * 0.11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(check);
        g.setStroke(new BasicStroke(1f));
    }

    // ---------------------------------------------------------------- text helpers

    /**
     * Wraps text to a width. Characters the primary font cannot draw (e.g. Latin letters in the Bengali font)
     * use the fallback font, so mixed text such as "SPF 30" is never drawn as empty boxes.
     */
    private List<TextLayout> wrap(String text, Font primary, Font fallback, float width, FontRenderContext frc) {
        List<TextLayout> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        AttributedString as = new AttributedString(text);
        as.addAttribute(TextAttribute.FONT, primary);
        if (fallback != null) {
            for (int i = 0; i < text.length(); i++) {
                if (!primary.canDisplay(text.charAt(i))) {
                    as.addAttribute(TextAttribute.FONT, fallback.deriveFont(primary.getSize2D()), i, i + 1);
                }
            }
        }
        AttributedCharacterIterator it = as.getIterator();
        LineBreakMeasurer m = new LineBreakMeasurer(it, frc);
        while (m.getPosition() < it.getEndIndex()) {
            lines.add(m.nextLayout(width));
        }
        return lines;
    }

    private void drawCentered(Graphics2D g, String text, Font font, Color color, float cx, float cy, FontRenderContext frc) {
        List<TextLayout> lines = wrap(text, font, new Font(Font.SANS_SERIF, Font.BOLD, font.getSize()), 10000, frc);
        if (lines.isEmpty()) return;
        TextLayout t = lines.get(0);
        float w = (float) t.getBounds().getWidth();
        g.setColor(color);
        t.draw(g, cx - w / 2f, cy + (t.getAscent() - t.getDescent()) / 2f);
    }

    /** The product name on at most two lines: shrinks the font first, then cuts the text and adds "…". */
    private List<TextLayout> fitName(String name, float width, FontRenderContext frc) {
        if (name.isEmpty()) return List.of();
        for (int size = 34; size >= 24; size -= 2) {
            List<TextLayout> lines = wrap(name, new Font(Font.SANS_SERIF, Font.BOLD, size), null, width, frc);
            if (lines.size() <= 2) return lines;
        }
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 24);
        String s = name;
        while (s.length() > 4) {
            s = s.substring(0, s.length() - 4).trim();
            List<TextLayout> lines = wrap(s + "…", font, null, width, frc);
            if (lines.size() <= 2) return lines;
        }
        return wrap(s + "…", font, null, width, frc);
    }

    // ---------------------------------------------------------------- picture helpers

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Draws the photo as large as possible inside the box, keeping its shape (up to 2x enlargement). */
    private static void drawFit(Graphics2D g, BufferedImage src, int x, int y, int w, int h) {
        double scale = Math.min(Math.min((double) w / src.getWidth(), (double) h / src.getHeight()), 2.0);
        int tw = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int th = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage scaled = scaleHigh(src, tw, th);
        g.drawImage(scaled, x + (w - tw) / 2, y + (h - th) / 2, null);
    }

    /** Halves the picture step by step when shrinking a lot, which looks much sharper than one big jump. */
    private static BufferedImage scaleHigh(BufferedImage src, int tw, int th) {
        BufferedImage cur = toArgb(src);
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return (cur.getWidth() == tw && cur.getHeight() == th) ? cur : resize(cur, tw, th);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void writeJpeg(BufferedImage img, Path file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.88f);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(Files.newOutputStream(file))) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private synchronized void loadFonts() throws IOException {
        if (bengaliBold != null) return;
        bengaliBold = loadFont("/fonts/NotoSansBengali_700Bold.ttf");
        bengaliMedium = loadFont("/fonts/NotoSansBengali_500Medium.ttf");
    }

    private static Font loadFont(String resource) throws IOException {
        try (InputStream in = ImageService.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Font file missing from the project: " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (java.awt.FontFormatException e) {
            throw new IOException("Could not read font " + resource, e);
        }
    }
}
