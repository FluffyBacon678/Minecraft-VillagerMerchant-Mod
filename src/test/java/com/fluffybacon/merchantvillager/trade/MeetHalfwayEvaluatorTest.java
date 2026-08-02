package com.fluffybacon.merchantvillager.trade;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MeetHalfwayEvaluatorTest {
    @Test void distance54IsNormal() {
        assertEquals(MeetHalfwayEvaluator.Range.NORMAL, MeetHalfwayEvaluator.classify(54));
    }

    @Test void distance55IsNormal() {
        assertEquals(MeetHalfwayEvaluator.Range.NORMAL, MeetHalfwayEvaluator.classify(55));
    }

    @Test void justBeyond55NeedsObservation() {
        assertEquals(MeetHalfwayEvaluator.Range.OBSERVE, MeetHalfwayEvaluator.classify(55.01));
    }

    @Test void distance60NeedsObservation() {
        assertEquals(MeetHalfwayEvaluator.Range.OBSERVE, MeetHalfwayEvaluator.classify(60));
    }

    @Test void distance66NeedsObservation() {
        assertEquals(MeetHalfwayEvaluator.Range.OBSERVE, MeetHalfwayEvaluator.classify(66));
    }

    @Test void beyond66IsRejected() {
        assertEquals(MeetHalfwayEvaluator.Range.REJECT, MeetHalfwayEvaluator.classify(66.01));
    }

    @Test void meaningfulApproachIsAccepted() {
        assertTrue(MeetHalfwayEvaluator.converging(60, 58.8, 0.1));
    }

    @Test void sidewaysMovementIsRejected() {
        assertFalse(MeetHalfwayEvaluator.converging(60, 58.8, 0.0));
    }

    @Test void movementAwayIsRejected() {
        assertFalse(MeetHalfwayEvaluator.converging(60, 61, -0.1));
    }

    @Test void floatingPointNoiseIsRejected() {
        assertFalse(MeetHalfwayEvaluator.converging(60, 59.99, 0.1));
    }
}
