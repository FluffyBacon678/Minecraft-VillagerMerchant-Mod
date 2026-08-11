# Merchant Villager

Merchant Villager automates approved trades without replacing villagers with
virtual menus. One physical Merchant Villager operates one Merchant's Post,
visits the real villager or Wandering Trader that owns an offer, performs the
trade, and carries the result back.

The post safely pre-indexes Minecraft's villager profession tables and
Wandering Trader pools. Players can approve those trades before a matching
merchant arrives; identical exact trades from multiple providers share one
catalogue row and one approval. Fabric exposes arbitrary modded trades only as
executable factory code, so opaque custom factories are never run speculatively;
their exact trades appear safely after a real merchant exposes the offer.

## Requirements

- Minecraft: Java Edition 1.21.11
- Fabric Loader 0.19.3 or newer
- Fabric API 0.141.6+1.21.11 or newer
- Java 21
- The mod and Fabric API installed on the server and every joining client
- Optional: Mod Menu for translated listing metadata and update notifications

For single-player, install both Merchant Villager and Fabric API in the
instance's `mods` folder. For multiplayer, install the same compatible mod
version on the dedicated server and on each client.

## Screenshots

![Merchant's Post screen with four live trades, stored materials, and an assigned Merchant](art/screenshots/merchant-post-gui.png)

![Original burgundy-and-walnut Merchant Villager outfit beside a touching chest](art/screenshots/merchant-villager-outfit.png)

## Gameplay

Craft a Merchant's Post from a barrel, bell, and emerald, place it near an
unemployed adult villager, and place one or two chests directly against the
post. Open the post (or interact with its assigned Merchant) to approve offers
and insert matching trade materials into its 27-slot Trade Storage.

Only a chest block that shares a face with the post is accepted. Diagonal
chests, chests separated by a gap, and unrelated nearby player storage are
ignored. A connected double chest works when at least one half shares a face
with the post.

One adjacent chest is marked for both Import and Export. With two or more,
stable, distinct Import and Export roles are chosen and marked by waxed signs;
extra chests are ignored. Breaking a marker or assigned chest causes a safe
rescan. Import stages only a bounded one-execution reserve of approved inputs
in Trade Storage and pauses while worker cargo may need recovery; excess stock
stays in the chest. Completed rewards go only to Export.

The global catalogue supports text search, readiness/permission/stock/profession
filters, multiple sort orders, click-anywhere trade toggles, clear gray disabled
rows, and a server-authoritative Disable All action. The right side presents
the post's 27-slot Trade Storage, the player's inventory, live worker
details, and all nine live Merchant Cargo slots. Players may click, split, or
shift-click carried inputs and earned rewards out before delivery; Cargo is
take-only, so items cannot be inserted there. Gold cargo outlines mark
completed trade results that are waiting for Export. Ghost inputs provide
convenient deposits: left-click moves the matching cursor stack, right-click
moves one, and shift-click moves all matching stacks from the player inventory.

The server moves an exact trade batch into the Merchant's nine cargo slots.
The Merchant walks to the real target, spends a randomized 3–10 seconds facing
and conversing with it once per same-target visit, performs the visit's approved
offer uses at interaction distance, returns to the post, and deposits the
rewards into Export. Vanilla trade XP is held internally instead of littering
the ground, then released when the player interacts with the Merchant. Several
approved offers at one target can share that physical visit and conversation.
Hover the live status line for worker health, distance, cargo, work-order
progress, current target, output chest, and last-failure details.
If unusually large custom item components exceed telemetry summary limits, the
real synchronized Cargo slots still preserve and display the exact server items.

Targets through 55 blocks are normal. Targets farther than 55 and no farther
than 66 blocks must make measurable progress toward the post during a 20-tick
observation window. The Merchant never intentionally exceeds the 66-block
tether.

## Storage safety and recovery

The Merchant never searches arbitrary storage around the post. If its assigned
Export cannot accept the expected rewards, work waits with the result in
Merchant Cargo instead of selecting another nearby chest. Keep free space in
Export before enabling large batches.

On an unload or restart, serialized in-flight cargo is conservatively routed
through recovery rather than blindly reissuing an uncertain offer use. Inputs
return to the post; rewards remain reward cargo until a valid chest accepts
them.

## Operator commands

`/merchant_villager inspect [pos]`

`/merchant_villager refresh [pos]`

`/merchant_villager pause [pos]`

`/merchant_villager resume [pos]`

`/merchant_villager release [pos]`

Without `pos`, the command uses the post the operator is looking at.

## Source, issues, and license

- Source: https://github.com/FluffyBacon678/Minecraft-VillagerMerchant-Mod
- Issues: https://github.com/FluffyBacon678/Minecraft-VillagerMerchant-Mod/issues
- License: [CC0 1.0 Universal](LICENSE)

## Development

Use Java 21:

```text
gradlew build
gradlew test
gradlew runGameTest
gradlew runClientGameTest
gradlew runDatagen
gradlew runServer
gradlew runClient
```

Every push and pull request also runs the build, unit tests, server-side Fabric
GameTests, and the real non-origin Merchant Post client-screen test
through GitHub Actions. The client job uploads its GUI screenshot as an
artifact.

See [ARCHITECTURE.md](ARCHITECTURE.md) for persistence, networking, AI, cargo,
reservation, and recovery design.
