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

        int opaquePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                boolean opaque = (image.getRGB(x, y) >>> 24) != 0;
                assertEquals(
                    isMerchantUvIsland(x, y),
                    opaque,
                    "profession overlay changed the vanilla UV mask at " + x + "," + y
                );
                if (opaque) {
                    opaquePixels++;
                }
            }
        }
        assertEquals(1_888, opaquePixels);

        // These five colors are the dye-ready cloth contract. Permanent
        // leather, shirt, gold, emerald, boots, and ledger pixels deliberately
        // use separate colors and can remain unchanged when dyeing is added.
        for (int clothRgb : new int[] {
            0x300A19, 0x450E20, 0x5E152B, 0x7E2039, 0x992F45
        }) {
            assertTrue(containsOpaqueRgb(image, clothRgb), "missing cloth shade " + clothRgb);
        }
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

    private static boolean containsOpaqueRgb(BufferedImage image, int expectedRgb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == expectedRgb
                    && (image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMerchantUvIsland(int x, int y) {
        if (y <= 7) {
            return between(x, 40, 55);
        }
        if (y <= 11) {
            return between(x, 32, 63);
        }
        if (y >= 20 && y <= 21) {
            return between(x, 22, 37);
        }
        if (y >= 22 && y <= 25) {
            return between(x, 4, 11) || between(x, 22, 37) || between(x, 48, 55);
        }
        if (y >= 26 && y <= 33) {
            return between(x, 0, 59);
        }
        if (y >= 34 && y <= 37) {
            return between(x, 0, 43);
        }
        if (y >= 38 && y <= 41) {
            return between(x, 6, 21) || between(x, 44, 59);
        }
        if (y >= 42 && y <= 43) {
            return between(x, 6, 21) || between(x, 40, 63);
        }
        if (y >= 44 && y <= 45) {
            return between(x, 0, 27) || between(x, 40, 63);
        }
        return y >= 46 && between(x, 0, 27);
    }

    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }
}
