package com.fluffybacon.merchantvillager.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

class PendingMarkerRemovalsTest {
    private static final BlockPos POST = new BlockPos(15, 64, 0);
    private static final BlockPos CHEST = POST.east();
    private static final BlockPos MARKER = CHEST.east();

    @Test void unavailableOwnedMarkerRemainsPendingAndDrainsAfterLoad() {
        PendingMarkerRemovals pending = new PendingMarkerRemovals(POST);
        AtomicBoolean chunkLoaded = new AtomicBoolean(false);
        AtomicBoolean taggedMarkerPresent = new AtomicBoolean(true);

        assertTrue(pending.enqueue(MARKER, CHEST, ChestRoleMarker.Role.DUAL));
        assertFalse(pending.drain(entry -> {
            assertEquals(MARKER, entry.markerPos());
            assertEquals(CHEST, entry.chestPos());
            assertEquals(ChestRoleMarker.Role.DUAL, entry.role());
            if (!chunkLoaded.get()) {
                return false;
            }
            taggedMarkerPresent.set(false);
            return true;
        }));
        assertEquals(1, pending.size(), "Unloaded cleanup must survive role reconciliation");
        assertTrue(taggedMarkerPresent.get(), "An unloaded marker cannot yet be removed safely");

        chunkLoaded.set(true);
        assertTrue(pending.drain(entry -> {
            taggedMarkerPresent.set(false);
            return true;
        }));
        assertEquals(0, pending.size(), "Validated cleanup must leave the persisted queue");
        assertFalse(taggedMarkerPresent.get(), "The exact tagged marker must be removed after load");
    }

    @Test void rejectsCleanupOutsideThePostStorageBoundary() {
        PendingMarkerRemovals pending = new PendingMarkerRemovals(POST);

        assertFalse(pending.enqueue(
            POST.add(512, 0, 512), CHEST, ChestRoleMarker.Role.DUAL
        ));
        assertFalse(pending.enqueue(
            MARKER, POST.add(2, 0, 0), ChestRoleMarker.Role.DUAL
        ));
        assertFalse(pending.enqueue(
            POST, CHEST, ChestRoleMarker.Role.DUAL
        ));
        assertEquals(0, pending.size());
    }

    @Test void persistedWorkIsDeduplicatedAndHardCapped() {
        PendingMarkerRemovals pending = new PendingMarkerRemovals(POST);
        assertTrue(pending.restore(new PendingMarkerRemovals.Entry(
            MARKER, CHEST, ChestRoleMarker.Role.DUAL
        )));
        assertFalse(pending.restore(new PendingMarkerRemovals.Entry(
            MARKER, CHEST, ChestRoleMarker.Role.DUAL
        )));

        outer:
        for (Direction chestDirection : Direction.values()) {
            BlockPos chest = POST.offset(chestDirection);
            for (Direction markerDirection : Direction.values()) {
                BlockPos marker = chest.offset(markerDirection);
                for (ChestRoleMarker.Role role : ChestRoleMarker.Role.values()) {
                    pending.enqueue(marker, chest, role);
                    if (pending.size() == PendingMarkerRemovals.MAX_ENTRIES) {
                        break outer;
                    }
                }
            }
        }

        assertEquals(PendingMarkerRemovals.MAX_ENTRIES, pending.size());
        assertFalse(pending.enqueue(CHEST.north(), CHEST, ChestRoleMarker.Role.EXPORT));
    }
}
