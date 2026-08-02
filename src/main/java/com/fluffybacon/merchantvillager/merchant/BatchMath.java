package com.fluffybacon.merchantvillager.merchant;

public final class BatchMath {
    public static int executions(
        int firstAvailable,
        int firstCost,
        int secondAvailable,
        int secondCost,
        int remainingUses,
        int cargoInputCapacity,
        int cargoOutputCapacity
    ) {
        if (firstCost <= 0 || remainingUses <= 0) {
            return 0;
        }
        int result = firstAvailable / firstCost;
        if (secondCost > 0) {
            result = Math.min(result, secondAvailable / secondCost);
        }
        return Math.max(0, Math.min(
            Math.min(result, remainingUses),
            Math.min(cargoInputCapacity, cargoOutputCapacity)
        ));
    }

    public static int unused(int reservedCount, int unitCost, int completedExecutions) {
        return Math.max(0, reservedCount - unitCost * completedExecutions);
    }

    private BatchMath() {
    }
}
