package com.fluffybacon.merchantvillager;

import com.fluffybacon.merchantvillager.command.MerchantVillagerCommands;
import com.fluffybacon.merchantvillager.inventory.StorageMarkerEvents;
import com.fluffybacon.merchantvillager.merchant.MerchantInteractions;
import com.fluffybacon.merchantvillager.merchant.MerchantClothing;
import com.fluffybacon.merchantvillager.network.ModPayloads;
import com.fluffybacon.merchantvillager.registry.ModBlockEntities;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.registry.ModPointOfInterests;
import com.fluffybacon.merchantvillager.registry.ModScreenHandlers;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import com.fluffybacon.merchantvillager.trade.GlobalTradeCatalogueCache;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MerchantVillagerMod implements ModInitializer {
    public static final String MOD_ID = "merchant_villager";
    public static final Logger LOGGER = LoggerFactory.getLogger("Merchant Villager");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        MerchantClothing.initialize();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModScreenHandlers.initialize();
        ModPointOfInterests.initialize();
        ModVillagerProfessions.initialize();
        GlobalTradeCatalogueCache.initialize();
        ModPayloads.initializeServer();
        MerchantInteractions.initialize();
        StorageMarkerEvents.initialize();
        MerchantVillagerCommands.initialize();
        LOGGER.info("Merchant Villager initialized");
    }
}
