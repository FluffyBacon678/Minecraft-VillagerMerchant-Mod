package com.fluffybacon.merchantvillager.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

/** Canonical target-independent hashes used by the global catalogue. */
public final class TradeSignatures {
    private static final String EXACT_VERSION = "merchant-villager-recipe-v2";
    private static final String LEGACY_EXACT_VERSION = "merchant-villager-exact-v1";

    public static TradeCatalogueKey exact(
        RegistryWrapper.WrapperLookup registries, TradeOffer offer
    ) {
        var ops = registries.getOps(JsonOps.INSTANCE);
        String first = canonical(
            TradedItem.CODEC.encodeStart(ops, offer.getFirstBuyItem()).getOrThrow()
        );
        String second = offer.getSecondBuyItem()
            .map(input -> canonical(TradedItem.CODEC.encodeStart(ops, input).getOrThrow()))
            .orElse("-");
        String output = canonical(
            ItemStack.CODEC.encodeStart(ops, offer.getSellItem()).getOrThrow()
        );
        return recipeFromEncoded(first, second, output);
    }

    static TradeCatalogueKey exactFromEncoded(
        String first,
        String second,
        String output,
        int maxUses,
        int merchantExperience,
        float priceMultiplier,
        boolean rewardsPlayerExperience
    ) {
        return recipeFromEncoded(first, second, output);
    }

    /**
     * Player-facing approval identity. Material counts and every item
     * component remain significant, while hidden stock/XP bookkeeping does
     * not split two recipes that look and execute identically to the player.
     */
    private static TradeCatalogueKey recipeFromEncoded(
        String first, String second, String output
    ) {
        return new TradeCatalogueKey(
            TradeCatalogueKey.Kind.EXACT,
            sha256(String.join("\n", EXACT_VERSION, first, second, output))
        );
    }

    /** Previous RC identity, retained solely for one-way save migration. */
    public static TradeCatalogueKey legacyExact(
        RegistryWrapper.WrapperLookup registries, TradeOffer offer
    ) {
        var ops = registries.getOps(JsonOps.INSTANCE);
        String first = canonical(
            TradedItem.CODEC.encodeStart(ops, offer.getFirstBuyItem()).getOrThrow()
        );
        String second = offer.getSecondBuyItem()
            .map(input -> canonical(TradedItem.CODEC.encodeStart(ops, input).getOrThrow()))
            .orElse("-");
        String output = canonical(
            ItemStack.CODEC.encodeStart(ops, offer.getSellItem()).getOrThrow()
        );
        return legacyExactFromEncoded(first, second, output, offer);
    }

    private static TradeCatalogueKey legacyExactFromEncoded(
        String first, String second, String output, TradeOffer offer
    ) {
        List<String> parts = new ArrayList<>();
        parts.add(LEGACY_EXACT_VERSION);
        parts.add(first);
        parts.add(second);
        parts.add(output);
        parts.add(Integer.toString(offer.getMaxUses()));
        parts.add(Integer.toString(offer.getMerchantExperience()));
        parts.add(Float.toHexString(offer.getPriceMultiplier()));
        parts.add(Boolean.toString(offer.shouldRewardPlayerExperience()));
        return new TradeCatalogueKey(
            TradeCatalogueKey.Kind.EXACT,
            sha256(String.join("\n", parts))
        );
    }

    /**
     * Stable approval identity for explorer maps. Map ids and destination
     * decorations are allocated from world state, while the translated map
     * type/name and every material cost remain approval-significant.
     */
    public static TradeCatalogueKey contextualMap(
        RegistryWrapper.WrapperLookup registries, TradeOffer offer
    ) {
        return contextualMap(registries, offer, false);
    }

    /** Previous RC contextual identity, retained solely for save migration. */
    public static TradeCatalogueKey legacyContextualMap(
        RegistryWrapper.WrapperLookup registries, TradeOffer offer
    ) {
        return contextualMap(registries, offer, true);
    }

    private static TradeCatalogueKey contextualMap(
        RegistryWrapper.WrapperLookup registries, TradeOffer offer, boolean legacy
    ) {
        var ops = registries.getOps(JsonOps.INSTANCE);
        ItemStack output = offer.copySellItem();
        output.remove(DataComponentTypes.MAP_ID);
        output.remove(DataComponentTypes.MAP_DECORATIONS);
        output.remove(DataComponentTypes.MAP_COLOR);
        List<String> parts = new ArrayList<>();
        parts.add(legacy
            ? "merchant-villager-context-map-v1"
            : "merchant-villager-context-map-v2");
        parts.add(canonical(TradedItem.CODEC.encodeStart(ops, offer.getFirstBuyItem()).getOrThrow()));
        parts.add(offer.getSecondBuyItem()
            .map(input -> canonical(TradedItem.CODEC.encodeStart(ops, input).getOrThrow()))
            .orElse("-"));
        parts.add(canonical(ItemStack.CODEC.encodeStart(ops, output).getOrThrow()));
        if (legacy) {
            parts.add(Integer.toString(offer.getMaxUses()));
            parts.add(Integer.toString(offer.getMerchantExperience()));
            parts.add(Float.toHexString(offer.getPriceMultiplier()));
            parts.add(Boolean.toString(offer.shouldRewardPlayerExperience()));
        }
        return new TradeCatalogueKey(
            TradeCatalogueKey.Kind.CONTEXTUAL,
            sha256(String.join("\n", parts))
        );
    }

    static String canonical(JsonElement element) {
        StringBuilder result = new StringBuilder();
        appendCanonical(result, element);
        return result.toString();
    }

    private static void appendCanonical(StringBuilder result, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            result.append("null");
        } else if (element.isJsonPrimitive()) {
            result.append(element);
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            result.append('[');
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                appendCanonical(result, array.get(index));
            }
            result.append(']');
        } else {
            JsonObject object = element.getAsJsonObject();
            List<String> keys = object.keySet().stream().sorted().toList();
            result.append('{');
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                String key = keys.get(index);
                result.append(new JsonPrimitive(key)).append(':');
                appendCanonical(result, object.get(key));
            }
            result.append('}');
        }
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private TradeSignatures() {
    }
}
