# Release readiness

## Current candidate

`1.1.1-dev.1` adds menu usability fixes after the tagged `v1.1.0` source release.
The tag remains historical evidence; it does not establish that every manual
smoke scenario passed. See `SMOKE_TEST.md` for individual recorded results.

Verification on 2026-09-05:

- Java tests: 57 passed, zero failures or errors.
- Server GameTests: 62 required tests passed. Resource conversion ended with
  three Export emeralds, zero remaining inputs, and empty cargo. The reverse
  fixture spent nine emeralds and exported one clock, compass, and name tag.
- Client screen test: passed, including retained search, keyboard focus, and
  catalogue session after resizing. The new assertion caught a focus-lifecycle
  defect during implementation; the corrected resize handling passes.
- Production JAR verifier: passed after the final client correction.
- GUI capture: `art/screenshots/merchant-post-gui.png`, visually reviewed.
- Artifact: `build/libs/merchant-villager-1.1.1-dev.1.jar`.
- SHA-256: `CFBEFCCA27C349196458B8A226D77900B8BC4920DE48D2CE86D28BB93EE5CC45`.

The client harness renders the actual screen without a live world. It does
not substitute for the manual item-click and delivery acceptance cases below.

## Final hands-on acceptance

Use an isolated Creative test world with a Merchant, a librarian, a Post,
touching Import/Export chests, and an unrelated nearby control chest. Record
the exact JAR hash, start/end item counts, screenshots, and any reproduction
steps. Do not mark a manual case passed solely because a related GameTest passes.

| Check | Acceptance | Status |
|---|---|---|
| Menu and catalogue | Search at GUI scales 2 and 3; resize with search focused; toggle one duplicate recipe and confirm every matching provider shares permission; check scrolling, filters, tooltips and item deposits | Pending |
| Visible delivery | Supply exactly 48 paper for two 24-paper offers; observe emeralds in Cargo and two in Export, with no unexplained leftovers or control-chest changes | Pending |
| Early withdrawal | Take one earned emerald from Cargo before delivery; player plus Export totals exactly two emeralds, including after save/reload | Pending |
| Storage recovery | Remove Export after trading; rewards remain recoverable; replace Export and verify delivery. Repeat with one touching double chest as Dual and break its marker | Pending |
| XP and persistence | Accumulate XP, interact once to collect, then interact again; no duplicate XP. Save/reload with cargo and approvals and compare counts | Pending |
| Wandering Trader and compatibility | Complete one approved Wandering Trader purchase; launch the production JAR with and without Mod Menu and with the intended companion mods | Pending |

## Publication gate

- Complete and record the checks above, including the final production version.
- Use the final artifact's hash; a development JAR is not the release artifact.
- Resolve project media authorship and AI disclosure using `MODRINTH.md`, and
  recheck the platform rules at actual submission time.
- Publish the final changelog, artifact, and matching source tag together.
