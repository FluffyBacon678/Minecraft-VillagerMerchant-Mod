package com.fluffybacon.merchantvillager.inventory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ChestCandidateSelector {
    public static Optional<Candidate> choose(List<Candidate> candidates) {
        return candidates.stream()
            .filter(Candidate::loaded)
            .filter(Candidate::exists)
            .filter(Candidate::hasCapacity)
            .filter(Candidate::reachable)
            .min(Comparator.comparingDouble(Candidate::distanceSquared).thenComparingLong(Candidate::stablePosition));
    }

    public record Candidate(
        long stablePosition,
        double distanceSquared,
        boolean loaded,
        boolean exists,
        boolean hasCapacity,
        boolean reachable
    ) {
    }

    private ChestCandidateSelector() {
    }
}
