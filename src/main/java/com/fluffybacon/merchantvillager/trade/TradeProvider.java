package com.fluffybacon.merchantvillager.trade;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Identifies one deterministic occurrence in the active runtime trade tables. */
public record TradeProvider(
    Kind kind,
    String professionId,
    int level,
    String villagerTypeId,
    int poolIndex,
    int poolSelectionCount,
    int factoryIndex
) implements Comparable<TradeProvider> {
    public TradeProvider {
        Objects.requireNonNull(kind, "kind");
        professionId = Objects.requireNonNull(professionId, "professionId");
        villagerTypeId = Objects.requireNonNull(villagerTypeId, "villagerTypeId");
        if (level < 0 || poolIndex < -1 || poolSelectionCount < 0 || factoryIndex < 0) {
            throw new IllegalArgumentException("Invalid trade provider coordinates");
        }
    }

    public static TradeProvider villager(
        String professionId, int level, String villagerTypeId, int factoryIndex
    ) {
        return new TradeProvider(
            Kind.VILLAGER,
            professionId,
            level,
            villagerTypeId,
            -1,
            0,
            factoryIndex
        );
    }

    public static TradeProvider wanderingTrader(
        int poolIndex, int poolSelectionCount, int factoryIndex
    ) {
        return new TradeProvider(
            Kind.WANDERING_TRADER,
            "minecraft:wandering_trader",
            0,
            "",
            poolIndex,
            poolSelectionCount,
            factoryIndex
        );
    }

    /** Stable seed/provenance material for a fixed active table layout. */
    public String stableId() {
        return kind + "|" + professionId + "|" + level + "|" + villagerTypeId
            + "|" + poolIndex + "|" + poolSelectionCount + "|" + factoryIndex;
    }

    static List<TradeProvider> normalized(Collection<TradeProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        return List.copyOf(new TreeSet<>(providers));
    }

    static List<TradeProvider> merged(
        Collection<TradeProvider> providers, TradeProvider additional
    ) {
        Objects.requireNonNull(additional, "additional");
        TreeSet<TradeProvider> merged = new TreeSet<>(providers);
        merged.add(additional);
        return List.copyOf(merged);
    }

    @Override
    public int compareTo(TradeProvider other) {
        return stableId().compareTo(other.stableId());
    }

    public enum Kind {
        VILLAGER,
        WANDERING_TRADER
    }
}
