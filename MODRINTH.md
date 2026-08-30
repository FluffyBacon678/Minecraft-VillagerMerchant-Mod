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

Mention the sheep-style Merchant clothing feature in the long description:
all 16 vanilla dyes recolor only the cloth, the choice persists, Survival uses
one dye, and Creative/same-color interactions do not consume it.

### Files

- Primary: `build/libs/merchant-villager-1.1.0-rc.1.jar`
- Optional additional file: `build/libs/merchant-villager-1.1.0-rc.1-sources.jar`
  with file type `Sources JAR`

Do not upload the reference texture ZIP files or development/run directories.

## Media and rights

- Do **not** upload `art/merchant-villager-project-thumbnail-v2.png`, either
  generated source image, or the derived Fabric icon to the Modrinth project
  page. Modrinth's August 13, 2026 rules prohibit AI-generated or AI-derived
  images in project icons, gallery entries, descriptions, and changelogs.
- A new project icon made directly by the human project author is required
  before submission. Record its source and authorship here before uploading it.
- `art/screenshots/merchant-post-gui.png` is a deterministic in-game capture of
  the real GUI and does not contain the AI-derived Merchant clothing. It is the
  safest current gallery candidate (title: `Merchant's Post trade control`).
- Do not upload `art/screenshots/merchant-villager-outfit.png` or other images
  containing the AI-derived clothing artwork under the current image rule.
- Give every gallery image a descriptive title.
- Before submission, confirm that every bundled mod-authored texture and icon
  is original or covered by permission that allows redistribution under the
  project's declared license.
- Confirm the in-game gallery captures may be used for project documentation
  and uploaded under the applicable Minecraft terms and usage guidelines; do
  not describe the Minecraft imagery inside them as CC0.

## AI disclosure

The project used substantial generative-AI assistance for code, artwork,
documentation, testing, and publication preparation. Select Modrinth's
`Contains AI-generated content` disclosure and describe that assistance
honestly. Public submission is only appropriate if the human author made the
primary and significant creative contribution required by Modrinth's current
rules; this is an author judgment that automated checks cannot make.

## Final checks

- Confirm the primary JAR embeds version `1.1.0-rc.1` in `fabric.mod.json`.
- Confirm Fabric API appears in the Modrinth version dependency list as
  required.
- Confirm both client-side and server-side support are marked required.
- Confirm the source and issue links are public and relevant.
- Confirm the project title contains only the project name and the summary does
  not repeat it.
- Confirm the description remains readable without relying on images.
- Confirm a human-authored, non-AI-generated project icon is being used.
- Confirm `Contains AI-generated content` is selected and accurately described.
- Check https://modrinth.com/legal/rules immediately before submission.
