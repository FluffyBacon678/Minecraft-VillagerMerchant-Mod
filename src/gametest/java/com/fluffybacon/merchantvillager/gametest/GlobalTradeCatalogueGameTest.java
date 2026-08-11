package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.trade.GlobalTradeCatalogue;
import com.fluffybacon.merchantvillager.trade.TradeProvider;
import com.fluffybacon.merchantvillager.trade.TradeCatalogueKey;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.village.TradedItem;
import net.minecraft.village.TradeOffer;

public final class GlobalTradeCatalogueGameTest {
    @GameTest(maxTicks = 20)
    public void activeRuntimeTablesMaterializeWithoutNearbyMerchants(TestContext context) {
        int beforeMapId = context.getWorld().increaseAndGetMapId().id();
        GlobalTradeCatalogue catalogue = GlobalTradeCatalogue.fromRuntimeTables(
            context.getWorld(), 2
        );
        int afterMapId = context.getWorld().increaseAndGetMapId().id();

        context.assertTrue(catalogue.sourceCount() > 0, "Active trade tables must expose sources");
        context.assertTrue(!catalogue.entries().isEmpty(), "Active trade tables must materialize rows");
        context.assertTrue(
            catalogue.entries().stream()
                .flatMap(entry -> entry.providers().stream())
                .anyMatch(provider -> provider.kind() == TradeProvider.Kind.VILLAGER),
            "Global catalogue must include villager providers"
        );
        context.assertTrue(
            catalogue.entries().stream()
                .flatMap(entry -> entry.providers().stream())
                .anyMatch(provider -> provider.kind() == TradeProvider.Kind.WANDERING_TRADER),
            "Global catalogue must include wandering-trader providers"
        );
        context.assertTrue(
            catalogue.entries().stream().anyMatch(entry ->
                entry.key().kind() == TradeCatalogueKey.Kind.CONTEXTUAL),
            "Explorer maps must have safe contextual previews even without a nearby structure"
        );
        context.assertEquals(
            beforeMapId + 1,
            afterMapId,
            "Global catalogue generation must not allocate or fill map saved data"
        );
        context.complete();
    }

    @GameTest(maxTicks = 20)
    public void opaqueFactoriesAreNotExecutedBeforeARealOfferExists(TestContext context) {
        AtomicInteger calls = new AtomicInteger();
        var provider = TradeProvider.villager(
            "test:opaque", 1, "minecraft:plains", 0
        );
        GlobalTradeCatalogue catalogue = GlobalTradeCatalogue.fromSources(
            context.getWorld().getRegistryManager(),
            List.of(new GlobalTradeCatalogue.TradeSampleSource(
                provider,
                (seed, sample) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("opaque sampler was executed");
                },
                GlobalTradeCatalogue.SamplingPolicy.OPAQUE_UNSAFE
            )),
            2
        );
        context.assertEquals(0, calls.get(), "Opaque factory code must not run during indexing");
        context.assertTrue(catalogue.entries().isEmpty(), "Opaque pre-arrival rows must fail closed");
        context.assertEquals(1, catalogue.samplingIssues().size(), "Skip reason must remain diagnosable");
        context.complete();
    }

    @GameTest(maxTicks = 20)
    public void deterministicFactoriesAreSampledOnlyOnce(TestContext context) {
        AtomicInteger calls = new AtomicInteger();
        var provider = TradeProvider.villager(
            "test:deterministic", 1, "minecraft:plains", 0
        );
        GlobalTradeCatalogue catalogue = GlobalTradeCatalogue.fromSources(
            context.getWorld().getRegistryManager(),
            List.of(new GlobalTradeCatalogue.TradeSampleSource(
                provider,
                (seed, sample) -> {
                    calls.incrementAndGet();
                    return new TradeOffer(
                        new TradedItem(Items.PAPER),
                        new ItemStack(Items.EMERALD),
                        12,
                        2,
                        0.05F
                    );
                },
                GlobalTradeCatalogue.SamplingPolicy.DETERMINISTIC_VANILLA
            )),
            4
        );
        context.assertEquals(1, calls.get(), "Deterministic factory should use one seed");
        context.assertEquals(1, catalogue.sampledOfferCount(), "Only one preview should be built");
        context.assertEquals(
            1,
            catalogue.entries().getFirst().attemptedSamples(),
            "Telemetry must report the actual one-sample policy"
        );
        AtomicInteger randomizedCalls = new AtomicInteger();
        GlobalTradeCatalogue.fromSources(
            context.getWorld().getRegistryManager(),
            List.of(new GlobalTradeCatalogue.TradeSampleSource(
                TradeProvider.villager("test:randomized", 1, "minecraft:plains", 0),
                (seed, sample) -> {
                    randomizedCalls.incrementAndGet();
                    return new TradeOffer(
                        new TradedItem(Items.PAPER),
                        new ItemStack(Items.EMERALD),
                        12,
                        2,
                        0.05F
                    );
                },
                GlobalTradeCatalogue.SamplingPolicy.SAFE_VANILLA
            )),
            4
        );
        context.assertEquals(
            4, randomizedCalls.get(), "Randomized factory should retain all requested seeds"
        );
        context.complete();
    }
}
