package com.fluffybacon.merchantvillager.trade;

import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import com.fluffybacon.merchantvillager.mixin.DeterministicVanillaTradeFactory;
import com.fluffybacon.merchantvillager.mixin.ProcessItemFactoryAccessor;
import com.fluffybacon.merchantvillager.mixin.RandomizedVanillaTradeFactory;
import com.fluffybacon.merchantvillager.mixin.SellMapFactoryAccessor;
import com.fluffybacon.merchantvillager.mixin.SellItemFactoryAccessor;
import com.fluffybacon.merchantvillager.mixin.TypedWrapperFactoryAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, server-built index of active villager and wandering-trader trade
 * factories. It has no row limit and does not depend on nearby entities.
 *
 * <p>Factories are sampled with deterministic seeds and every observed variant
 * remains a separately approvable, component-sensitive row. Explorer maps use
 * a narrowly normalized destination identity; opaque custom executable code is
 * discovered only from real live offers. Individual failures are isolated.
 */
public final class GlobalTradeCatalogue {
    public static final int DEFAULT_SAMPLE_COUNT = 4;

    private final List<Entry> entries;
    private final List<SamplingIssue> samplingIssues;
    private final Map<TradeCatalogueKey, Entry> byKey;
    private final int sourceCount;
    private final int sampledOfferCount;

    private GlobalTradeCatalogue(
        Collection<Entry> entries,
        List<SamplingIssue> samplingIssues,
        int sourceCount,
        int sampledOfferCount
    ) {
        List<Entry> built = entries.stream()
            .sorted(Comparator.comparing(Entry::key))
            .toList();
        Map<TradeCatalogueKey, Entry> index = new LinkedHashMap<>();
        for (Entry entry : built) {
            index.put(entry.key(), entry);
        }
        this.entries = built;
        this.samplingIssues = samplingIssues.stream()
            .sorted(Comparator.comparing(SamplingIssue::provider)
                .thenComparingInt(SamplingIssue::sampleIndex))
            .toList();
        this.byKey = Map.copyOf(index);
        this.sourceCount = sourceCount;
        this.sampledOfferCount = sampledOfferCount;
    }

    /** Builds from the final active vanilla/Fabric-mutated runtime tables. */
    public static GlobalTradeCatalogue fromRuntimeTables(ServerWorld world) {
        return fromRuntimeTables(world, DEFAULT_SAMPLE_COUNT);
    }

    public static GlobalTradeCatalogue fromRuntimeTables(ServerWorld world, int sampleCount) {
        Objects.requireNonNull(world, "world");
        List<TradeSampleSource> sources = runtimeSources(world);
        return fromSources(world.getRegistryManager(), sources, sampleCount);
    }

    /** Public test/integration seam for additional explicitly described sources. */
    public static GlobalTradeCatalogue fromSources(
        RegistryWrapper.WrapperLookup registries,
        Collection<TradeSampleSource> sources,
        int sampleCount
    ) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(sources, "sources");
        if (sampleCount < 2 || sampleCount > 256) {
            throw new IllegalArgumentException("sampleCount must be between 2 and 256");
        }

        List<TradeSampleSource> orderedSources = sources.stream()
            .sorted(Comparator.comparing(source -> source.provider().stableId()))
            .toList();
        List<TradeSampleAggregator.SourceSamples<TradeOffer>> sampledSources = new ArrayList<>();
        List<SamplingIssue> issues = new ArrayList<>();
        int sampledOffers = 0;

        for (TradeSampleSource source : orderedSources) {
            if (source.policy() == SamplingPolicy.OPAQUE_UNSAFE) {
                issues.add(new SamplingIssue(
                    source.provider(),
                    -1,
                    "OpaqueTradeFactory",
                    "Skipped pre-arrival preview because Fabric exposes only executable factory code"
                ));
                continue;
            }
            List<TradeSampleAggregator.Sample<TradeOffer>> samples = new ArrayList<>();
            int nullSamples = 0;
            int attempts = source.policy() == SamplingPolicy.DETERMINISTIC_VANILLA
                    || source.policy() == SamplingPolicy.CONTEXTUAL_MAP
                ? 1
                : sampleCount;
            for (int sampleIndex = 0; sampleIndex < attempts; sampleIndex++) {
                long seed = deterministicSeed(source.provider(), sampleIndex);
                try {
                    TradeOffer offer = source.sampler().sample(seed, sampleIndex);
                    if (offer == null) {
                        nullSamples++;
                        continue;
                    }
                    sampledOffers++;
                    TradeCatalogueKey approvalKey = source.policy() == SamplingPolicy.CONTEXTUAL_MAP
                        ? TradeSignatures.contextualMap(registries, offer)
                        : TradeSignatures.exact(registries, offer);
                    samples.add(new TradeSampleAggregator.Sample<>(
                        approvalKey,
                        offer.copy()
                    ));
                } catch (Exception failure) {
                    issues.add(new SamplingIssue(
                        source.provider(),
                        sampleIndex,
                        failure.getClass().getName(),
                        safeMessage(failure)
                    ));
                }
            }

            sampledSources.add(new TradeSampleAggregator.SourceSamples<>(
                source.provider(),
                samples,
                attempts,
                nullSamples,
                source.policy() == SamplingPolicy.CONTEXTUAL_MAP
            ));
        }

        List<Entry> entries = TradeSampleAggregator.aggregate(sampledSources).stream()
            .map(group -> new Entry(
                group.key(),
                TradeTemplate.from(group.preview()),
                group.variable(),
                group.providers(),
                group.exactVariantCount(),
                group.attemptedSamples(),
                group.nullSamples()
            ))
            .toList();

        return new GlobalTradeCatalogue(
            entries,
            issues,
            orderedSources.size(),
            sampledOffers
        );
    }

    /** Exact resolution is attempted before the narrowly normalized map identity. */
    public Optional<Resolution> resolve(
        RegistryWrapper.WrapperLookup registries, TradeOffer liveOffer
    ) {
        TradeCatalogueKey exact = TradeSignatures.exact(registries, liveOffer);
        Entry exactEntry = byKey.get(exact);
        if (exactEntry != null) {
            return Optional.of(new Resolution(exactEntry, ResolutionKind.EXACT));
        }
        TradeCatalogueKey contextual = TradeSignatures.contextualMap(registries, liveOffer);
        Entry contextualEntry = byKey.get(contextual);
        return contextualEntry == null
            ? Optional.empty()
            : Optional.of(new Resolution(contextualEntry, ResolutionKind.CONTEXTUAL_MAP));
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> find(TradeCatalogueKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public List<SamplingIssue> samplingIssues() {
        return samplingIssues;
    }

    public int sourceCount() {
        return sourceCount;
    }

    public int sampledOfferCount() {
        return sampledOfferCount;
    }

    private static List<TradeSampleSource> runtimeSources(ServerWorld world) {
        List<TradeSampleSource> sources = new ArrayList<>();
        boolean rebalanced = world.getEnabledFeatures().contains(FeatureFlags.TRADE_REBALANCE);
        Set<RegistryKey<VillagerProfession>> professions = new TreeSet<>(
            Comparator.comparing(key -> key.getValue().toString())
        );
        professions.addAll(TradeOffers.PROFESSION_TO_LEVELED_TRADE.keySet());
        if (rebalanced) {
            professions.addAll(TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.keySet());
        }

        List<RegistryEntry.Reference<VillagerType>> villagerTypes = Registries.VILLAGER_TYPE
            .streamEntries()
            .sorted(Comparator.comparing(entry -> entry.registryKey().getValue().toString()))
            .toList();
        BlockPos samplingPos = world.getSpawnPoint().getPos();

        for (RegistryKey<VillagerProfession> professionKey : professions) {
            if (professionKey.equals(ModVillagerProfessions.MERCHANT_KEY)) {
                continue;
            }
            Int2ObjectMap<TradeOffers.Factory[]> levels = rebalanced
                ? TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(professionKey)
                : null;
            if (levels == null) {
                levels = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(professionKey);
            }
            if (levels == null) {
                continue;
            }
            VillagerProfession profession = Registries.VILLAGER_PROFESSION.get(professionKey);
            if (profession == null) {
                continue;
            }
            RegistryEntry<VillagerProfession> professionEntry =
                Registries.VILLAGER_PROFESSION.getEntry(profession);
            List<Integer> orderedLevels = levels.keySet().intStream().boxed().sorted().toList();
            for (int level : orderedLevels) {
                TradeOffers.Factory[] factories = levels.get(level);
                if (factories == null) {
                    continue;
                }
                for (int factoryIndex = 0; factoryIndex < factories.length; factoryIndex++) {
                    TradeOffers.Factory factory = factories[factoryIndex];
                    if (factory == null) {
                        continue;
                    }
                    for (RegistryEntry.Reference<VillagerType> typeEntry : villagerTypes) {
                        TradeProvider provider = TradeProvider.villager(
                            professionKey.getValue().toString(),
                            level,
                            typeEntry.registryKey().getValue().toString(),
                            factoryIndex
                        );
                        TradeOffers.Factory effectiveFactory = factoryForType(
                            factory, typeEntry.registryKey()
                        );
                        if (effectiveFactory == null) {
                            continue;
                        }
                        VillagerEntity samplingVillager = EntityType.VILLAGER.create(
                            world, SpawnReason.COMMAND
                        );
                        if (samplingVillager == null) {
                            continue;
                        }
                        position(samplingVillager, samplingPos);
                        samplingVillager.setVillagerData(new VillagerData(
                            typeEntry, professionEntry, level
                        ));
                        SamplingPolicy policy = policyFor(effectiveFactory);
                        sources.add(new TradeSampleSource(provider, (seed, sampleIndex) -> {
                            if (effectiveFactory instanceof SellMapFactoryAccessor mapFactory) {
                                return safeMapPreview(mapFactory);
                            }
                            position(samplingVillager, samplingPos);
                            samplingVillager.setVillagerData(new VillagerData(
                                typeEntry, professionEntry, level
                            ));
                            return effectiveFactory.create(
                                world, samplingVillager, Random.create(seed)
                            );
                        }, policy));
                    }
                }
            }
        }

        List<Pair<TradeOffers.Factory[], Integer>> pools = TradeOffers.WANDERING_TRADER_TRADES;
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            Pair<TradeOffers.Factory[], Integer> pool = pools.get(poolIndex);
            TradeOffers.Factory[] factories = pool.getLeft();
            int selectionCount = pool.getRight();
            if (factories == null) {
                continue;
            }
            for (int factoryIndex = 0; factoryIndex < factories.length; factoryIndex++) {
                TradeOffers.Factory factory = factories[factoryIndex];
                if (factory == null) {
                    continue;
                }
                TradeProvider provider = TradeProvider.wanderingTrader(
                    poolIndex, selectionCount, factoryIndex
                );
                WanderingTraderEntity samplingTrader = EntityType.WANDERING_TRADER.create(
                    world, SpawnReason.COMMAND
                );
                if (samplingTrader == null) {
                    continue;
                }
                position(samplingTrader, samplingPos);
                SamplingPolicy policy = policyFor(factory);
                sources.add(new TradeSampleSource(provider, (seed, sampleIndex) -> {
                    if (factory instanceof SellMapFactoryAccessor mapFactory) {
                        return safeMapPreview(mapFactory);
                    }
                    position(samplingTrader, samplingPos);
                    return factory.create(world, samplingTrader, Random.create(seed));
                }, policy));
            }
        }
        return List.copyOf(sources);
    }

    private static void position(Entity entity, BlockPos pos) {
        entity.setPosition(Vec3d.ofBottomCenter(pos));
    }

    private static SamplingPolicy policyFor(TradeOffers.Factory factory) {
        if (factory instanceof SellMapFactoryAccessor) {
            return SamplingPolicy.CONTEXTUAL_MAP;
        }
        // Fabric's API stores opaque executable factories rather than metadata.
        // Known Minecraft table factories are audited here; arbitrary custom
        // code is discovered safely when a real live offer exists instead.
        if (factory.getClass().getEnclosingClass() != TradeOffers.class) {
            return SamplingPolicy.OPAQUE_UNSAFE;
        }
        if (factory instanceof RandomizedVanillaTradeFactory
            || (factory instanceof SellItemFactoryAccessor accessor
                && accessor.merchantVillager$getEnchantmentProviderKey().isPresent())
            || (factory instanceof ProcessItemFactoryAccessor accessor
                && accessor.merchantVillager$getEnchantmentProviderKey().isPresent())) {
            return SamplingPolicy.SAFE_VANILLA;
        }
        if (factory instanceof DeterministicVanillaTradeFactory
            || factory instanceof SellItemFactoryAccessor
            || factory instanceof ProcessItemFactoryAccessor) {
            return SamplingPolicy.DETERMINISTIC_VANILLA;
        }
        // Future vanilla factories remain conservatively multi-sampled until
        // their implementation is audited.
        return SamplingPolicy.SAFE_VANILLA;
    }

    private static TradeOffers.Factory factoryForType(
        TradeOffers.Factory factory, RegistryKey<VillagerType> type
    ) {
        TradeOffers.Factory current = factory;
        for (int depth = 0; depth < 8; depth++) {
            if (!(current instanceof TypedWrapperFactoryAccessor wrapper)) {
                return current;
            }
            current = wrapper.merchantVillager$getTypeToFactory().get(type);
            if (current == null) {
                return null;
            }
        }
        return null;
    }

    private static TradeOffer safeMapPreview(SellMapFactoryAccessor factory) {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponentTypes.ITEM_NAME, Text.translatable(factory.merchantVillager$getNameKey()));
        return new TradeOffer(
            new TradedItem(Items.EMERALD, factory.merchantVillager$getPrice()),
            Optional.of(new TradedItem(Items.COMPASS)),
            map,
            factory.merchantVillager$getMaxUses(),
            factory.merchantVillager$getExperience(),
            0.2F
        );
    }

    private static long deterministicSeed(TradeProvider provider, int sampleIndex) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (provider.stableId() + "|sample=" + sampleIndex).getBytes(StandardCharsets.UTF_8)
            );
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    public record Entry(
        TradeCatalogueKey key,
        TradeTemplate preview,
        boolean variable,
        List<TradeProvider> providers,
        int sampledExactVariantCount,
        int attemptedSamples,
        int nullSamples
    ) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(preview, "preview");
            providers = TradeProvider.normalized(providers);
        }

        /** Adds live provenance without duplicating an already-known provider. */
        public Entry withProvider(TradeProvider provider) {
            Objects.requireNonNull(provider, "provider");
            if (providers.contains(provider)) {
                return this;
            }
            return new Entry(
                key,
                preview,
                variable,
                TradeProvider.merged(providers, provider),
                sampledExactVariantCount,
                attemptedSamples,
                nullSamples
            );
        }
    }

    public record SamplingIssue(
        TradeProvider provider,
        int sampleIndex,
        String exceptionType,
        String message
    ) {
    }

    public record Resolution(Entry entry, ResolutionKind kind) {
    }

    public enum ResolutionKind {
        EXACT,
        CONTEXTUAL_MAP
    }

    public record TradeSampleSource(
        TradeProvider provider,
        Sampler sampler,
        SamplingPolicy policy
    ) {
        public TradeSampleSource(TradeProvider provider, Sampler sampler) {
            this(provider, sampler, SamplingPolicy.SAFE_VANILLA);
        }

        public TradeSampleSource {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(sampler, "sampler");
            Objects.requireNonNull(policy, "policy");
        }
    }

    public enum SamplingPolicy {
        SAFE_VANILLA,
        DETERMINISTIC_VANILLA,
        CONTEXTUAL_MAP,
        OPAQUE_UNSAFE
    }

    @FunctionalInterface
    public interface Sampler {
        @Nullable TradeOffer sample(long deterministicSeed, int sampleIndex) throws Exception;
    }

}
