# Changelog

Notable user-facing changes to Merchant Villager are recorded here.

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
