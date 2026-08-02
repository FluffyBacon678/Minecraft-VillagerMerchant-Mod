# Merchant Villager

Merchant Villager is a Fabric 1.21.11 mod in which one physical Merchant
Villager operates one Merchant's Post. The post catalogues real, loaded
villager and Wandering Trader offers; every offer starts disabled (`X`) until a
player explicitly enables it (`V`).

## Gameplay

Craft a Merchant's Post from a barrel, bell, and emerald, place it near an
unemployed adult villager, and place a chest directly against any face of the
post. Open the post (or interact with its assigned Merchant) to approve offers
and insert matching trade materials into the shared 27-slot inventory.

The catalogue supports text search, readiness/permission/stock/profession
filters, multiple sort orders, click-anywhere trade toggles, clear gray disabled
rows, and a server-authoritative Disable All action. The right side presents
the post's 27-slot Merchant Backpack, the player's inventory, live worker
details, and all nine read-only in-transit cargo slots. Ghost inputs provide
convenient deposits: left-click
moves the matching cursor stack, right-click moves one, and shift-click moves
all matching stacks from the player inventory.

The Merchant removes an exact, server-owned batch into its nine cargo slots,
walks to the real target, executes the real `TradeOffer` at interaction
distance, returns to the post area, and deposits rewards into that touching
chest. A connected double chest is supported when either half touches the post;
unrelated nearby storage is ignored.
Several approved offers at one target can share the same physical visit.
Hover the live status line for worker health, distance, cargo, work-order
progress, current target, output chest, and last-failure details.

Targets through 55 blocks are normal. Targets farther than 55 and no farther
than 66 blocks must make measurable progress toward the post during a 20-tick
observation window. The Merchant never intentionally exceeds the 66-block
tether.

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

## Development

Use Java 21:

```text
gradlew build
gradlew test
gradlew runGametest
gradlew runDatagen
gradlew runServer
gradlew runClient
```

Every push and pull request also runs the build, unit tests, and Fabric
GameTests through GitHub Actions.

See [ARCHITECTURE.md](ARCHITECTURE.md) for persistence, networking, AI, cargo,
reservation, and recovery design.
