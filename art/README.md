# Artwork sources

The source/concept images in this directory were generated specifically for
Merchant Villager with OpenAI's built-in image generation tool.

`merchant-profession-concept.png` is the visual direction for the in-game
profession overlay. `tools/generate_merchant_texture.py` translates its
burgundy vest, walnut apron, emerald clasp, ledger, and satchel motifs onto the
exact vanilla-compatible 64x64 villager UV islands.

`merchant-villager-project-icon-source.png` is the untouched icon generation.
The model rendered a checkerboard as pixels, so
`tools/prepare_project_icon.py` removes only the connected outer checkerboard
and creates both the transparent release icon and the 128x128 Fabric icon.

`merchant-villager-project-thumbnail-source-v2.png` is the untouched 2026-08-11
release-thumbnail generation. The existing project icon and profession concept
were supplied only as identity/style references. The optimized 256x256 Modrinth
upload is `merchant-villager-project-thumbnail-v2.png`.

## Profession concept prompt

> Create an original Minecraft-compatible villager profession texture design
> concept: a burgundy tailored ledger vest, dark walnut leather work apron with
> utility pockets, small emerald clasp in a gold frame, fine gold ledger chain,
> and tan cross-body satchel. Present the outfit as a clean technical pixel-art
> parts sheet on a solid magenta chroma background, enlarged with crisp hard
> pixels. Do not imitate wandering-trader clothing, warehouse-apron art, online
> resource packs, or existing character textures.

## Project icon prompt

> Create an original square project icon for a Minecraft Fabric mod called
> Merchant Villager. Pixel-art aesthetic with crisp hard-edged pixels, no text,
> letters, logos, or borrowed game/company marks. Center a friendly blocky
> merchant villager bust in a low burgundy cap, tailored vest, dark walnut
> apron, gold-framed emerald clasp, and tiny ledger, with a subtle wooden
> merchant counter and emerald behind it. Use a warm parchment-gold frame and a
> strong silhouette that remains readable at small sizes.

## Project thumbnail v2 prompt

> Create a highly polished square pixel-art/voxel-game icon for the Merchant
> Villager mod, faithfully based on the supplied merchant character and
> burgundy-and-brown outfit. Show the merchant from the waist up holding a
> small trade ledger, with one emerald and a wooden Merchant's Post/chest motif
> clearly visible. Keep a centered circular brass medallion composition,
> strong small-size silhouette, warm market-stall light, and no text, letters,
> logos, watermark, UI, photorealism, blur, or tiny noisy details.

The CC0-1.0 dedication applies to the original generated source art and the
mod-authored textures and icon derived from it.

## Verified in-game captures

The PNGs in `screenshots/` are deterministic captures from
`MerchantPostClientGameTest`. They verify the compact live-trade screen and the
profession texture in a real Minecraft 1.21.11 client, rather than a mockup.
Those captures also contain Minecraft imagery owned by Microsoft/Mojang. They
are provided for project documentation and are not relicensed under CC0 by this
repository; their use remains subject to the applicable Minecraft terms and
usage guidelines.
