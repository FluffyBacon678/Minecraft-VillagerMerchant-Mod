package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure row diffing kept separate so ordinary UI updates cannot prepare a full payload. */
final class CatalogueDeltaPlanner {
    static Plan plan(
        Map<String, CataloguePayload.Entry> previous,
        Map<String, CataloguePayload.Entry> current
    ) {
        if (!previous.keySet().equals(current.keySet())) {
            return new Plan(true, List.of());
        }
        List<CatalogueDeltaPayload.RowDelta> deltas = new ArrayList<>();
        for (Map.Entry<String, CataloguePayload.Entry> row : current.entrySet()) {
            CataloguePayload.Entry old = previous.get(row.getKey());
            CataloguePayload.Entry now = row.getValue();
            boolean offerChanged = !sameDisplayedOffer(old.offer(), now.offer());
            if (offerChanged || !sameRowState(old, now)) {
                deltas.add(CatalogueDeltaPayload.RowDelta.between(
                    row.getKey(), now, offerChanged
                ));
            }
        }
        return new Plan(false, List.copyOf(deltas));
    }

    private static boolean sameDisplayedOffer(OfferSnapshot left, OfferSnapshot right) {
        // Cost/result identity is immutable for an exact catalogue key. A
        // changed live representative is identified by UUID/index; all mutable
        // economics and availability fields are compared here or in row state.
        return left.targetUuid().equals(right.targetUuid())
            && left.targetName().equals(right.targetName())
            && left.profession().equals(right.profession())
            && left.villagerLevel() == right.villagerLevel()
            && left.offerIndex() == right.offerIndex()
            && left.uses() == right.uses()
            && left.maxUses() == right.maxUses()
            && Double.compare(left.distanceSquared(), right.distanceSquared()) == 0
            && left.wanderingTrader() == right.wanderingTrader()
            && left.targetAvailable() == right.targetAvailable()
            && left.despawnDelay() == right.despawnDelay()
            && left.fingerprint().equals(right.fingerprint());
    }

    private static boolean sameRowState(
        CataloguePayload.Entry left, CataloguePayload.Entry right
    ) {
        return left.enabled() == right.enabled()
            && left.coolingDown() == right.coolingDown()
            && left.fundableExecutions() == right.fundableExecutions()
            && left.selected() == right.selected()
            && left.storedFirstCount() == right.storedFirstCount()
            && left.storedSecondCount() == right.storedSecondCount()
            && left.effectiveFirstCount() == right.effectiveFirstCount()
            && left.effectiveSecondCount() == right.effectiveSecondCount();
    }

    record Plan(boolean fullCatalogueRequired, List<CatalogueDeltaPayload.RowDelta> deltas) {
    }

    private CatalogueDeltaPlanner() {
    }
}
