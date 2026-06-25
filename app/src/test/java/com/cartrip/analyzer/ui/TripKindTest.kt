package com.cartrip.analyzer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripKindTest {
    @Test fun walkSpeedsAreNonDrive() {
        assertTrue(TripKind.isLikelyNonDrive(5.0))   // typical walk
        assertTrue(TripKind.isLikelyNonDrive(11.9))  // just under the threshold
    }

    @Test fun driveSpeedsAreDrive() {
        assertFalse(TripKind.isLikelyNonDrive(12.1))
        assertFalse(TripKind.isLikelyNonDrive(40.0))
    }

    @Test fun zeroSpeedTreatedAsDrive() {
        // No GPS / unknown top speed -> don't mislabel as a walk.
        assertFalse(TripKind.isLikelyNonDrive(0.0))
    }
}
