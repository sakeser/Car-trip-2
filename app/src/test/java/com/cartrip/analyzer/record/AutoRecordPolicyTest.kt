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
}
