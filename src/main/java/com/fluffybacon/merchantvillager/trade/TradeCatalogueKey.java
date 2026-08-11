package com.fluffybacon.merchantvillager.trade;

import java.util.Objects;

/**
 * A target-independent approval key for one catalogue row.
 *
 * <p>Exact keys retain every component-sensitive part of an offer. Contextual
 * keys are reserved for audited cases such as explorer maps where destination
 * ids vary but the displayed map type and complete material cost stay fixed.
 */
public record TradeCatalogueKey(Kind kind, String digest) implements Comparable<TradeCatalogueKey> {
    public TradeCatalogueKey {
        Objects.requireNonNull(kind, "kind");
        if (digest == null || digest.length() != 64 || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Trade catalogue digest must be 64 lowercase hex characters");
        }
    }

    public String serialized() {
        return kind.prefix + digest;
    }

    @Override
    public int compareTo(TradeCatalogueKey other) {
        return serialized().compareTo(other.serialized());
    }

    public enum Kind {
        EXACT("exact:"),
        CONTEXTUAL("context:");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }
}
