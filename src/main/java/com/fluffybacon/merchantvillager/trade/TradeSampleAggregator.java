package com.fluffybacon.merchantvillager.trade;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Pure deterministic grouping stage shared by runtime sampling and unit tests. */
final class TradeSampleAggregator {
    static <T> List<Group<T>> aggregate(Collection<SourceSamples<T>> unsortedSources) {
        List<SourceSamples<T>> sources = unsortedSources.stream()
            .sorted(Comparator.comparing(source -> source.provider().stableId()))
            .toList();
        Map<TradeCatalogueKey, MutableGroup<T>> grouped = new LinkedHashMap<>();
        for (SourceSamples<T> source : sources) {
            Map<TradeCatalogueKey, Sample<T>> exact = new LinkedHashMap<>();
            for (Sample<T> sample : source.samples()) {
                exact.putIfAbsent(sample.exactKey(), sample);
            }
            if (exact.isEmpty()) {
                continue;
            }
            boolean variableSource = source.contextual() || exact.size() > 1;
            for (Sample<T> sample : exact.values()) {
                grouped.computeIfAbsent(
                    sample.exactKey(),
                    key -> new MutableGroup<>(key, sample.value())
                ).add(
                    source.provider(),
                    exact.keySet(),
                    source.attemptedSamples(),
                    source.nullSamples(),
                    variableSource
                );
            }
        }
        return grouped.values().stream()
            .map(MutableGroup::freeze)
            .sorted(Comparator.comparing(Group::key))
            .toList();
    }

    record SourceSamples<T>(
        TradeProvider provider,
        List<Sample<T>> samples,
        int attemptedSamples,
        int nullSamples,
        boolean contextual
    ) {
        SourceSamples(
            TradeProvider provider,
            List<Sample<T>> samples,
            int attemptedSamples,
            int nullSamples
        ) {
            this(provider, samples, attemptedSamples, nullSamples, false);
        }

        SourceSamples {
            samples = List.copyOf(samples);
        }
    }

    record Sample<T>(TradeCatalogueKey exactKey, T value) {
    }

    record Group<T>(
        TradeCatalogueKey key,
        T preview,
        boolean variable,
        List<TradeProvider> providers,
        int exactVariantCount,
        int attemptedSamples,
        int nullSamples
    ) {
        Group {
            providers = List.copyOf(providers);
        }
    }

    private static final class MutableGroup<T> {
        private final TradeCatalogueKey key;
        private final T preview;
        private boolean variable;
        private final Set<TradeProvider> providers = new TreeSet<>();
        private final Set<TradeCatalogueKey> exactVariants = new LinkedHashSet<>();
        private int attemptedSamples;
        private int nullSamples;

        private MutableGroup(TradeCatalogueKey key, T preview) {
            this.key = key;
            this.preview = preview;
        }

        private void add(
            TradeProvider provider,
            Collection<TradeCatalogueKey> variants,
            int attemptedSamples,
            int nullSamples,
            boolean variable
        ) {
            providers.add(provider);
            exactVariants.addAll(variants);
            this.attemptedSamples += attemptedSamples;
            this.nullSamples += nullSamples;
            this.variable |= variable;
        }

        private Group<T> freeze() {
            return new Group<>(
                key,
                preview,
                variable,
                List.copyOf(providers),
                exactVariants.size(),
                attemptedSamples,
                nullSamples
            );
        }
    }

    private TradeSampleAggregator() {
    }
}
