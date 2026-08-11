# Modrinth release checklist

This file records the intended Modrinth metadata for the 1.1.0 release-candidate cycle. It is
not a substitute for checking the current Modrinth Content Rules before each
upload.

## Project metadata

- Project type: Mod
- Title: `Merchant Villager`
- Suggested slug: `merchant-villager`
- Summary: `Automates approved real villager and Wandering Trader offers through a physical worker and Merchant's Post.`
- License: `CC0-1.0`
- Categories: Economy, Utility, Game Mechanics
- Client-side support: Required
- Server-side support: Required
- Source: https://github.com/FluffyBacon678/Minecraft-VillagerMerchant-Mod
- Issues: https://github.com/FluffyBacon678/Minecraft-VillagerMerchant-Mod/issues

Use the English, plain-text content in `README.md` as the basis of the long
description. Keep the requirements and touching-chest storage boundary visible
on the project page.

## Version 1.1.0-rc.1

- Version number: `1.1.0-rc.1`
- Version title: `Merchant Villager 1.1.0 RC 1`
- Release channel: Beta
- Loader: Fabric
- Game version: Minecraft 1.21.11 only
- Environment: Client and server
- Changelog: use the 1.1.0-rc.1 section of `CHANGELOG.md`

### Dependency

Declare Fabric API as a required dependency:

- Project: `Fabric API`
- Modrinth project ID: `P7dR8mSH`
- Dependency type: Required

Fabric Loader is represented by the version's Fabric loader field. State the
Java 21 requirement in the description.

Mod Menu is optional and must not be declared as required. When installed, the
mod provides translated name/summary/description metadata and uses Mod Menu's
Modrinth update checker; Merchant Villager has no separate global settings
screen because its player controls live on each Merchant's Post.

### Files

- Primary: `build/libs/merchant-villager-1.1.0-rc.1.jar`
- Optional additional file: `build/libs/merchant-villager-1.1.0-rc.1-sources.jar`
  with file type `Sources JAR`

Do not upload the reference texture ZIP files or development/run directories.

## Media and rights

- Use a square, project-relevant icon that the project author has the right to
  redistribute. The prepared upload is
  `art/merchant-villager-project-thumbnail-v2.png` (256 x 256 PNG, verified at
  no more than Modrinth's 256 KiB upload limit). Its full-resolution generated
  source is `art/merchant-villager-project-thumbnail-source-v2.png`; the first
  icon and its source remain available as non-destructive alternatives.
- Gallery candidates are `art/screenshots/merchant-post-gui.png` (title:
  `Merchant's Post trade control`) and
  `art/screenshots/merchant-villager-outfit.png` (title:
  `The physical Merchant worker`). Add a third delivery screenshot when one is
  available.
- Give every gallery image a descriptive title.
- Before submission, confirm that every bundled mod-authored texture and icon
  is original or covered by permission that allows redistribution under the
  project's declared license.
- Confirm the in-game gallery captures may be used for project documentation
  and uploaded under the applicable Minecraft terms and usage guidelines; do
  not describe the Minecraft imagery inside them as CC0.

## Final checks

- Confirm the primary JAR embeds version `1.1.0-rc.1` in `fabric.mod.json`.
- Confirm Fabric API appears in the Modrinth version dependency list as
  required.
- Confirm both client-side and server-side support are marked required.
- Confirm the source and issue links are public and relevant.
- Confirm the project title contains only the project name and the summary does
  not repeat it.
- Confirm the description remains readable without relying on images.
- Check https://modrinth.com/legal/rules immediately before submission.
