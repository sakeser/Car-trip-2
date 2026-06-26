package com.cartrip.analyzer.record

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRecordPolicyTest {

    private val on = AutoRecordPolicy.Config(enabled = true)               // requireCharging=true default
    private val off = AutoRecordPolicy.Config(enabled = false)

    @Test fun disabledNeverTriggers() {
        assertFalse(AutoRecordPolicy.triggerPresent(off, charging = true, wireless = true, carBtConnected = true))
        assertFalse(AutoRecordPolicy.shouldArm(off, recording = false, charging = true, wireless = true, carBtConnected = true))
    }

    @Test fun chargingIsTheInCarTrigger() {
        assertTrue(AutoRecordPolicy.triggerPresent(on, charging = true, wireless = false, carBtConnected = false))
        assertFalse(AutoRecordPolicy.triggerPresent(on, charging = false, wireless = false, carBtConnected = false))
    }

    @Test fun armsOnlyWhenNotAlreadyRecording() {
        assertTrue(AutoRecordPolicy.shouldArm(on, recording = false, charging = true, wireless = false, carBtConnected = false))
        assertFalse(AutoRecordPolicy.shouldArm(on, recording = true, charging = true, wireless = false, carBtConnected = false))
    }

    @Test fun stopsWhenTriggerDropsDuringRecording() {
        assertTrue(AutoRecordPolicy.shouldStop(on, recording = true, charging = false, wireless = false, carBtConnected = false))
        // still charging → keep recording
        assertFalse(AutoRecordPolicy.shouldStop(on, recording = true, charging = true, wireless = false, carBtConnected = false))
        // not recording → nothing to stop
        assertFalse(AutoRecordPolicy.shouldStop(on, recording = false, charging = false, wireless = false, carBtConnected = false))
    }

    @Test fun requireWirelessRejectsWiredCharging() {
        val cfg = AutoRecordPolicy.Config(enabled = true, requireWireless = true)
        assertFalse(AutoRecordPolicy.triggerPresent(cfg, charging = true, wireless = false, carBtConnected = false))
        assertTrue(AutoRecordPolicy.triggerPresent(cfg, charging = true, wireless = true, carBtConnected = false))
    }

    @Test fun bluetoothIsAnAlternateTrigger() {
        // With Bluetooth enabled, the car's BT arms recording on its own (not just while charging).
        val cfg = AutoRecordPolicy.Config(enabled = true, useBluetooth = true)
        assertTrue(AutoRecordPolicy.triggerPresent(cfg, charging = false, wireless = false, carBtConnected = true))
        // but only the car's BT (matched by address upstream) counts
        assertFalse(AutoRecordPolicy.triggerPresent(cfg, charging = false, wireless = false, carBtConnected = false))
        // charging still triggers too
        assertTrue(AutoRecordPolicy.triggerPresent(cfg, charging = true, wireless = false, carBtConnected = false))
    }

    @Test fun bluetoothIgnoredWhenNotEnabled() {
        // useBluetooth off → a BT connection is not a trigger; only charging is.
        val cfg = AutoRecordPolicy.Config(enabled = true)
        assertFalse(AutoRecordPolicy.triggerPresent(cfg, charging = false, wireless = false, carBtConnected = true))
        assertTrue(AutoRecordPolicy.triggerPresent(cfg, charging = true, wireless = false, carBtConnected = true))
    }

    @Test fun broadcastEdgeOverridesStaleSticky() {
        // Field-observed P1 bug: the sticky ACTION_BATTERY_CHANGED read lags the power broadcast and
        // reports inverted, so a real plug-in logged "charger-on -> chg=false" and did nothing.
        // The connect edge must win over a stale "not charging" sticky...
        assertTrue(AutoRecordPolicy.effectiveCharging(broadcastEdge = true, stickyCharging = false))
        // ...and the disconnect edge must win over a stale "charging" sticky.
        assertFalse(AutoRecordPolicy.effectiveCharging(broadcastEdge = false, stickyCharging = true))
    }

    @Test fun noEdgeFallsBackToSticky() {
        // CDM presence / watcher-start paths have no power edge; they trust the sticky read.
        assertTrue(AutoRecordPolicy.effectiveCharging(broadcastEdge = null, stickyCharging = true))
        assertFalse(AutoRecordPolicy.effectiveCharging(broadcastEdge = null, stickyCharging = false))
    }

    @Test fun connectEdgeArmsEvenWhenStickyIsStale() {
        // End-to-end at the policy layer: a connect edge → charging=true → arms, regardless of the
        // momentarily-stale sticky that previously caused the missed trigger.
        val charging = AutoRecordPolicy.effectiveCharging(broadcastEdge = true, stickyCharging = false)
        assertTrue(AutoRecordPolicy.shouldArm(on, recording = false, charging = charging, wireless = false, carBtConnected = false))
    }
}
