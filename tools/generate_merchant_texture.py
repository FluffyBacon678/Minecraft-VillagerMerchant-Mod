"""Generate the polished 64x64 Merchant villager profession overlay.

The design is translated from the project's Merchant concept art onto the
vanilla villager model's exact UV islands.  The cloth ramp is intentionally
isolated from leather, metal, shirt, ledger, and emerald colors so future dye
variants can recolor the tailored garment without tinting its equipment.
Pillow is only a development-time dependency; the generated PNG is committed
and is what Minecraft loads.
"""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = (
    ROOT
    / "src/main/resources/assets/merchant_villager/textures/entity/villager/profession/merchant.png"
)

TRANSPARENT = (0, 0, 0, 0)
# Dye-ready tailored cloth ramp. Keep all recolorable pixels in this family.
CLOTH_SHADOW = (48, 10, 25, 255)
CLOTH_DARK = (69, 14, 32, 255)
CLOTH = (94, 21, 43, 255)
CLOTH_LIGHT = (126, 32, 57, 255)
CLOTH_HIGHLIGHT = (153, 47, 69, 255)

# Permanent material ramps: these must not change when the cloth is dyed.
WALNUT_DARK = (42, 25, 19, 255)
WALNUT = (65, 41, 29, 255)
WALNUT_LIGHT = (92, 62, 42, 255)
LEATHER_DARK = (92, 57, 39, 255)
LEATHER = (135, 91, 61, 255)
LEATHER_LIGHT = (181, 137, 96, 255)
CREAM_DARK = (181, 153, 120, 255)
CREAM = (218, 193, 158, 255)
CREAM_LIGHT = (239, 220, 188, 255)
GOLD_DARK = (137, 85, 13, 255)
GOLD = (218, 158, 26, 255)
GOLD_LIGHT = (252, 213, 69, 255)
EMERALD_DARK = (0, 103, 66, 255)
EMERALD = (0, 174, 96, 255)
EMERALD_LIGHT = (63, 218, 124, 255)


image = Image.new("RGBA", (64, 64), TRANSPARENT)
pixels = image.load()


def rectangle(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            pixels[x, y] = color


def shaded_face(
    x: int,
    y: int,
    width: int,
    height: int,
    base: tuple[int, int, int, int],
    dark: tuple[int, int, int, int],
    light: tuple[int, int, int, int],
) -> None:
    rectangle(x, y, x + width - 1, y + height - 1, base)
    for row in range(height):
        pixels[x, y + row] = dark
    for col in range(1, max(1, width - 1)):
        pixels[x + col, y] = light


# Low tailored merchant cap on the inflated hat layer (UV 32,0).
rectangle(40, 0, 47, 7, CLOTH)
rectangle(41, 0, 46, 0, CLOTH_HIGHLIGHT)
rectangle(40, 1, 40, 6, CLOTH_DARK)
rectangle(40, 6, 47, 7, CLOTH_SHADOW)
rectangle(48, 0, 55, 7, CLOTH_DARK)
rectangle(49, 1, 54, 5, CLOTH)
for start_x in (32, 40, 48, 56):
    rectangle(start_x, 8, start_x + 7, 9, CLOTH)
    rectangle(start_x + 1, 8, start_x + 6, 8, CLOTH_LIGHT)
    rectangle(start_x, 10, start_x + 7, 10, LEATHER_DARK)
    rectangle(start_x, 11, start_x + 7, 11, CLOTH_SHADOW)
# Gold-framed emerald seal centered above the villager's brow.
pixels[43, 9] = GOLD_LIGHT
pixels[44, 9] = GOLD
pixels[42, 10] = GOLD_DARK
pixels[43, 10] = EMERALD_LIGHT
pixels[44, 10] = EMERALD
pixels[45, 10] = GOLD_DARK

# Tailored coat and vest on the body cube (UV 16,20).
rectangle(22, 20, 29, 25, CLOTH_DARK)
rectangle(30, 20, 37, 25, WALNUT_DARK)
shaded_face(16, 26, 6, 12, CLOTH, CLOTH_DARK, CLOTH_LIGHT)
shaded_face(22, 26, 8, 12, CLOTH, CLOTH_DARK, CLOTH_LIGHT)
shaded_face(30, 26, 6, 12, CLOTH_DARK, CLOTH_SHADOW, CLOTH)
shaded_face(36, 26, 8, 12, CLOTH_DARK, CLOTH_SHADOW, CLOTH)
# Narrow cream shirt and crisp split lapels on the visible front.
rectangle(25, 26, 26, 31, CREAM)
pixels[25, 26] = CREAM_LIGHT
pixels[24, 26] = CLOTH_HIGHLIGHT
pixels[27, 26] = CLOTH_HIGHLIGHT
pixels[24, 27] = CLOTH_LIGHT
pixels[27, 27] = CLOTH_LIGHT
pixels[23, 28] = CLOTH_HIGHLIGHT
pixels[28, 28] = CLOTH_HIGHLIGHT
pixels[24, 29] = GOLD_DARK
pixels[27, 29] = GOLD_DARK
pixels[25, 30] = CREAM_DARK
pixels[26, 31] = CREAM_DARK
# Walnut belt and a compact gold-framed emerald clasp.
rectangle(22, 32, 29, 33, WALNUT_DARK)
rectangle(23, 32, 28, 32, WALNUT_LIGHT)
pixels[24, 33] = GOLD_DARK
pixels[25, 33] = EMERALD_LIGHT
pixels[26, 33] = EMERALD
pixels[27, 33] = GOLD_DARK
rectangle(22, 36, 29, 37, CLOTH_DARK)
# Tan cross-body strap and a shaded satchel on the back face.
for offset in range(8):
    pixels[36 + offset, 27 + offset] = LEATHER_LIGHT
    if 37 + offset <= 43:
        pixels[37 + offset, 27 + offset] = LEATHER
rectangle(39, 33, 43, 37, LEATHER_DARK)
rectangle(40, 34, 43, 36, LEATHER)
rectangle(40, 37, 43, 37, WALNUT_DARK)
pixels[41, 34] = GOLD

# Long apron/jacket layer (UV 0,38).  Front is a work apron; back keeps the
# burgundy coat and the satchel motif from the generated concept art.
rectangle(6, 38, 13, 43, CLOTH_DARK)
rectangle(14, 38, 21, 43, WALNUT_DARK)
shaded_face(0, 44, 6, 20, CLOTH_DARK, CLOTH_SHADOW, CLOTH)
shaded_face(6, 44, 8, 20, CLOTH, CLOTH_DARK, CLOTH_LIGHT)
shaded_face(14, 44, 6, 20, CLOTH_DARK, CLOTH_SHADOW, CLOTH)
shaded_face(20, 44, 8, 20, CLOTH, CLOTH_DARK, CLOTH_LIGHT)
# Fitted leather apron over visible burgundy coat side panels.
rectangle(8, 44, 8, 49, LEATHER)
rectangle(11, 44, 11, 49, LEATHER)
rectangle(8, 48, 11, 61, WALNUT)
rectangle(8, 48, 11, 49, WALNUT_DARK)
rectangle(9, 49, 10, 60, WALNUT_LIGHT)
rectangle(9, 50, 10, 60, WALNUT)
pixels[9, 48] = GOLD_DARK
pixels[10, 48] = GOLD
# Two readable stitched utility pockets and a short ledger chain.
rectangle(8, 54, 9, 58, WALNUT_DARK)
rectangle(10, 54, 11, 58, WALNUT_DARK)
pixels[9, 55] = LEATHER
pixels[10, 55] = LEATHER
pixels[9, 58] = LEATHER_DARK
pixels[10, 58] = LEATHER_DARK
for x, y in ((10, 50), (10, 51), (11, 52), (11, 53)):
    pixels[x, y] = GOLD
pixels[11, 54] = GOLD_LIGHT
rectangle(6, 61, 13, 61, GOLD_DARK)
rectangle(6, 62, 13, 63, CLOTH_DARK)
# Back strap and satchel.
for offset in range(8):
    pixels[20 + offset, 44 + offset] = LEATHER_LIGHT
    if 21 + offset <= 27:
        pixels[21 + offset, 44 + offset] = LEATHER
rectangle(23, 52, 27, 59, LEATHER_DARK)
rectangle(24, 53, 27, 57, LEATHER)
rectangle(23, 58, 27, 59, WALNUT_DARK)
pixels[25, 53] = GOLD
rectangle(20, 61, 27, 61, GOLD_DARK)
rectangle(20, 62, 27, 63, CLOTH_DARK)

# Sleeves and gloves on both folded outer arms (UV 44,22).
rectangle(48, 22, 51, 25, CLOTH_DARK)
rectangle(52, 22, 55, 25, WALNUT_DARK)
for start_x in (44, 48, 52, 56):
    rectangle(start_x, 26, start_x + 3, 29, CREAM)
    pixels[start_x + 1, 26] = CREAM_LIGHT
    pixels[start_x + 3, 29] = CREAM_DARK
    rectangle(start_x, 30, start_x + 3, 31, CLOTH)
    rectangle(start_x, 31, start_x + 3, 31, GOLD_DARK)
    rectangle(start_x, 32, start_x + 3, 33, WALNUT_DARK)
    pixels[start_x + 1, 32] = WALNUT_LIGHT

# The joined forearms hold a tiny gold-edged burgundy account ledger (UV 40,38).
rectangle(44, 38, 51, 41, WALNUT_DARK)
rectangle(52, 38, 59, 41, WALNUT_DARK)
rectangle(40, 42, 43, 45, CLOTH_DARK)
rectangle(52, 42, 55, 45, CLOTH_DARK)
rectangle(56, 42, 63, 45, WALNUT_DARK)
rectangle(44, 42, 51, 45, GOLD_DARK)
rectangle(45, 42, 50, 42, GOLD_LIGHT)
rectangle(45, 43, 50, 44, CLOTH)
pixels[45, 43] = CLOTH_LIGHT
rectangle(45, 45, 50, 45, GOLD_DARK)
pixels[47, 43] = EMERALD_DARK
pixels[48, 43] = EMERALD_LIGHT
pixels[48, 44] = EMERALD

# Dark merchant boots on the visible lower four pixels of each leg (UV 0,22).
rectangle(4, 22, 7, 25, WALNUT)
rectangle(8, 22, 11, 25, WALNUT_DARK)
for start_x in (0, 4, 8, 12):
    rectangle(start_x, 26, start_x + 3, 33, WALNUT)
    rectangle(start_x, 34, start_x + 3, 37, WALNUT_DARK)
    rectangle(start_x, 34, start_x + 3, 34, WALNUT_LIGHT)

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUTPUT, optimize=True)
print(f"Wrote {OUTPUT} ({image.size[0]}x{image.size[1]}, RGBA)")
