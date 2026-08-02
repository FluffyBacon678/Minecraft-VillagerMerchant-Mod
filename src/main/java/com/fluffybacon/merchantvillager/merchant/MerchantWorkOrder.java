package com.fluffybacon.merchantvillager.merchant;

import java.util.List;
import java.util.UUID;

public record MerchantWorkOrder(
    UUID targetUuid,
    List<MerchantTradePlan> trades
) {
    public MerchantWorkOrder {
        trades = List.copyOf(trades);
    }

    public int totalExecutions() {
        return trades.stream().mapToInt(MerchantTradePlan::executions).sum();
    }
}
