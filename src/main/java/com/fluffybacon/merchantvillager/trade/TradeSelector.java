package com.fluffybacon.merchantvillager.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TradeSelector {
    public static Optional<Candidate> choose(List<Candidate> candidates, long roundRobinCursor) {
        List<Candidate> eligible = candidates.stream()
            .filter(Candidate::enabled)
            .filter(candidate -> !candidate.outOfStock())
            .filter(candidate -> candidate.fundableExecutions() > 0)
            .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        return eligible.stream().min(Comparator
            .comparing(Candidate::wanderingTrader, Comparator.reverseOrder())
            .thenComparing(Candidate::sameTargetBatch, Comparator.reverseOrder())
            .thenComparingDouble(Candidate::distanceSquared)
            .thenComparingLong(candidate -> Math.floorMod(candidate.fairnessOrder() - roundRobinCursor, eligible.size()))
            .thenComparing(Candidate::identity));
    }

    public record Candidate(
        String identity,
        boolean enabled,
        boolean outOfStock,
        int fundableExecutions,
        boolean wanderingTrader,
        boolean sameTargetBatch,
        double distanceSquared,
        long fairnessOrder
    ) {
    }

    private TradeSelector() {
    }
}
