package com.fluffybacon.merchantvillager.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Discovers logical chests that directly share a block face with a Merchant's
 * Post and deterministically assigns their import/export roles.
 *
 * <p>A double chest is one logical candidate. Its {@link Candidate#accessPos}
 * always remains a half that directly touches the post, while
 * {@link Candidate#partnerPos} identifies the connected half. This separation
 * lets callers use all 54 slots without ever broadening the touching-storage
 * safety boundary.</p>
 */
public final class AdjacentChestManager {
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
        .comparingLong(Candidate::logicalId)
        .thenComparingLong(candidate -> candidate.accessPos().asLong());

    /**
     * Scans only the six blocks that directly share a face with {@code postPos}.
     * Capacity and entity pathfinding deliberately do not affect discovery.
     */
    public static List<Candidate> scan(ServerWorld world, BlockPos postPos) {
        Map<Long, Candidate> logicalChests = new HashMap<>();
        for (Direction direction : Direction.values()) {
            inspect(world, postPos, postPos.offset(direction)).ifPresent(candidate ->
                logicalChests.merge(
                    candidate.logicalId(),
                    candidate,
                    AdjacentChestManager::preferStableAccess
                )
            );
        }
        return logicalChests.values().stream().sorted(CANDIDATE_ORDER).toList();
    }

    /**
     * Assigns one logical chest as dual-purpose, or distinct deterministic
     * Import and Export chests when at least two logical chests are present.
     */
    public static Assignment assign(List<Candidate> candidates) {
        return assign(candidates, Optional.empty(), Optional.empty());
    }

    /**
     * Reconciles a fresh scan with persisted access positions. Valid distinct
     * roles remain stable. A previous dual-purpose chest remains Import when a
     * second chest appears, and the new chest becomes Export.
     */
    public static Assignment assign(
        List<Candidate> candidates,
        Optional<BlockPos> previousImport,
        Optional<BlockPos> previousExport
    ) {
        List<Candidate> normalized = normalize(candidates);
        if (normalized.isEmpty()) {
            return Assignment.empty();
        }
        if (normalized.size() == 1) {
            Candidate only = normalized.getFirst();
            return new Assignment(Optional.of(only), Optional.of(only));
        }

        Candidate retainedImport = resolvePrevious(normalized, previousImport).orElse(null);
        Candidate retainedExport = resolvePrevious(normalized, previousExport).orElse(null);
        if (retainedImport != null
            && retainedExport != null
            && retainedImport.logicalId() != retainedExport.logicalId()) {
            return new Assignment(Optional.of(retainedImport), Optional.of(retainedExport));
        }
        if (retainedImport != null) {
            return new Assignment(
                Optional.of(retainedImport),
                Optional.of(firstDifferent(normalized, retainedImport))
            );
        }
        if (retainedExport != null) {
            return new Assignment(
                Optional.of(firstDifferent(normalized, retainedExport)),
                Optional.of(retainedExport)
            );
        }
        return new Assignment(Optional.of(normalized.get(0)), Optional.of(normalized.get(1)));
    }

    public static boolean isDirectFaceNeighbor(BlockPos postPos, BlockPos chestPos) {
        return postPos.getManhattanDistance(chestPos) == 1;
    }

    /**
     * Resolves a candidate's complete single/double inventory after rechecking
     * that its access half still directly touches the post and represents the
     * same logical chest.
     */
    public static Optional<Inventory> resolveInventory(
        ServerWorld world, BlockPos postPos, Candidate candidate
    ) {
        if (!isDirectFaceNeighbor(postPos, candidate.accessPos())) {
            return Optional.empty();
        }
        Optional<Candidate> current = inspect(world, postPos, candidate.accessPos());
        if (current.isEmpty() || current.get().logicalId() != candidate.logicalId()) {
            return Optional.empty();
        }
        return Optional.ofNullable(chestInventory(world, candidate.accessPos()));
    }

    /** Returns a single or combined double-chest inventory without loading chunks. */
    @Nullable
    public static Inventory chestInventory(ServerWorld world, BlockPos chestPos) {
        if (!world.isChunkLoaded(chestPos)) {
            return null;
        }
        BlockState state = world.getBlockState(chestPos);
        if (!(state.getBlock() instanceof ChestBlock chest)) {
            return null;
        }
        if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos partnerPos = chestPos.offset(ChestBlock.getFacing(state));
            if (!world.isChunkLoaded(partnerPos)) {
                return null;
            }
        }
        return ChestBlock.getInventory(chest, state, world, chestPos, true);
    }

    private static Optional<Candidate> inspect(
        ServerWorld world, BlockPos postPos, BlockPos accessPos
    ) {
        if (!isDirectFaceNeighbor(postPos, accessPos) || !world.isChunkLoaded(accessPos)) {
            return Optional.empty();
        }
        BlockState state = world.getBlockState(accessPos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return Optional.empty();
        }

        Optional<BlockPos> partner = Optional.empty();
        if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos partnerPos = accessPos.offset(ChestBlock.getFacing(state));
            if (!world.isChunkLoaded(partnerPos)) {
                return Optional.empty();
            }
            BlockState partnerState = world.getBlockState(partnerPos);
            if (partnerState.getBlock() != state.getBlock()
                || !(partnerState.getBlock() instanceof ChestBlock)
                || partnerState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                || !partnerPos.offset(ChestBlock.getFacing(partnerState)).equals(accessPos)) {
                return Optional.empty();
            }
            partner = Optional.of(partnerPos);
        }
        if (chestInventory(world, accessPos) == null) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(accessPos, partner));
    }

    private static List<Candidate> normalize(List<Candidate> candidates) {
        Map<Long, Candidate> logicalChests = new HashMap<>();
        for (Candidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            logicalChests.merge(
                candidate.logicalId(),
                candidate,
                AdjacentChestManager::preferStableAccess
            );
        }
        return logicalChests.values().stream().sorted(CANDIDATE_ORDER).toList();
    }

    private static Candidate preferStableAccess(Candidate left, Candidate right) {
        return Long.compare(left.accessPos().asLong(), right.accessPos().asLong()) <= 0
            ? left
            : right;
    }

    private static Optional<Candidate> resolvePrevious(
        List<Candidate> candidates, Optional<BlockPos> previous
    ) {
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().filter(candidate -> candidate.contains(previous.get())).findFirst();
    }

    private static Candidate firstDifferent(List<Candidate> candidates, Candidate excluded) {
        return candidates.stream()
            .filter(candidate -> candidate.logicalId() != excluded.logicalId())
            .findFirst()
            .orElseThrow();
    }

    public record Candidate(BlockPos accessPos, Optional<BlockPos> partnerPos) {
        public Candidate(BlockPos accessPos) {
            this(accessPos, Optional.empty());
        }

        public Candidate {
            accessPos = Objects.requireNonNull(accessPos, "accessPos").toImmutable();
            partnerPos = Objects.requireNonNull(partnerPos, "partnerPos")
                .map(BlockPos::toImmutable);
            if (partnerPos.filter(accessPos::equals).isPresent()) {
                throw new IllegalArgumentException("A double chest partner must be a different block");
            }
        }

        public long logicalId() {
            return partnerPos
                .map(partner -> Math.min(accessPos.asLong(), partner.asLong()))
                .orElseGet(accessPos::asLong);
        }

        public boolean contains(BlockPos pos) {
            return accessPos.equals(pos) || partnerPos.filter(pos::equals).isPresent();
        }

        public List<BlockPos> members() {
            List<BlockPos> members = new ArrayList<>(2);
            members.add(accessPos);
            partnerPos.ifPresent(members::add);
            members.sort(Comparator.comparingLong(BlockPos::asLong));
            return List.copyOf(members);
        }
    }

    public record Assignment(
        Optional<Candidate> importChest,
        Optional<Candidate> exportChest
    ) {
        public Assignment {
            importChest = Objects.requireNonNull(importChest, "importChest");
            exportChest = Objects.requireNonNull(exportChest, "exportChest");
            if (importChest.isPresent() != exportChest.isPresent()) {
                throw new IllegalArgumentException("Import and Export assignments must settle together");
            }
        }

        public static Assignment empty() {
            return new Assignment(Optional.empty(), Optional.empty());
        }

        public boolean isEmpty() {
            return importChest.isEmpty();
        }

        public boolean isDualPurpose() {
            return importChest.isPresent()
                && importChest.get().logicalId() == exportChest.orElseThrow().logicalId();
        }

        public Optional<BlockPos> importPos() {
            return importChest.map(Candidate::accessPos);
        }

        public Optional<BlockPos> exportPos() {
            return exportChest.map(Candidate::accessPos);
        }
    }

    private AdjacentChestManager() {
    }
}
