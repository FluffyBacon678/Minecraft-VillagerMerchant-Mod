package com.fluffybacon.merchantvillager.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Keeps a verified Fabric datagen launch available for registry/resource
 * validation. The small recipe, loot, tags, models, and original pixel assets
 * are intentionally reviewed and maintained as hand-authored resources.
 */
public final class MerchantVillagerDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        generator.createPack();
    }
}
