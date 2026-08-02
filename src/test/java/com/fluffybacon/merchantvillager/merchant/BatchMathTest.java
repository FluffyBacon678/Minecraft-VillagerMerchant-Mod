package com.fluffybacon.merchantvillager.merchant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BatchMathTest {
    @Test void oneInputRepeatedTrades() {
        assertEquals(8, BatchMath.executions(192, 24, 0, 0, 12, 9, 9));
    }

    @Test void twoInputRepeatedTrades() {
        assertEquals(4, BatchMath.executions(48, 12, 8, 1, 12, 9, 9));
    }

    @Test void offerUseLimitCapsPlan() {
        assertEquals(2, BatchMath.executions(192, 24, 0, 0, 2, 9, 9));
    }

    @Test void cargoCapacityCapsPlan() {
        assertEquals(3, BatchMath.executions(192, 24, 0, 0, 12, 3, 9));
    }

    @Test void outputCapacityCapsPlan() {
        assertEquals(2, BatchMath.executions(192, 24, 0, 0, 12, 9, 2));
    }

    @Test void missingSecondInputStopsPlan() {
        assertEquals(0, BatchMath.executions(192, 24, 0, 1, 12, 9, 9));
    }

    @Test void unusedInputIsCalculated() {
        assertEquals(48, BatchMath.unused(192, 24, 6));
    }

    @Test void priceIncreaseReducesExecutions() {
        assertEquals(6, BatchMath.executions(192, 30, 0, 0, 12, 9, 9));
    }
}
