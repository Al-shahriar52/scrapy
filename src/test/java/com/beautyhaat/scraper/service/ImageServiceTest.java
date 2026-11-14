package com.beautyhaat.scraper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageServiceTest {

    @TempDir
    Path tmp;

    @Test
    void drawsBothPicturesWithBanglaText() throws Exception {
        System.setProperty("java.awt.headless", "true");
        BufferedImage photo = new BufferedImage(300, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(new Color(0xD9A441));
        g.fillRoundRect(60, 100, 180, 480, 40, 40);
        g.dispose();

        ImageService images = new ImageService(tmp.toString(), "BeautyHaat");
        ImageService.Result r = images.render("test-serum", "Test Serum 30ml",
                List.of("ত্বককে উজ্জ্বল দেখাতে সাহায্য করে", "ত্বককে আর্দ্র রাখে"), photo);

        BufferedImage product = ImageIO.read(tmp.resolve(r.productFile()).toFile());
        BufferedImage benefits = ImageIO.read(tmp.resolve(r.benefitFile()).toFile());
        assertEquals(1000, product.getWidth());
        assertEquals(1200, benefits.getHeight());
        assertTrue(Files.size(tmp.resolve(r.benefitFile())) > 20_000, "benefits picture should not be blank");
        // the product picture has a white corner (clean background)
        assertEquals(0xFFFFFF, product.getRGB(5, 5) & 0xFFFFFF);
        // the benefits picture has the brand-coloured header band
        assertTrue((benefits.getRGB(20, 20) & 0xFFFFFF) != 0xFFFFFF);
    }
}
