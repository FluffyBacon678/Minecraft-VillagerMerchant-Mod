package com.fluffybacon.merchantvillager.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GlobalTradeCatalogueTest {
    @Test
    void encodedExactSignatureIsComponentSensitiveAndCanonical() {
        JsonObject firstOutput = new JsonObject();
        firstOutput.addProperty("id", "minecraft:paper");
        JsonObject firstComponents = new JsonObject();
        firstComponents.addProperty("minecraft:custom_name", "A");
        firstOutput.add("components", firstComponents);

        JsonObject reorderedOutput = new JsonObject();
        JsonObject reorderedComponents = new JsonObject();
        reorderedComponents.addProperty("minecraft:custom_name", "A");
        reorderedOutput.add("components", reorderedComponents);
        reorderedOutput.addProperty("id", "minecraft:paper");

        JsonObject changedOutput = new JsonObject();
        changedOutput.addProperty("id", "minecraft:paper");
        JsonObject changedComponents = new JsonObject();
        changedComponents.addProperty("minecraft:custom_name", "B");
        changedOutput.add("components", changedComponents);

        TradeCatalogueKey first = exact(TradeSignatures.canonical(firstOutput));
        TradeCatalogueKey reordered = exact(TradeSignatures.canonical(reorderedOutput));
        TradeCatalogueKey changed = exact(TradeSignatures.canonical(changedOutput));

        assertEquals(first, reordered);
        assertNotEquals(first, changed);
    }

    @Test
    void exactDuplicatesAggregateEveryProviderDeterministically() {
        var librarian = stableSource(provider("minecraft:librarian", 1, 0), key('a'), "paper");
        var cartographer = stableSource(provider("minecraft:cartographer", 1, 3), key('a'), "paper");

        List<TradeSampleAggregator.Group<String>> forward = TradeSampleAggregator.aggregate(
            List.of(librarian, cartographer)
        );
        List<TradeSampleAggregator.Group<String>> reversed = TradeSampleAggregator.aggregate(
            List.of(cartographer, librarian)
        );

        assertEquals(1, forward.size());
        assertFalse(forward.getFirst().variable());
        assertEquals(2, forward.getFirst().providers().size());
        assertEquals(forward, reversed);
    }

    @Test
    void hiddenStockAndExperienceFieldsDoNotSplitOneVisibleRecipe() {
        TradeCatalogueKey first = TradeSignatures.exactFromEncoded(
            "{item:minecraft:paper,count:24}",
            "-",
            "{id:minecraft:emerald,count:1}",
            12,
            2,
            0.05F,
            true
        );
        TradeCatalogueKey second = TradeSignatures.exactFromEncoded(
            "{item:minecraft:paper,count:24}",
            "-",
            "{id:minecraft:emerald,count:1}",
            16,
            10,
            0.2F,
            false
        );

        assertEquals(
            first,
            second,
            "One component-and-count-identical recipe must have one approval"
        );
    }

    @Test
    void liveObservedEntryMergesAndSortsEveryProvider() {
        TradeProvider librarian = provider("minecraft:librarian", 1, 3);
        TradeProvider cartographer = provider("minecraft:cartographer", 1, 0);
        List<TradeProvider> merged = TradeProvider.merged(
            TradeProvider.merged(List.of(librarian), cartographer),
            librarian
        );

        assertEquals(List.of(cartographer, librarian), merged);
        assertEquals(2, merged.size(), "Repeated observations must stay deduplicated");
    }

    @Test
    void varyingSamplesRemainSeparatelyApprovableExactVariants() {
        TradeCatalogueKey contextual = contextual('f');
        List<TradeSampleAggregator.Sample<String>> variants = List.of(
            new TradeSampleAggregator.Sample<>(key('a'), "variant-a"),
            new TradeSampleAggregator.Sample<>(key('b'), "variant-b"),
            new TradeSampleAggregator.Sample<>(key('c'), "variant-c")
        );
        var source = new TradeSampleAggregator.SourceSamples<>(
            provider("minecraft:librarian", 1, 0), variants, 8, 1
        );

        List<TradeSampleAggregator.Group<String>> groups = TradeSampleAggregator.aggregate(
            List.of(source)
        );

        assertEquals(3, groups.size());
        assertTrue(groups.stream().allMatch(TradeSampleAggregator.Group::variable));
        assertTrue(groups.stream().allMatch(group ->
            group.key().kind() == TradeCatalogueKey.Kind.EXACT));
        assertTrue(groups.stream().noneMatch(group -> group.key().equals(contextual)));
    }

    @Test
    void oneVariableFactoryExposesOnlyObservedExactVariants() {
        var source = new TradeSampleAggregator.SourceSamples<>(
            provider("minecraft:fisherman", 5, 2),
            List.of(
                new TradeSampleAggregator.Sample<>(key('a'), "fish"),
                new TradeSampleAggregator.Sample<>(key('b'), "book")
            ),
            4,
            0
        );

        List<TradeSampleAggregator.Group<String>> groups = TradeSampleAggregator.aggregate(
            List.of(source)
        );

        assertEquals(2, groups.size());
        assertTrue(groups.stream().allMatch(TradeSampleAggregator.Group::variable));
    }

    @Test
    void catalogueGroupingHasNoFiveHundredTwelveRowCeiling() {
        List<TradeSampleAggregator.SourceSamples<String>> sources = new ArrayList<>();
        for (int index = 0; index < 513; index++) {
            String digest = "%064x".formatted(index + 1);
            sources.add(stableSource(
                provider("test:profession_" + index, 1, 0),
                new TradeCatalogueKey(TradeCatalogueKey.Kind.EXACT, digest),
                "offer-" + index
            ));
        }
        Collections.reverse(sources);

        List<TradeSampleAggregator.Group<String>> groups = TradeSampleAggregator.aggregate(sources);

        assertEquals(513, groups.size());
    }

    private static TradeSampleAggregator.SourceSamples<String> stableSource(
        TradeProvider provider, TradeCatalogueKey exact, String preview
    ) {
        return new TradeSampleAggregator.SourceSamples<>(
            provider,
            List.of(
                new TradeSampleAggregator.Sample<>(exact, preview),
                new TradeSampleAggregator.Sample<>(exact, preview)
            ),
            2,
            0
        );
    }

    private static TradeProvider provider(String profession, int level, int factoryIndex) {
        return TradeProvider.villager(profession, level, "minecraft:plains", factoryIndex);
    }

    private static TradeCatalogueKey exact(String output) {
        return TradeSignatures.exactFromEncoded(
            "{item:minecraft:emerald,count:1}",
            "-",
            output,
            12,
            2,
            0.05F,
            true
        );
    }

    private static TradeCatalogueKey key(char character) {
        return new TradeCatalogueKey(
            TradeCatalogueKey.Kind.EXACT,
            String.valueOf(character).repeat(64)
        );
    }

    private static TradeCatalogueKey contextual(char character) {
        return new TradeCatalogueKey(
            TradeCatalogueKey.Kind.CONTEXTUAL,
            String.valueOf(character).repeat(64)
        );
    }
}
