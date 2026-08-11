package com.fluffybacon.merchantvillager.trade;

import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

/** Immutable, display-oriented copy of a materialized trade offer. */
public record TradeTemplate(
    TradedItem firstInput,
    Optional<TradedItem> secondInput,
    ItemStack output,
    int maxUses,
    int merchantExperience,
    float priceMultiplier,
    boolean rewardsPlayerExperience
) {
    public TradeTemplate {
        secondInput = secondInput == null ? Optional.empty() : secondInput;
        output = output.copy();
    }

    public static TradeTemplate from(TradeOffer offer) {
        return new TradeTemplate(
            offer.getFirstBuyItem(),
            offer.getSecondBuyItem(),
            offer.copySellItem(),
            offer.getMaxUses(),
            offer.getMerchantExperience(),
            offer.getPriceMultiplier(),
            offer.shouldRewardPlayerExperience()
        );
    }

    @Override
    public ItemStack output() {
        return output.copy();
    }
}
