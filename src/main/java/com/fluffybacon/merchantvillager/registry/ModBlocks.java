package com.fluffybacon.merchantvillager.registry;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.block.MerchantPostBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModBlocks {
    public static final RegistryKey<Block> MERCHANT_POST_KEY =
        RegistryKey.of(RegistryKeys.BLOCK, MerchantVillagerMod.id("merchant_post"));
    public static final RegistryKey<Item> MERCHANT_POST_ITEM_KEY =
        RegistryKey.of(RegistryKeys.ITEM, MerchantVillagerMod.id("merchant_post"));

    public static final MerchantPostBlock MERCHANT_POST = Registry.register(
        Registries.BLOCK,
        MERCHANT_POST_KEY,
        new MerchantPostBlock(AbstractBlock.Settings.copy(Blocks.BARREL).registryKey(MERCHANT_POST_KEY))
    );

    public static final BlockItem MERCHANT_POST_ITEM = Registry.register(
        Registries.ITEM,
        MERCHANT_POST_ITEM_KEY,
        new BlockItem(MERCHANT_POST, new Item.Settings().registryKey(MERCHANT_POST_ITEM_KEY).useBlockPrefixedTranslationKey())
    );

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(MERCHANT_POST_ITEM));
    }

    private ModBlocks() {
    }
}
