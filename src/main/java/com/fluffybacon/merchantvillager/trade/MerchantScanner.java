package com.fluffybacon.merchantvillager.trade;

import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.TradeOffer;

public final class MerchantScanner {
    public static List<OfferSnapshot> scan(ServerWorld world, BlockPos postPos) {
        double radius = MerchantVillagerConfig.EXTENDED_RADIUS;
        Box box = new Box(postPos).expand(radius);
        List<MerchantEntity> targets = world.getEntitiesByClass(
            MerchantEntity.class,
            box,
            target -> target.isAlive()
                && !target.isRemoved()
                && target.squaredDistanceTo(postPos.toCenterPos()) <= radius * radius
                && (!(target instanceof VillagerEntity villager)
                    || !villager.getVillagerData().profession().matchesKey(ModVillagerProfessions.MERCHANT_KEY))
        );

        targets.sort(Comparator.comparing(MerchantEntity::getUuid));
        List<OfferSnapshot> snapshots = new ArrayList<>();
        for (MerchantEntity target : targets) {
            boolean wandering = target instanceof WanderingTraderEntity;
            String profession = wandering ? "wandering_trader" : professionOf((VillagerEntity)target);
            int level = target instanceof VillagerEntity villager ? villager.getVillagerData().level() : 0;
            String name = target.hasCustomName()
                ? target.getCustomName().getString()
                : target.getType().getName().getString();
            double distanceSquared = target.squaredDistanceTo(postPos.toCenterPos());

            for (int index = 0; index < target.getOffers().size(); index++) {
                TradeOffer offer = target.getOffers().get(index);
                String fingerprint = OfferIdentity.create(
                    world.getRegistryManager(),
                    target.getUuid(),
                    index,
                    offer.getFirstBuyItem(),
                    offer.getSecondBuyItem(),
                    offer.getSellItem(),
                    offer.getMaxUses(),
                    offer.getMerchantExperience(),
                    offer.getPriceMultiplier()
                );
                snapshots.add(new OfferSnapshot(
                    target.getUuid(),
                    name,
                    profession,
                    level,
                    index,
                    offer.getFirstBuyItem(),
                    offer.getSecondBuyItem(),
                    offer.copySellItem(),
                    offer.getUses(),
                    offer.getMaxUses(),
                    distanceSquared,
                    wandering,
                    TargetMerchantAvailability.canTradeNow(target),
                    wandering ? ((WanderingTraderEntity)target).getDespawnDelay() : -1,
                    fingerprint
                ));
            }
        }
        return List.copyOf(snapshots);
    }

    private static String professionOf(VillagerEntity villager) {
        return villager.getVillagerData().profession().getKey()
            .map(key -> key.getValue().toString())
            .orElseGet(() -> Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().profession().value()).toString());
    }

    private MerchantScanner() {
    }
}
