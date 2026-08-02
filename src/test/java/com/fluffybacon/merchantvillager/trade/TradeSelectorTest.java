package com.fluffybacon.merchantvillager.trade;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TradeSelectorTest {
    private static TradeSelector.Candidate candidate(
        String id, boolean enabled, boolean out, int funds, boolean wandering, boolean batch,
        double distance, long fairness
    ) {
        return new TradeSelector.Candidate(id, enabled, out, funds, wandering, batch, distance, fairness);
    }

    @Test void disabledTradeIsIgnored() {
        assertTrue(TradeSelector.choose(List.of(candidate("x", false, false, 2, false, false, 1, 0)), 0).isEmpty());
    }

    @Test void enabledTradeIsConsidered() {
        assertEquals("v", TradeSelector.choose(List.of(candidate("v", true, false, 2, false, false, 1, 0)), 0).orElseThrow().identity());
    }

    @Test void missingMaterialsPreventSelection() {
        assertTrue(TradeSelector.choose(List.of(candidate("v", true, false, 0, false, false, 1, 0)), 0).isEmpty());
    }

    @Test void outOfStockTradeIsIgnored() {
        assertTrue(TradeSelector.choose(List.of(candidate("v", true, true, 2, false, false, 1, 0)), 0).isEmpty());
    }

    @Test void wanderingTraderHasPriority() {
        var selected = TradeSelector.choose(List.of(
            candidate("villager", true, false, 2, false, false, 1, 0),
            candidate("wandering", true, false, 2, true, false, 100, 1)
        ), 0).orElseThrow();
        assertEquals("wandering", selected.identity());
    }

    @Test void sameTargetBatchIsPreferred() {
        var selected = TradeSelector.choose(List.of(
            candidate("single", true, false, 2, false, false, 1, 0),
            candidate("batch", true, false, 2, false, true, 5, 1)
        ), 0).orElseThrow();
        assertEquals("batch", selected.identity());
    }

    @Test void shorterDistanceBreaksTie() {
        var selected = TradeSelector.choose(List.of(
            candidate("far", true, false, 2, false, false, 10, 0),
            candidate("near", true, false, 2, false, false, 2, 1)
        ), 0).orElseThrow();
        assertEquals("near", selected.identity());
    }
}
