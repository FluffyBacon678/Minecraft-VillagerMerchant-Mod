package com.fluffybacon.merchantvillager.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Bounded cleanup work for owned storage markers whose chunks were unavailable
 * when a post changed chest roles.
 */
public final class PendingMarkerRemovals {
    static final int MAX_ENTRIES = 32;
    static final int MAX_MARKER_DISTANCE = 3;

    private final BlockPos postPos;
    private final ArrayList<Entry> entries = new ArrayList<>();

    public PendingMarkerRemovals(BlockPos postPos) {
        this.postPos = Objects.requireNonNull(postPos, "postPos").toImmutable();
    }

    /** Adds valid cleanup work without allowing duplicate or unbounded NBT data. */
    public boolean enqueue(
        @Nullable BlockPos markerPos,
        @Nullable BlockPos chestPos,
        ChestRoleMarker.Role role
    ) {
        if (markerPos == null || chestPos == null || role == null) {
            return false;
        }
        Entry entry = new Entry(markerPos, chestPos, role);
        if (!isValidFor(postPos, entry)
            || entries.contains(entry)
            || entries.size() >= MAX_ENTRIES) {
            return false;
        }
        entries.add(entry);
        return true;
    }

    /** Restores one persisted entry through the same safety checks as live work. */
    public boolean restore(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        return enqueue(entry.markerPos(), entry.chestPos(), entry.role());
    }

    /**
     * Attempts every queued removal. Entries remain queued while the callback
     * reports that exact owned-marker cleanup cannot yet be validated.
     *
     * @return whether the queue changed
     */
    public boolean drain(Predicate<Entry> cleanupComplete) {
        Objects.requireNonNull(cleanupComplete, "cleanupComplete");
        boolean changed = false;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!isValidFor(postPos, entry) || cleanupComplete.test(entry)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }

    static boolean isValidFor(BlockPos postPos, Entry entry) {
        return AdjacentChestManager.isDirectFaceNeighbor(postPos, entry.chestPos())
            && entry.markerPos().getManhattanDistance(entry.chestPos()) == 1
            && entry.markerPos().getManhattanDistance(postPos) <= MAX_MARKER_DISTANCE
            && !entry.markerPos().equals(postPos);
    }

    public record Entry(
        BlockPos markerPos,
        BlockPos chestPos,
        ChestRoleMarker.Role role
    ) {
        private static final Codec<ChestRoleMarker.Role> ROLE_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(ChestRoleMarker.Role.valueOf(name));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Unknown storage-marker role: " + name);
                }
            },
            ChestRoleMarker.Role::name
        );

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("marker").forGetter(Entry::markerPos),
            BlockPos.CODEC.fieldOf("chest").forGetter(Entry::chestPos),
            ROLE_CODEC.fieldOf("role").forGetter(Entry::role)
        ).apply(instance, Entry::new));

        public Entry {
            markerPos = Objects.requireNonNull(markerPos, "markerPos").toImmutable();
            chestPos = Objects.requireNonNull(chestPos, "chestPos").toImmutable();
            role = Objects.requireNonNull(role, "role");
        }
    }
}
