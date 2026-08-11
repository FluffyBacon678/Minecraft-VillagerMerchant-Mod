package com.fluffybacon.merchantvillager.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/**
 * World-owned cleanup work that outlives a destroyed Merchant's Post.
 *
 * <p>Entries contain the complete marker ownership tuple. Cleanup never loads
 * a chunk and only removes a sign whose ownership token still matches exactly.
 */
public final class OrphanedMarkerCleanupState extends PersistentState {
    static final int MAX_SCANS_PER_TICK = 32;

    static final Codec<CleanupEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("post").forGetter(CleanupEntry::postPos),
            PendingMarkerRemovals.Entry.CODEC.fieldOf("removal")
                .forGetter(CleanupEntry::removal)
        ).apply(instance, CleanupEntry::new)
    );
    static final Codec<OrphanedMarkerCleanupState> CODEC = ENTRY_CODEC.listOf()
        .fieldOf("entries")
        .xmap(OrphanedMarkerCleanupState::fromPersisted, state -> List.copyOf(state.entries))
        .codec();
    public static final PersistentStateType<OrphanedMarkerCleanupState> TYPE =
        new PersistentStateType<>(
            "merchant_villager_marker_cleanup",
            OrphanedMarkerCleanupState::new,
            CODEC,
            // Custom persistent state has no vanilla DFU schema. Command
            // storage is the closest pass-through saved-data category; using
            // map data here could rewrite unrelated map-specific fields.
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        );

    private final ArrayList<CleanupEntry> entries = new ArrayList<>();
    private int scanCursor;

    OrphanedMarkerCleanupState() {
    }

    /**
     * Hands valid cleanup work to the world's save data. Existing identical
     * work is considered accepted so destruction can safely retry handoff.
     */
    public static boolean enqueue(
        ServerWorld world,
        BlockPos postPos,
        PendingMarkerRemovals.Entry removal
    ) {
        Objects.requireNonNull(world, "world");
        OrphanedMarkerCleanupState state =
            world.getPersistentStateManager().getOrCreate(TYPE);
        return state.enqueue(new CleanupEntry(postPos, removal), true);
    }

    /** Periodic fallback for work whose target chunk was already loaded. */
    public static void tick(ServerWorld world) {
        if (world.getTime() % 20L != 0L) {
            return;
        }
        OrphanedMarkerCleanupState state = world.getPersistentStateManager().get(TYPE);
        if (state != null) {
            state.drainInternal(
                entry -> removeIfAvailable(world, entry)
                    ? DrainResult.COMPLETE
                    : DrainResult.RETAIN,
                MAX_SCANS_PER_TICK
            );
        }
    }

    /** Immediately revisits matching work when its chunk becomes available. */
    public static void onChunkLoaded(ServerWorld world, ChunkPos loadedChunk) {
        OrphanedMarkerCleanupState state = world.getPersistentStateManager().get(TYPE);
        if (state == null || state.entries.isEmpty()) {
            return;
        }
        state.drainInternal(
            entry -> !new ChunkPos(entry.removal().markerPos()).equals(loadedChunk)
                ? DrainResult.SKIP
                : removeIfAvailable(world, entry)
                    ? DrainResult.COMPLETE
                    : DrainResult.RETAIN,
            state.entries.size()
        );
    }

    public static int pendingCount(ServerWorld world) {
        OrphanedMarkerCleanupState state = world.getPersistentStateManager().get(TYPE);
        return state == null ? 0 : state.entries.size();
    }

    /** Exact ownership lookup used by diagnostics and lifecycle regression tests. */
    public static boolean contains(
        ServerWorld world, BlockPos postPos, PendingMarkerRemovals.Entry removal
    ) {
        OrphanedMarkerCleanupState state = world.getPersistentStateManager().get(TYPE);
        return state != null && state.entries.contains(new CleanupEntry(postPos, removal));
    }

    private static boolean removeIfAvailable(ServerWorld world, CleanupEntry entry) {
        PendingMarkerRemovals.Entry removal = entry.removal();
        return ChestRoleMarker.removeOwned(
            world,
            removal.markerPos(),
            entry.postPos(),
            removal.chestPos(),
            removal.role()
        );
    }

    private static OrphanedMarkerCleanupState fromPersisted(List<CleanupEntry> persisted) {
        OrphanedMarkerCleanupState state = new OrphanedMarkerCleanupState();
        for (CleanupEntry entry : persisted) {
            state.enqueue(entry, false);
        }
        return state;
    }

    boolean enqueue(CleanupEntry entry, boolean dirty) {
        Objects.requireNonNull(entry, "entry");
        if (!isValid(entry)) {
            return false;
        }
        if (entries.contains(entry)) {
            return true;
        }
        entries.add(entry);
        if (dirty) {
            markDirty();
        }
        return true;
    }

    boolean drain(Predicate<CleanupEntry> cleanupComplete, int scanBudget) {
        Objects.requireNonNull(cleanupComplete, "cleanupComplete");
        return drainInternal(
            entry -> cleanupComplete.test(entry)
                ? DrainResult.COMPLETE
                : DrainResult.RETAIN,
            scanBudget
        );
    }

    private boolean drainInternal(DrainAttempt attempt, int scanBudget) {
        if (entries.isEmpty() || scanBudget <= 0) {
            return false;
        }
        boolean changed = false;
        int scanned = 0;
        int initialSize = entries.size();
        while (!entries.isEmpty() && scanned < scanBudget && scanned < initialSize) {
            if (scanCursor >= entries.size()) {
                scanCursor = 0;
            }
            CleanupEntry entry = entries.get(scanCursor);
            scanned++;
            DrainResult result = !isValid(entry)
                ? DrainResult.COMPLETE
                : attempt.tryCleanup(entry);
            if (result == DrainResult.COMPLETE) {
                entries.remove(scanCursor);
                changed = true;
            } else {
                scanCursor++;
            }
        }
        if (scanCursor >= entries.size()) {
            scanCursor = 0;
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }

    List<CleanupEntry> entries() {
        return List.copyOf(entries);
    }

    static boolean isValid(CleanupEntry entry) {
        return isValid(entry.postPos(), entry.removal());
    }

    public static boolean isValid(
        BlockPos postPos, PendingMarkerRemovals.Entry removal
    ) {
        return PendingMarkerRemovals.isValidFor(postPos, removal);
    }

    public record CleanupEntry(
        BlockPos postPos,
        PendingMarkerRemovals.Entry removal
    ) {
        public CleanupEntry {
            postPos = Objects.requireNonNull(postPos, "postPos").toImmutable();
            removal = Objects.requireNonNull(removal, "removal");
        }
    }

    private enum DrainResult {
        COMPLETE,
        RETAIN,
        SKIP
    }

    @FunctionalInterface
    private interface DrainAttempt {
        DrainResult tryCleanup(CleanupEntry entry);
    }
}
