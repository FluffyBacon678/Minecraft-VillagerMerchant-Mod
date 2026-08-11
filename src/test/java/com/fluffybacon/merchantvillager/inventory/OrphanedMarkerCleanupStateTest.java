package com.fluffybacon.merchantvillager.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class OrphanedMarkerCleanupStateTest {
    private static final BlockPos POST = new BlockPos(15, 64, 0);
    private static final BlockPos CHEST = POST.east();
    private static final BlockPos MARKER = CHEST.east();
    private static final PendingMarkerRemovals.Entry REMOVAL =
        new PendingMarkerRemovals.Entry(MARKER, CHEST, ChestRoleMarker.Role.DUAL);
    private static final OrphanedMarkerCleanupState.CleanupEntry CLEANUP =
        new OrphanedMarkerCleanupState.CleanupEntry(POST, REMOVAL);

    @Test
    void pendingCleanupSurvivesSavedDataRoundTripAndUnavailableChunk() {
        OrphanedMarkerCleanupState original = new OrphanedMarkerCleanupState();
        assertTrue(original.enqueue(CLEANUP, true));
        JsonElement encoded = OrphanedMarkerCleanupState.CODEC
            .encodeStart(JsonOps.INSTANCE, original)
            .getOrThrow();
        OrphanedMarkerCleanupState restored = OrphanedMarkerCleanupState.CODEC
            .parse(JsonOps.INSTANCE, encoded)
            .getOrThrow();
        assertEquals(1, restored.entries().size(), "Saved cleanup must survive world reload");

        AtomicBoolean markerChunkAvailable = new AtomicBoolean(false);
        assertFalse(restored.drain(
            entry -> markerChunkAvailable.get(),
            OrphanedMarkerCleanupState.MAX_SCANS_PER_TICK
        ));
        assertEquals(1, restored.entries().size(), "Unavailable cleanup must remain persisted");

        markerChunkAvailable.set(true);
        assertTrue(restored.drain(
            entry -> markerChunkAvailable.get(),
            OrphanedMarkerCleanupState.MAX_SCANS_PER_TICK
        ));
        assertTrue(restored.entries().isEmpty(), "Cleanup must retire after exact removal succeeds");
    }

    @Test
    void persistedCleanupIsValidatedAndDeduplicated() {
        OrphanedMarkerCleanupState state = new OrphanedMarkerCleanupState();
        assertTrue(state.enqueue(CLEANUP, true));
        assertTrue(state.enqueue(CLEANUP, true), "Repeated destruction handoff must be idempotent");
        assertEquals(1, state.entries().size());

        var remote = new OrphanedMarkerCleanupState.CleanupEntry(
            POST,
            new PendingMarkerRemovals.Entry(
                POST.add(512, 0, 512), CHEST, ChestRoleMarker.Role.DUAL
            )
        );
        assertFalse(state.enqueue(remote, true), "Corrupt remote positions must never be queued");
        assertEquals(1, state.entries().size());
    }

    @Test
    void boundedDrainRotatesPastUnavailableEntries() {
        OrphanedMarkerCleanupState state = new OrphanedMarkerCleanupState();
        BlockPos targetPost = null;
        for (int x = 0; x < OrphanedMarkerCleanupState.MAX_SCANS_PER_TICK + 1; x++) {
            BlockPos post = POST.add(x * 4, 0, 0);
            BlockPos chest = post.east();
            BlockPos marker = chest.east();
            assertTrue(state.enqueue(new OrphanedMarkerCleanupState.CleanupEntry(
                post,
                new PendingMarkerRemovals.Entry(marker, chest, ChestRoleMarker.Role.DUAL)
            ), true));
            targetPost = post;
        }
        BlockPos finalTargetPost = targetPost;

        assertFalse(state.drain(
            entry -> entry.postPos().equals(finalTargetPost),
            OrphanedMarkerCleanupState.MAX_SCANS_PER_TICK
        ));
        assertTrue(state.drain(
            entry -> entry.postPos().equals(finalTargetPost),
            1
        ), "Round-robin cursor must eventually reach work behind unavailable entries");
        assertEquals(OrphanedMarkerCleanupState.MAX_SCANS_PER_TICK, state.entries().size());
    }
}
