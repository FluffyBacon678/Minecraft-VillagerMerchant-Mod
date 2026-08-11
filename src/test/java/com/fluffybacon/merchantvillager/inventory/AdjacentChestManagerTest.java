package com.fluffybacon.merchantvillager.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class AdjacentChestManagerTest {
    private static final BlockPos A = new BlockPos(1, 4, 0);
    private static final BlockPos B = new BlockPos(0, 4, 1);
    private static final BlockPos C = new BlockPos(-1, 4, 0);

    @Test void noChestProducesNoRoles() {
        assertTrue(AdjacentChestManager.assign(List.of()).isEmpty());
    }

    @Test void oneLogicalChestIsDualPurpose() {
        var assignment = AdjacentChestManager.assign(List.of(candidate(A)));

        assertTrue(assignment.isDualPurpose());
        assertEquals(Optional.of(A), assignment.importPos());
        assertEquals(Optional.of(A), assignment.exportPos());
    }

    @Test void twoOrMoreChestsReceiveDistinctDeterministicRoles() {
        var forward = AdjacentChestManager.assign(List.of(candidate(C), candidate(A), candidate(B)));
        var reverse = AdjacentChestManager.assign(List.of(candidate(B), candidate(A), candidate(C)));

        assertEquals(forward, reverse);
        assertFalse(forward.isDualPurpose());
        assertNotEquals(
            forward.importChest().orElseThrow().logicalId(),
            forward.exportChest().orElseThrow().logicalId()
        );
    }

    @Test void duplicateViewsOfDoubleChestRemainOneLogicalCandidate() {
        BlockPos left = new BlockPos(1, 4, 0);
        BlockPos right = new BlockPos(2, 4, 0);
        var assignment = AdjacentChestManager.assign(List.of(
            new AdjacentChestManager.Candidate(left, Optional.of(right)),
            new AdjacentChestManager.Candidate(right, Optional.of(left))
        ));

        assertTrue(assignment.isDualPurpose());
        assertEquals(2, assignment.importChest().orElseThrow().members().size());
    }

    @Test void validPersistedDistinctRolesRemainStable() {
        var assignment = AdjacentChestManager.assign(
            List.of(candidate(A), candidate(B), candidate(C)),
            Optional.of(C),
            Optional.of(A)
        );

        assertEquals(Optional.of(C), assignment.importPos());
        assertEquals(Optional.of(A), assignment.exportPos());
    }

    @Test void previousDualChestStaysImportWhenSecondChestAppears() {
        var assignment = AdjacentChestManager.assign(
            List.of(candidate(A), candidate(B)),
            Optional.of(B),
            Optional.of(B)
        );

        assertEquals(Optional.of(B), assignment.importPos());
        assertNotEquals(Optional.of(B), assignment.exportPos());
    }

    @Test void survivingExportRoleIsPreservedWhenImportDisappears() {
        var assignment = AdjacentChestManager.assign(
            List.of(candidate(A), candidate(B)),
            Optional.of(C),
            Optional.of(B)
        );

        assertEquals(Optional.of(B), assignment.exportPos());
        assertEquals(Optional.of(A), assignment.importPos());
    }

    @Test void persistedPartnerHalfResolvesToTouchingAccessHalf() {
        BlockPos touching = new BlockPos(1, 4, 0);
        BlockPos partner = new BlockPos(2, 4, 0);
        var doubleChest = new AdjacentChestManager.Candidate(touching, Optional.of(partner));
        var assignment = AdjacentChestManager.assign(
            List.of(doubleChest, candidate(B)),
            Optional.of(partner),
            Optional.of(B)
        );

        assertEquals(Optional.of(touching), assignment.importPos());
    }

    @Test void safetyBoundaryAcceptsFacesButRejectsDiagonalsAndRadius() {
        BlockPos post = new BlockPos(4, 8, 12);
        for (var direction : net.minecraft.util.math.Direction.values()) {
            assertTrue(AdjacentChestManager.isDirectFaceNeighbor(post, post.offset(direction)));
        }
        assertFalse(AdjacentChestManager.isDirectFaceNeighbor(post, post.add(1, 0, 1)));
        assertFalse(AdjacentChestManager.isDirectFaceNeighbor(post, post.add(2, 0, 0)));
        assertFalse(AdjacentChestManager.isDirectFaceNeighbor(post, post));
    }

    private static AdjacentChestManager.Candidate candidate(BlockPos pos) {
        return new AdjacentChestManager.Candidate(pos);
    }
}
