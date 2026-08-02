package com.fluffybacon.merchantvillager.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AssetValidationTest {
    @Test
    void merchantProfessionOverlayUsesVanillaUvDimensionsAndTransparency() throws IOException {
        BufferedImage image = load(
            "/assets/merchant_villager/textures/entity/villager/profession/merchant.png"
        );

        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertTrue(hasAlpha(image, 0), "profession overlay needs transparent pixels");
        assertTrue(hasAlpha(image, 255), "profession overlay needs visible pixels");
    }

    @Test
    void fabricIconIsSquareAndReleaseReady() throws IOException {
        BufferedImage image = load("/assets/merchant_villager/icon.png");

        assertEquals(128, image.getWidth());
        assertEquals(128, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
    }

    @Test
    void modrinthProjectIconMeetsUploadLimit() throws IOException {
        Path iconPath = Path.of("art", "merchant-villager-project-icon.png");

        assertTrue(Files.isRegularFile(iconPath), "missing prepared Modrinth project icon");
        assertTrue(
            Files.size(iconPath) <= 256L * 1024L,
            "Modrinth project icons may be at most 256 KiB"
        );
        BufferedImage image = ImageIO.read(iconPath.toFile());
        assertNotNull(image, "prepared Modrinth project icon must be a readable image");
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
    }

    private static BufferedImage load(String path) throws IOException {
        try (InputStream stream = AssetValidationTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "unreadable PNG " + path);
            return image;
        }
    }

    private static boolean hasAlpha(BufferedImage image, int expected) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == expected) {
                    return true;
                }
            }
        }
        return false;
    }
}
