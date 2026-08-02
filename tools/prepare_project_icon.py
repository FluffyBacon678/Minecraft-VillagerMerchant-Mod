"""Prepare the generated project icon for Fabric and release-page use."""

from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art/merchant-villager-project-icon-source.png"
RELEASE_ICON = ROOT / "art/merchant-villager-project-icon.png"
FABRIC_ICON = ROOT / "src/main/resources/assets/merchant_villager/icon.png"
MODRINTH_ICON_SIZE = (256, 256)
MODRINTH_MAX_BYTES = 256 * 1024


image = Image.open(SOURCE).convert("RGBA")
width, height = image.size
pixels = image.load()


def is_checkerboard_background(x: int, y: int) -> bool:
    red, green, blue, _alpha = pixels[x, y]
    return min(red, green, blue) >= 205 and max(red, green, blue) - min(red, green, blue) <= 22


queue: deque[tuple[int, int]] = deque()
visited: set[tuple[int, int]] = set()
for x in range(width):
    queue.append((x, 0))
    queue.append((x, height - 1))
for y in range(height):
    queue.append((0, y))
    queue.append((width - 1, y))

while queue:
    x, y = queue.popleft()
    if (x, y) in visited or not is_checkerboard_background(x, y):
        continue
    visited.add((x, y))
    pixels[x, y] = (0, 0, 0, 0)
    if x > 0:
        queue.append((x - 1, y))
    if x + 1 < width:
        queue.append((x + 1, y))
    if y > 0:
        queue.append((x, y - 1))
    if y + 1 < height:
        queue.append((x, y + 1))

RELEASE_ICON.parent.mkdir(parents=True, exist_ok=True)
release_image = image.resize(MODRINTH_ICON_SIZE, Image.Resampling.LANCZOS).quantize(
    colors=256,
    method=Image.Quantize.FASTOCTREE,
    dither=Image.Dither.FLOYDSTEINBERG,
)
release_image.save(RELEASE_ICON, optimize=True, compress_level=9)
release_bytes = RELEASE_ICON.stat().st_size
if release_bytes > MODRINTH_MAX_BYTES:
    raise RuntimeError(
        f"Modrinth icon is {release_bytes} bytes; maximum is {MODRINTH_MAX_BYTES}"
    )
FABRIC_ICON.parent.mkdir(parents=True, exist_ok=True)
image.resize((128, 128), Image.Resampling.LANCZOS).save(FABRIC_ICON, optimize=True)
print(
    f"Removed {len(visited)} connected background pixels; wrote "
    f"{RELEASE_ICON.name} ({release_bytes} bytes) and {FABRIC_ICON.name}"
)
