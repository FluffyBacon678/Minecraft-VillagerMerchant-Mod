"""Generate the original 64x64 Merchant villager profession overlay.

The design is translated from art/merchant-profession-concept.png onto the
vanilla villager model's exact UV islands.  Pillow is only a development-time
dependency; the generated PNG is committed and is what Minecraft loads.
"""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = (
    ROOT
    / "src/main/resources/assets/merchant_villager/textures/entity/villager/profession/merchant.png"
)

TRANSPARENT = (0, 0, 0, 0)
BURGUNDY_DARK = (72, 20, 33, 255)
BURGUNDY = (93, 25, 39, 255)
BURGUNDY_LIGHT = (119, 31, 49, 255)
WALNUT_DARK = (47, 30, 23, 255)
WALNUT = (67, 43, 30, 255)
WALNUT_LIGHT = (88, 62, 45, 255)
LEATHER = (129, 88, 64, 255)
LEATHER_LIGHT = (176, 135, 105, 255)
CREAM_DARK = (176, 135, 105, 255)
CREAM = (205, 170, 139, 255)
GOLD_DARK = (151, 100, 18, 255)
GOLD = (226, 174, 40, 255)
GOLD_LIGHT = (249, 211, 75, 255)
EMERALD_DARK = (0, 103, 66, 255)
EMERALD = (0, 174, 96, 255)
EMERALD_LIGHT = (63, 218, 124, 255)


image = Image.new("RGBA", (64, 64), TRANSPARENT)
pixels = image.load()


def rectangle(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            pixels[x, y] = color


def shaded_face(x: int, y: int, width: int, height: int, base: tuple[int, int, int, int]) -> None:
    rectangle(x, y, x + width - 1, y + height - 1, base)
    for row in range(height):
        pixels[x, y + row] = BURGUNDY_DARK if base in (BURGUNDY, BURGUNDY_LIGHT) else WALNUT_DARK
    for col in range(1, max(1, width - 1)):
        pixels[x + col, y] = BURGUNDY_LIGHT if base in (BURGUNDY, BURGUNDY_LIGHT) else WALNUT_LIGHT


# Low burgundy merchant cap on the inflated hat layer (UV 32,0).
rectangle(40, 0, 47, 7, BURGUNDY)
rectangle(40, 0, 47, 1, BURGUNDY_LIGHT)
rectangle(40, 6, 47, 7, BURGUNDY_DARK)
rectangle(48, 0, 55, 7, BURGUNDY_DARK)
for start_x in (32, 40, 48, 56):
    rectangle(start_x, 8, start_x + 7, 11, BURGUNDY)
    rectangle(start_x, 8, start_x + 7, 8, BURGUNDY_LIGHT)
    rectangle(start_x, 11, start_x + 7, 11, BURGUNDY_DARK)
# Gold-framed emerald seal centered above the villager's brow.
pixels[43, 9] = GOLD_LIGHT
pixels[44, 9] = GOLD
pixels[42, 10] = GOLD_DARK
pixels[43, 10] = EMERALD_LIGHT
pixels[44, 10] = EMERALD
pixels[45, 10] = GOLD_DARK

# Tailored vest on the body cube (UV 16,20).
rectangle(22, 20, 29, 25, BURGUNDY_DARK)
rectangle(30, 20, 37, 25, WALNUT_DARK)
shaded_face(16, 26, 6, 12, BURGUNDY)
shaded_face(22, 26, 8, 12, BURGUNDY)
shaded_face(30, 26, 6, 12, BURGUNDY)
shaded_face(36, 26, 8, 12, BURGUNDY_DARK)
# Cream shirt and split lapels on the front.
rectangle(24, 26, 27, 28, CREAM)
rectangle(25, 29, 26, 30, CREAM_DARK)
pixels[23, 27] = BURGUNDY_LIGHT
pixels[28, 27] = BURGUNDY_LIGHT
pixels[24, 29] = BURGUNDY_LIGHT
pixels[27, 29] = BURGUNDY_LIGHT
# Walnut belt and emerald clasp.
rectangle(22, 33, 29, 34, WALNUT_DARK)
pixels[24, 33] = GOLD_DARK
pixels[25, 33] = EMERALD_LIGHT
pixels[26, 33] = EMERALD
pixels[27, 33] = GOLD_DARK
# Tan cross-body strap and a shaded satchel on the back face.
for offset in range(8):
    pixels[36 + offset, 27 + offset] = LEATHER_LIGHT
    if 37 + offset <= 43:
        pixels[37 + offset, 27 + offset] = LEATHER
rectangle(39, 33, 43, 37, LEATHER)
rectangle(40, 34, 43, 36, LEATHER_LIGHT)
rectangle(40, 37, 43, 37, WALNUT_DARK)
pixels[41, 34] = GOLD

# Long apron/jacket layer (UV 0,38).  Front is a work apron; back keeps the
# burgundy coat and the satchel motif from the generated concept art.
rectangle(6, 38, 13, 43, BURGUNDY_DARK)
rectangle(14, 38, 21, 43, WALNUT_DARK)
shaded_face(0, 44, 6, 20, WALNUT)
shaded_face(6, 44, 8, 20, WALNUT)
shaded_face(14, 44, 6, 20, WALNUT)
shaded_face(20, 44, 8, 20, BURGUNDY)
# Burgundy apron straps and belt.
rectangle(6, 44, 7, 51, BURGUNDY)
rectangle(12, 44, 13, 51, BURGUNDY)
rectangle(6, 48, 13, 49, WALNUT_DARK)
pixels[9, 48] = GOLD_DARK
pixels[10, 48] = EMERALD_LIGHT
pixels[11, 48] = GOLD_DARK
# Front utility pocket with a small gold ledger chain.
rectangle(8, 55, 12, 60, WALNUT_DARK)
rectangle(9, 56, 11, 59, WALNUT_LIGHT)
rectangle(9, 56, 11, 56, LEATHER)
for x, y in ((10, 50), (10, 51), (11, 52), (11, 53), (12, 54)):
    pixels[x, y] = GOLD
pixels[12, 55] = GOLD_LIGHT
rectangle(6, 62, 13, 63, WALNUT_DARK)
# Back strap and satchel.
for offset in range(8):
    pixels[20 + offset, 44 + offset] = LEATHER_LIGHT
    if 21 + offset <= 27:
        pixels[21 + offset, 44 + offset] = LEATHER
rectangle(23, 52, 27, 59, LEATHER)
rectangle(24, 53, 27, 57, LEATHER_LIGHT)
rectangle(23, 58, 27, 59, WALNUT_DARK)
pixels[25, 53] = GOLD
rectangle(20, 62, 27, 63, BURGUNDY_DARK)

# Sleeves and gloves on both folded outer arms (UV 44,22).
rectangle(48, 22, 51, 25, BURGUNDY_DARK)
rectangle(52, 22, 55, 25, WALNUT_DARK)
for start_x in (44, 48, 52, 56):
    rectangle(start_x, 26, start_x + 3, 31, BURGUNDY)
    rectangle(start_x, 30, start_x + 3, 30, GOLD_DARK)
    rectangle(start_x, 31, start_x + 3, 33, WALNUT_DARK)
    pixels[start_x + 1, 31] = WALNUT_LIGHT

# The joined forearms hold a tiny gold-edged burgundy account ledger (UV 40,38).
rectangle(44, 38, 51, 41, WALNUT_DARK)
rectangle(52, 38, 59, 41, WALNUT_DARK)
rectangle(40, 42, 43, 45, BURGUNDY_DARK)
rectangle(52, 42, 55, 45, BURGUNDY_DARK)
rectangle(56, 42, 63, 45, WALNUT_DARK)
rectangle(44, 42, 51, 45, GOLD_DARK)
rectangle(45, 42, 50, 42, GOLD_LIGHT)
rectangle(45, 43, 50, 44, BURGUNDY)
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
