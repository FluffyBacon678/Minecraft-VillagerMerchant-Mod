# Merchant Villager architecture

Merchant Villager is server-authoritative. A `MerchantPostBlockEntity` owns the
27-slot Trade Storage, global trade approvals, live-offer snapshots, persistent
Import/Export assignments and markers, worker assignment, fairness cursor, and
unreachable-offer cooldowns. The villager owns its persistent cargo, stored XP,
and active work queue. A target-independent catalogue key controls approval;
the UUID/slot fingerprint remains separate for stale-offer validation and
in-flight work.

The worker remains a vanilla `VillagerEntity` with the registered Merchant
profession and Merchant's Post POI. A small mixin supplies persistent cargo and
work state to the existing entity and calls `MerchantController` after the
vanilla Brain tick. Vanilla panic, raid, sleep, and conversion behavior is not
replaced. The controller is an explicit state machine and only starts new work
during work activity; recovery and delivery may continue later.

The physical loop is:

1. Build a server catalogue from audited, safely previewable Minecraft
   profession and Wandering Trader factories, then scan loaded targets at a
   40-tick cadence. Opaque mod factories are indexed only from real live offers.
2. Select only globally enabled, current, fundable offers whose assigned
   face-touching Export can accept the result.
3. Atomically move planned inputs from the post into nine persistent cargo
   slots and reserve every selected offer at that target.
4. Navigate the real worker to the real target.
5. At interaction distance, reserve the whole target, run the 3–10 second
   social phase, revalidate, and execute repeated uses and multiple
   approved offers at the same target, with a bounded per-tick batch.
6. Return within the post area and insert rewards directly into the assigned
   Export. Unused inputs go back only to Trade Storage.

Targets at 55 blocks or less are normal. Targets in `(55, 66]` are observed for
20 ticks and accepted only after meaningful distance reduction and a positive
movement projection toward the post. Convergence is rechecked; 40 ticks without
progress, a target outside 66 blocks, or a worker path that would break the
66-block tether aborts into cargo recovery.

Client intent networking carries only a post position, a global catalogue key,
and tightly bounded click data. The server requires the matching open handler,
same dimension, interaction distance, current block entity, current offer
identity, and valid bounds before changing permissions or moving an item. The
server sends a complete catalogue baseline in chunks of at most 64 rows and
96 KiB of encoded row data, then sends revision-checked keyed row deltas only
for changed state or display offers. Worker telemetry is sent once in a
separate packet, never repeated in catalogue chunks. Its cargo preview is
bounded to nine slots and 48 KiB; oversized component data is stripped only
from the read-only client preview and reported as summarized, while the
server's real cargo remains exact. Every payload has a final 128 KiB guard.
The client never creates or consumes trade stacks.

`ReservationManager` is per-server, target-exclusive, and expiring. Removed post items are the
only material source of truth; reservations never contain duplicate stacks.
Worker cargo drops once on death and otherwise persists through ordinary
unloads. If a post is destroyed, existing post contents drop normally and the
worker retains its distinct cargo for bounded recovery at the former post.
Generated role signs that cannot be reached during post destruction are handed
to a per-dimension persistent cleanup ledger. The ledger never force-loads a
chunk and removes a sign only after its exact post, chest, and role ownership
token can be verified on a later chunk load or bounded world tick.
Every non-empty cargo load re-arms the one-time drop guard, so a later job
cannot inherit the completed recovery marker from an earlier job. After entity
deserialization, in-flight cargo enters conservative recovery; the controller
never blindly repeats a saved trade execution, and stale reservation leases
are discarded.

The controller yields completely to vanilla panic, raid, hiding, and sleep
activities. It resumes the same persisted state afterward. A target occupied
by a real player is never taken over; the worker waits for a bounded period
with its cargo intact.

The management screen receives the bounded baseline, compact row deltas, and
live worker telemetry. Its two-column layout keeps the scrollable trade
catalogue on the left and the 27-slot Trade Storage, read-only nine-slot
Merchant Cargo, and player inventory on the right. Search, filters, sorting,
Disable All, row toggles, refresh, and ghost-input deposits only issue small
intent packets. The server revalidates the open handler, post position,
interaction distance, catalogue key, selected input, item components,
inventory capacity, and all counts before changing state.

Eleven narrowly scoped mixins are used. `VillagerEntityMixin` persists worker
state, invokes the controller after the vanilla Brain tick, handles normal
death cargo, and captures only automated-trade XP spawning. The Wandering
Trader mixin applies the same narrow XP capture, while `ZombieEntityMixin`
covers successful infection recovery. `MobEntityMixin` enforces the ephemeral
target-side social hold after normal AI runs. `AbstractBlockStateMixin`
suppresses loot only for owned generated role signs. Six read-only factory
markers/accessors classify deterministic, randomized, wrapped, enchanted, and
map-backed vanilla trade factories; `SellMapFactoryAccessor` also synthesizes
explorer-map previews without structure searches or world map allocation.
