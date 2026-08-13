# Merchant Villager 1.1.0-rc.1 smoke test

Target date: 2026-08-11
Target instance: `MerchantVillager-RC-Smoke` / display name `Merchant Villager RC Smoke`
Runtime: Minecraft 1.21.11, Fabric Loader 0.19.3, Fabric API 0.141.6, Java 21

The earlier target instance was no longer present at installation time. The RC
was therefore installed into the isolated replacement
`MerchantVillager-RC-Smoke` / display name `Merchant Villager RC Smoke`; the
NeoForge 1.21.1 `create mod` instance and its existing world were not modified.

Legend: **PASS**, **FAIL**, **PENDING**, **BLOCKED**. Automated rows may be
marked PASS by the build. Manual rows stay PENDING until they are observed in
the installed Prism instance.

## Automated release checks

| ID | Scenario | Result | Evidence / notes |
|---|---|---|---|
| A01 | Java/unit tests | **PASS** | 57/57, zero failures/errors/skips; exact 64x64 profession UV mask, permanent/tint-layer separation, and five-level cloth mask covered |
| A02 | Full server gameplay suite | **PASS** | 57/57 required GameTests, including Merchant dye interaction and save/reload persistence |
| A03 | Repeat server suite for randomized travel/social timing | **PASS** | 57/57 repeat after dye integration; isolated stationary-boundary fixture also passed 3/3 after test-signature isolation |
| A04 | Real Merchant Post client-screen GameTest | **PASS** | Real renderer/network/cache/UI run succeeded; screenshot hash matches tracked release image |
| A05 | Production build and release-jar verifier | **PASS** | Clean `build` succeeded; release verifier succeeded; no GameTest entrypoint/classes in production jar |
| A06 | Artifact metadata, contents, and SHA-256 | **PASS** | Version/dependencies/Java 21/main+client entrypoints/12 required mixins and both clothing texture layers verified |
| A07 | Duplicate recipe approval regression | **PASS** | Component/count-identical visible recipes share one approval even when stock, merchant XP, price multiplier, or player-XP bookkeeping differs; legacy approval keys migrate |
| A08 | Visible reward and chest-marker regression | **PASS** | Rewards remain in Merchant Cargo for 2 seconds, delivery receipt remains visible for 5 seconds, and generated role signs occupy the block directly on top of their chest |
| A09 | Interactive Merchant Cargo | **PASS** | Cargo is nine real server-authoritative take-only slots; exact partial input and full reward withdrawals clear the correct physical stacks/flags without duplication; real client screen render passed |
| A10 | Dyeable Merchant clothing | **PASS** | Survival consumes exactly one dye; Creative and same-color interactions consume none; babies and ordinary villagers ignore the feature; selected color survives entity NBT; only the isolated cloth UV mask is tinted |

Final RC artifact: `merchant-villager-1.1.0-rc.1.jar`
SHA-256: `47AFD139C5C0319F018D5BC50140CB3436897649EB7293CAEB1DAF3F3568DD1E`

## Manual Prism smoke scenarios

| ID | Scenario | Result | What to verify / evidence |
|---|---|---|---|
| M01 | Launch to title screen | **PASS** | Fresh Prism instance reached the client title screen with MC 1.21.11, Loader 0.19.3, Fabric API 0.141.6, Java 21, and Merchant Villager 1.1.0-rc.1; no loader/mixin/entrypoint errors in `latest.log` |
| M02 | Fresh-world basic setup | **PASS** | `Merchant RC Smoke`: Post placed beside a generated plains village; an adult villager claimed Merchant, the production screen opened, and a replacement Merchant reassigned after the first worker died before the enclosure was secured |
| M03 | One touching chest | PENDING | One face-touching logical chest becomes Dual; diagonal, gapped, and nearby storage is ignored |
| M04 | Two touching chests | **PASS** | Two face-touching single chests at `-331 73 1136` and `-329 73 1136` were assigned stable Import/Export roles. Updated RC places generated `[Import]` / `[Export]` standing signs directly on top of the assigned chests; visual recheck pending |
| M05 | Marker destruction | PENDING | Generated sign drops no free sign item and is safely recreated or reassigned |
| M06 | Global preapproval | PENDING | **Manual partial:** three catalogue rows were enabled in the real UI and matched against 11 nearby targets / 24–33 live offers. Approving before the matching provider is spawned still needs a deliberately ordered manual pass |
| M07 | Bounded Import intake | **PASS** | With 256 paper loaded, only 48 moved during the observed reserve cycle while 208 remained in Import; the prior completed batch retained an 8-paper internal reserve |
| M08 | Physical trade loop | **PASS** | First authoritative batch: 128 paper became an 8-paper reserve plus exactly 5 Export emeralds with empty cargo. A second batch raised Export from 5 to 24 emeralds. The Merchant was also captured facing/talking to a target during the social phase |
| M09 | Blocked or missing Export | PENDING | Reward remains in Merchant Cargo; no arbitrary nearby/player chest is used; resumes when Export is valid |
| M10 | Stored trade XP | PENDING | **Manual partial:** no XP orbs existed within 32 blocks and internal XP rose from 88 to 143 while player XP was reset to 0. The one-time interaction release remains automated-only (covered by A02), because crowded-entity raw-input targeting could not isolate the Merchant reliably |
| M11 | Save/reload recovery | PENDING | **Manual partial:** graceful close logged all dimensions saved; quick-play reload returned to the same Post, assigned Merchant, two chests, marker, villagers, and enclosure without startup errors. Approval/stored-XP/cargo values were not all re-read after reload because LAN command permission was not restored |
| M12 | Post/marker lifecycle | PENDING | Break a post near a chunk edge, reload/visit the marker chunk, and confirm no owned orphan sign remains |
| M13 | Wandering Trader | PENDING | Approved Wandering Trader offer participates in the same physical loop |
| M14 | GUI polish at scale 3 | PENDING | Search, filters, sort, paging, row toggles, Disable All, tooltips, inventory slots, XP, and chest status are readable |
| M15 | Instance compatibility | PENDING | ChromaCables and Merchant Villager coexist; exactly one active Merchant Villager jar is installed |
| M16 | Unified duplicate rows | PENDING | Identical visible paper-to-emerald recipes from multiple professions/providers appear once; one toggle authorizes every matching live offer |
| M17 | Visible reward handoff | PENDING | During the 2-second review window emeralds appear in Merchant Cargo, then move to Export; status shows `Delivered ... to Export chest` for 5 seconds |
| M18 | Early cargo withdrawal | PENDING | Take or shift-click paper/other reserved inputs and earned emeralds from Merchant Cargo; withdrawn rewards must not later appear again in Export |
| M19 | Merchant clothing dyes | **PASS** | Blue in Survival consumed exactly one dye; applying blue again consumed none and opened the normal Merchant UI; red and lime in Creative consumed none; only cap/coat cloth changed; lime survived a full save/quit/reload |

## Known RC notes

- Building the safe vanilla/Wandering catalogue can briefly make the test
  server report roughly two seconds behind during startup/reload.
- Opaque custom mod trade factories are not executed speculatively. Their exact
  offers appear after a real merchant exposes them.
- Randomized vanilla factories are sampled four ways. An unseen live exact
  variant appears safely as a separate disabled row when observed.
- The 3–10 second social phase runs once per same-target visit/batch, not once
  for every individual use in that batch.

## Manual result notes

Record the world name, time, screenshots, log excerpts, and reproduction steps
for every FAIL. Do not publish the RC until M01–M10 and M14–M15 pass; M11–M13
are release-candidate persistence/integration gates and should pass before the
final 1.1.0 release.

For the first hands-on pass, create a Creative superflat world with cheats,
place one Merchant's Post and one face-touching chest, then spawn two adult
villagers. Let one claim the Post; give the other a normal workstation such as
a composter and let it acquire offers. Open the Post, approve one affordable
row, and put its exact input into Import/Trade Storage. This single setup covers
M02, M03, M06, M07, M08, and M10 before expanding to two chests and failure
cases.

### 2026-08-11 unattended village smoke

- World: `Merchant RC Smoke`, generated plains village near `-336, 1136`.
- Setup: Merchant Post `-330 73 1136`; Import `-331 73 1136`; Export
  `-329 73 1136`; glass safety enclosure; 11 visible trade targets plus iron
  golems and village buildings.
- First measured loop: Import empty, Trade Storage `8 paper`, Export
  `5 emeralds`, Merchant Cargo empty, worker `IDLE`.
- Refilled/reset-offer loop: Import retained `208 paper`; Export rose to
  `24 emeralds`; no XP orb entity was present.
- Internal trade XP was observed at `88`, then `143`, while the player was
  baselined to `0` total XP.
- Graceful window close produced `Saving players`, `Saving worlds`, and
  `All dimensions are saved`; the same setup visibly survived quick-play
  reload.
- Screenshots: `art/screenshots/smoke-village-two-chests.png`,
  `smoke-social-interaction.png`, `smoke-global-catalogue-xp.png`, and
  `smoke-after-reload.png`.

### 2026-08-11 post-smoke correction build

- The manual screenshot showed two visually identical 24-paper-to-emerald
  rows. Recipe approval now ignores invisible per-offer stock/XP bookkeeping,
  while still treating different counts or item components as distinct.
- Emerald rewards are held visibly in Merchant Cargo for 40 ticks before the
  worker returns, and the completed Export receipt is held for 100 ticks.
- Role markers no longer choose a freestanding floor position; their only
  valid generated position is directly above the assigned chest.
- Validation after these corrections: clean build PASS, server GameTests
  55/55 PASS twice across the completed follow-up (including the final build), real client GameTest PASS, and
  release-jar verification PASS.
- Updated artifact installed into `MerchantVillager-RC-Smoke`; exactly one
  active Merchant Villager jar is present and its installed SHA-256 matches
  the build artifact: `973D21A38A2AAE70B93401F2402EA6990D771A86E410D68C76632CB98A12EA4F`.
- Installed-instance launch PASS: Fabric loaded Merchant Villager
  `1.1.0-rc.1`, initialized its client entrypoint and all texture atlases, and
  remained responsive with no error, fatal, mixin-failure, or entrypoint-failure
  lines in `latest.log`.

### 2026-08-11 interactive cargo follow-up

- Merchant Cargo is now the assigned villager's live physical nine-slot cargo,
  synchronized through the normal Minecraft screen handler rather than a
  read-only telemetry drawing.
- Players may click, split, or shift-click inputs and earned rewards out early.
  Cargo remains take-only, so items cannot be inserted into the courier slots.
- Removing required inputs during an active job fails closed: no reward is
  created, and any remaining physical inputs follow the existing recovery path.
- Validation: 55/55 server GameTests PASS, real client GUI GameTest PASS, unit
  suite/release verifier/build PASS. Updated installed artifact SHA-256:
  `DAC54B33F472A7E7E994551C308F9921DF5F84E7694EB3E77A78EDAF1F161CE4`.

### 2026-08-12 base clothing polish

- Refined the Merchant's burgundy cap and coat, split cream lapels and sleeves,
  fitted walnut apron with two pockets, coat tails, cuffs, gold trim, emerald
  clasp, satchel, chain, and account ledger.
- The five-shade burgundy cloth ramp is now isolated from every permanent
  material, ready for a later clothing-only dye system. This build does not
  yet add dye interaction, saved colors, or alternate rendered palettes.
- Exact vanilla UV-mask validation passed with 1,888 expected opaque pixels and
  no pixels leaking onto skin or unused model regions. Unit tests 57/57, server
  GameTests 55/55, release build, and real client GameTest all pass.
- Updated artifact SHA-256:
  `200F24069E0BE8055AFD9C3F58B3B8C216B8C54C35532182D0A670F6022022F4`.

### 2026-08-12 dyeable clothing follow-up

- Split the polished profession texture into a permanent material layer and a
  neutral five-level cloth mask. The original undyed burgundy remains the
  default appearance; all 16 vanilla dye colors tint only the cap and coat.
- Adult Merchants accept dye with sheep-like interaction. Survival consumes
  exactly one item, Creative consumes none, and applying the current color is
  a no-op that preserves the normal Merchant interaction. Babies and ordinary
  villagers are unaffected.
- The color uses a persistent, client-synchronized Fabric entity attachment.
  The server regression performs a real entity-NBT round trip, and the client
  applies it through a dedicated Villager render-state mixin and feature layer.
- Visual UV-sheet review confirmed every dye leaves leather, apron, shirt,
  satchel, ledger, chain, gold, and emerald pixels unchanged. Automated
  validation: unit tests 57/57, server GameTests 57/57, clean release build,
  release-jar verifier, and real client GameTest all pass.
- Installed the rebuilt RC into the isolated `MerchantVillager-RC-Smoke`
  instance with exactly one Merchant Villager jar. Installed SHA-256 matches
  the build: `47AFD139C5C0319F018D5BC50140CB3436897649EB7293CAEB1DAF3F3568DD1E`.
  The Prism client reached a responsive Minecraft 1.21.11 window with Fabric
  API, Mod Menu, Merchant Villager, the client mixin, and both texture layers;
  `latest.log` contains no ERROR, FATAL, exception, mixin-failure, or
  entrypoint-failure lines.

### 2026-08-13 hands-on dye smoke

- Reused the isolated `Merchant RC Smoke` village world and the exact installed
  `1.1.0-rc.1` artifact with SHA-256
  `47AFD139C5C0319F018D5BC50140CB3436897649EB7293CAEB1DAF3F3568DD1E`.
- In Survival, applying blue dye changed only the Merchant's cap and coat and
  reduced a stack of two dyes to one. Applying blue again kept the final dye
  and opened the ordinary Merchant's Post UI, confirming same-color fallback.
- In Creative, red and lime each recolored the cap and coat while their stacks
  remained at two. The cream shirt and sleeves, walnut apron and ledger,
  leather, gold trim, and emerald clasp remained visually unchanged.
- Lime persisted through a full Save and Quit to Title followed by a fresh
  world load. The temporary `NoAI` camera hold was removed afterward, the
  world saved cleanly again, and the client reached `Stopping!` with no ERROR,
  FATAL, exception, mixin-failure, or entrypoint-failure lines.
- Evidence: `art/screenshots/smoke-merchant-blue-dye.png`,
  `smoke-merchant-red-dye.png`, `smoke-merchant-lime-dye.png`, and
  `smoke-merchant-lime-after-reload.png`.
