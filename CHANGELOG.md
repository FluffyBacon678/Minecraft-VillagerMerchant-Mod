# Changelog

Notable user-facing changes to Merchant Villager are recorded here.

## 1.1.0-rc.1 - 2026-08-09

### Added

- A global pre-approval catalogue built from safely previewable Minecraft
  villager and Wandering Trader tables. Identical exact trades are grouped with
  all known profession, level, biome-type, and pool providers; opaque custom
  mod factories appear only after a real merchant safely exposes their offer.
- Persistent Import and Export roles for face-touching chests. One logical
  chest is dual-purpose; two or more receive stable distinct roles, and a
  connected double chest still counts as one.
- Automatic waxed role signs, periodic validation, and immediate player-break
  rescans without ever selecting unrelated nearby storage.
- Bounded approved-material intake from Import into the post's Trade Storage,
  capped to a one-execution reserve and paused while cargo may need recovery.
- Exact automated-trade XP capture, persistent internal XP storage, and
  one-time XP release when the player interacts with the Merchant.
- A randomized 3–10 second villager conversation per same-target visit before
  the actual item swap, including mutual looking, ambient sounds, happy
  particles, interruption recovery, and a target-level reservation.
- Sheep-style recoloring for adult Merchants with all 16 vanilla dyes. Only
  the tailored cloth and cap are tinted; permanent apron, shirt, satchel,
  ledger, metal, and emerald details retain their authored colors. Colors save
  with the villager and synchronize to clients; Creative and same-color uses
  do not consume dye.

### Improved

- The Merchant's base outfit has cleaner cap shading, split lapels, cream
  sleeves, fitted apron pockets, coat tails, cuffs, satchel, clasp, chain, and
  ledger details. Its original burgundy cloth is now an isolated tint layer,
  allowing dye colors without recoloring any permanent material.
- Merchant Cargo is now a first-class part of the GUI; completed results have
  a gold outline and remain there until Export accepts them. Its nine slots are
  now server-authoritative and take-only, allowing players to retrieve carried
  inputs or earned rewards before delivery.
- Optional Mod Menu integration provides translated listing metadata, the
  project icon, source/issues links, and Modrinth update checks without making
  Mod Menu a required dependency.
- Chest detection is structural and no longer incorrectly asks pathfinding to
  reach the solid chest block.
- The GUI distinguishes globally approved-but-absent trades as `Away`, shows
  aggregated providers, stored XP, and the assigned Import/Export status.
- Large catalogues use bounded baseline chunks followed by compact keyed row
  deltas and entry-free worker telemetry instead of repeated full snapshots.
- Legacy entity-specific approvals migrate to the equivalent global trade when
  that live offer is next observed.

### Reliability

- XP is intercepted only for the synchronous automated trade; normal player
  trading retains vanilla XP orbs.
- Stored XP survives reloads and is safely released on death, lightning
  conversion, or zombification.
- Export removal/fullness preserves cargo and never falls back to player
  storage outside the six directly touching blocks.
- Destroying a post can no longer orphan an owned role sign when the sign's
  chunk is unavailable; exact cleanup work persists at world scope until that
  chunk is loaded.
- Worker telemetry is packet-budgeted. Exceptionally component-heavy cargo is
  summarized only in the read-only GUI preview; authoritative server cargo is
  unchanged.

## 1.0.0 - 2026-08-02

Initial release for Fabric on Minecraft 1.21.11.

### Added

- The craftable Merchant's Post and assignable Merchant Villager profession.
- A catalogue of real offers from loaded villagers and Wandering Traders, with
  offers disabled by default and explicit per-offer controls.
- Search, filtering, sorting, paging, material-deposit shortcuts, and a
  server-controlled Disable All action in a compact, Minecraft-style post
  screen that remains usable at GUI scale 3.
- A 27-slot post inventory, nine visible in-transit cargo slots, and physical
  travel to the merchant that owns each selected offer.
- Reward delivery only to a face-touching chest, including a connected double
  chest when either half touches the post.
- Batching of compatible offers at the same target, target reservations,
  distance limits, failure reporting, and conservative cargo recovery after an
  unload or restart.
- Operator commands for inspecting, refreshing, pausing, resuming, and
  releasing a Merchant's Post.
- An original burgundy-and-walnut Merchant profession outfit and matching
  project icon.
- Automated real-client coverage for non-origin post opening, catalogue-session
  reset, compact layout, live trades, inventory sync, and in-game artwork.

### Reliability

- Zombified Merchants use the normal zombie-villager appearance while keeping
  conservative cargo recovery behavior.
- Lightning conversion, profession or job-site loss, changed job sites,
  unreachable destroyed posts, and blocked line of sight now settle or pause
  work without duplicating items, losing cargo, or leaving stale assignments.
