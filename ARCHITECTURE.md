# Merchant Villager architecture

Merchant Villager is server-authoritative. A `MerchantPostBlockEntity` owns the
27-slot material inventory, discovered-offer snapshots, persisted V/X
permissions, worker assignment, fairness cursor, and unreachable-offer
cooldowns. The villager owns its persistent cargo and active work queue. Item stacks are
serialized with Minecraft's registry-aware codecs; offer fingerprints include
the target UUID, offer slot, both component-bearing inputs, output, and the
offer's immutable characteristics.

The worker remains a vanilla `VillagerEntity` with the registered Merchant
profession and Merchant's Post POI. A small mixin supplies persistent cargo and
work state to the existing entity and calls `MerchantController` after the
vanilla Brain tick. Vanilla panic, raid, sleep, and conversion behavior is not
replaced. The controller is an explicit state machine and only starts new work
during work activity; recovery and delivery may continue later.

The physical loop is:

1. Scan loaded target merchants at a 40-tick cadence.
2. Select only enabled, current, fundable offers and a face-touching output chest (a connected double chest is one inventory).
3. Atomically move planned inputs from the post into nine persistent cargo
   slots and reserve every selected offer at that target.
4. Navigate the real worker to the real target.
5. At interaction distance, revalidate and execute repeated uses and multiple
   approved offers at the same target, with a bounded per-tick batch.
6. Return within the post area, navigate to the face-touching chest, and insert
   rewards. Unused inputs go back only to the post.

Targets at 55 blocks or less are normal. Targets in `(55, 66]` are observed for
20 ticks and accepted only after meaningful distance reduction and a positive
movement projection toward the post. Convergence is rechecked; 40 ticks without
progress, a target outside 66 blocks, or a worker path that would break the
66-block tether aborts into cargo recovery.

Networking carries only a post position and an offer fingerprint. The server
requires the matching open handler, same dimension, interaction distance,
current block entity, current offer identity, and valid bounds before changing
permissions or moving an item. Catalogue data is synchronized from server
snapshots and the client never creates or consumes trade stacks.

`ReservationManager` is per-server and expiring. Removed post items are the
only material source of truth; reservations never contain duplicate stacks.
Worker cargo drops once on death and otherwise persists through ordinary
unloads. If a post is destroyed, existing post contents drop normally and the
worker retains its distinct cargo for bounded recovery at the former post.
Every non-empty cargo load re-arms the one-time drop guard, so a later job
cannot inherit the completed recovery marker from an earlier job. After entity
deserialization, in-flight cargo enters conservative recovery; the controller
never blindly repeats a saved trade execution, and stale reservation leases
are discarded.

The controller yields completely to vanilla panic, raid, hiding, and sleep
activities. It resumes the same persisted state afterward. A target occupied
by a real player is never taken over; the worker waits for a bounded period
with its cargo intact.

The management screen receives bounded server snapshots with live worker
telemetry. Its two-column layout keeps the scrollable trade catalogue on the
left and the 27-slot Merchant Backpack, read-only nine-slot travel cargo, and
player inventory on the right. Search, filters, sorting, Disable All, row
toggles, refresh, and ghost-input deposits only issue small intent packets. The server revalidates
the open handler, post position, interaction distance, exact offer
fingerprint, selected input, item components, inventory capacity, and all
counts before changing state.

Two narrowly scoped mixins are used. `VillagerEntityMixin` persists worker
state, invokes the controller after the vanilla Brain tick, and handles normal
death cargo. `ZombieEntityMixin` covers vanilla's successful infection path,
which discards the original villager without its normal death callback; it
drops the exact cargo once and releases the post/reservations.
