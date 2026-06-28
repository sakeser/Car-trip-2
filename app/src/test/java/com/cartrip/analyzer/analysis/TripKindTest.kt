package com.cartrip.analyzer.analysis

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

    @Test fun manualOverrideForcesDrive() {
        // A slow crawl the heuristic calls a non-drive, but the owner says it's a drive.
        assertFalse(TripKind.isLikelyNonDrive(5.0, userIsDrive = true))
    }

    @Test fun manualOverrideForcesNonDrive() {
        // A fast reading the heuristic calls a drive, but the owner says it was a walk (e.g. on a train).
        assertTrue(TripKind.isLikelyNonDrive(40.0, userIsDrive = false))
    }

    @Test fun nullOverrideFallsBackToAuto() {
        assertTrue(TripKind.isLikelyNonDrive(5.0, userIsDrive = null))
        assertFalse(TripKind.isLikelyNonDrive(40.0, userIsDrive = null))
    }
}
