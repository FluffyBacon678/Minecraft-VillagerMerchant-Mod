# Release readiness

## Current candidate

`1.1.1-dev.8` includes a directional, barrel-style Merchant's Post, retained
menu polish, and corrected overlapping two-input trade allocation.
The `v1.1.0` tag remains historical evidence; it does not establish that every manual
smoke scenario passed. See `SMOKE_TEST.md` for individual recorded results.

## Current verification: 1.1.1-dev.8 (2026-10-01)

- `build runClientGameTest` passed with Java 21: 62 Java tests, all 72 required
  server GameTests, client rendering/menu suite, and production-JAR verifier.
- Post placement faces the player; vanilla rotation and mirroring pass.
  Rotating a populated Post retains the same block entity, seven stored paper,
  and both chest roles. Comparator output is verified at empty/partial/full.
- Real production item model rendered beside the vanilla barrel, visually
  reviewed and saved in `art/screenshots/merchant-post-block-polish.png`.
  Full-cube collision and face-touching storage rules remain unchanged.
- Artifact: `build/libs/merchant-villager-1.1.1-dev.8.jar`.
- SHA-256: `84D939AF410D14E53371A343DD10CC177C9ABE379EEFCA0D7230BD0EF062F18E`.
- No Bongo or test-harness entries in the production JAR. GitHub CI now uploads
  the verified installable JAR separately from test evidence and source JARs.
- Critique: the vanilla silhouette is cohesive, but the emblem is deliberately
  subtle. Worldless item rendering does not replace hands-on world acceptance;
  multiplayer, named-modpack compatibility, and manual acceptance below remain
  publication gates. This is a development candidate, not a published stable release.

## Previous verification: 1.1.1-dev.7 (2026-10-01)

- Final `build runClientGameTest` passed with Java 21: 62 Java tests,
  69 required server GameTests, client menu suite, and production-JAR verifier.
- Three new server regressions cover overlapping-input affordability and
  exact batch reservation, component-sensitive planning and execution, and
  atomic underpayment failure with no material loss or reward creation.
- Restoring the old cargo consumption order reproduced the named-input failure;
  the corrected order passes. Failure evidence is retained locally at
  `build/reports/merchant-regressions/overlapping-inputs-before-fix.log`.
- Both conversion fixtures retain exact final counts: three exported emeralds
  in one direction; one clock, compass, and name tag for nine emeralds in the
  other. Neither leaves inputs or cargo behind.
- Four additional edge fixtures cover contained waterlogged storage, submerged
  material conservation followed by dry-route recovery, 48 providers/192 duplicate
  live offers with provider churn, and custom component-sensitive trades with
  unstackable rewards and replay rejection. Wet-route failure preserves materials;
  the drained fixture finishes both trades and exports exactly two emeralds.
- These are bounded correctness tests, not a world-scale performance benchmark
  or verification of named third-party mods. Drowning is disabled in the water
  fixture to isolate navigation/recovery; manual death recovery remains separate.
- Artifact: `build/libs/merchant-villager-1.1.1-dev.7.jar`.
- SHA-256: `9BCA40207FA29BD02EFB83C19CD124E964AA9D79BBAB29D0C8152A2A1212AAFD`.
- No test harness or Bongo entries in the production artifact. Local only:
  not installed, committed, pushed, or published. Manual acceptance stays pending.

## Previous verification: 1.1.1-dev.6 (2026-10-01)

- Final `build runClientGameTest` passed with Java 21.
- Java tests: 62 passed, zero failures/errors/skips.
- Server GameTests: all 62 required tests passed. Resource conversion exported
  exactly three emeralds with zero remaining inputs/cargo; reverse conversion
  spent nine emeralds and exported one clock, compass, and name tag, with empty cargo.
- Client regression reproduced a real bug before the fix: adding Armorer while
  Librarian was selected silently switched the filter to Farmer. The corrected
  test passes insertion, removal of another profession, and fallback to All when
  the selected profession disappears. Existing cache/network/resize checks pass.
- Real client captures at GUI scales 3 and 2 were visually reviewed, including
  an empty search, the normal menu, and a focused paper search after resizing.
  New evidence: `art/screenshots/merchant-post-empty-search.png` and
  `art/screenshots/merchant-post-search-scale-2.png`.
- Production JAR verifier passed; zero Bongo entries remain in the artifact.
- Artifact: `build/libs/merchant-villager-1.1.1-dev.6.jar`.
- SHA-256: `DC56EEB8D5A67804B9D02ECDC0F96A6A8FC2B3CFDF3FF673B271766200829943`.
- Local only: not installed, committed, pushed, or published. These automated
  client captures are worldless and do not complete manual Prism acceptance.

## Previous verification: 1.1.1-dev.2 (2026-09-30)

- `build runClientGameTest` passed with Java 21.
- Java tests: 62 passed, zero failures, errors, or skips. Five new regressions
  cover independent action/player cooldowns, unauthorized requests, interval
  boundaries, and a rewound clock.
- Server GameTests: all 62 required tests passed; both conversion fixtures
  finished with exact Export contents and no remaining inputs or cargo.
- Client screen test: passed, including cached-view reuse for telemetry,
  normalized search, same-revision replacement rows, filters, and sorting.
- Production-JAR verification: passed; embedded version is `1.1.1-dev.2`.
- GUI screenshot: byte-for-byte identical to the tracked menu capture.
- Artifact: `build/libs/merchant-villager-1.1.1-dev.2.jar`.
- SHA-256: `79640325A4501887C1148514619B587F95D338546A00F54EB543B71EC4A34DF2`.

## Previous verification: 1.1.1-dev.1 (2026-09-05)

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
