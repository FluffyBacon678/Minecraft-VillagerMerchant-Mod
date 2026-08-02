package com.fluffybacon.merchantvillager.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChestCandidateSelectorTest {
    private static ChestCandidateSelector.Candidate candidate(
        long pos, double distance, boolean loaded, boolean exists, boolean capacity, boolean reachable
    ) {
        return new ChestCandidateSelector.Candidate(pos, distance, loaded, exists, capacity, reachable);
    }

    @Test void closestValidChestSelected() {
        assertEquals(2, ChestCandidateSelector.choose(List.of(
            candidate(1, 10, true, true, true, true),
            candidate(2, 2, true, true, true, true)
        )).orElseThrow().stablePosition());
    }

    @Test void fullChestRejected() {
        assertTrue(ChestCandidateSelector.choose(List.of(
            candidate(1, 1, true, true, false, true)
        )).isEmpty());
    }

    @Test void nextClosestSelected() {
        assertEquals(2, ChestCandidateSelector.choose(List.of(
            candidate(1, 1, true, true, false, true),
            candidate(2, 3, true, true, true, true)
        )).orElseThrow().stablePosition());
    }

    @Test void unloadedChestRejected() {
        assertTrue(ChestCandidateSelector.choose(List.of(
            candidate(1, 1, false, true, true, true)
        )).isEmpty());
    }

    @Test void destroyedChestRejected() {
        assertTrue(ChestCandidateSelector.choose(List.of(
            candidate(1, 1, true, false, true, true)
        )).isEmpty());
    }

    @Test void unreachableChestRejected() {
        assertTrue(ChestCandidateSelector.choose(List.of(
            candidate(1, 1, true, true, true, false)
        )).isEmpty());
    }

    @Test void equalDistanceUsesStablePosition() {
        assertEquals(3, ChestCandidateSelector.choose(List.of(
            candidate(7, 4, true, true, true, true),
            candidate(3, 4, true, true, true, true)
        )).orElseThrow().stablePosition());
    }
}
