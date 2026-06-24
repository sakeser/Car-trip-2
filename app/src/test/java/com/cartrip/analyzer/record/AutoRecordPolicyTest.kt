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

    @Test fun bluetoothCanTriggerWhenChargingNotRequired() {
        val cfg = AutoRecordPolicy.Config(enabled = true, requireCharging = false, useBluetooth = true)
        assertTrue(AutoRecordPolicy.triggerPresent(cfg, charging = false, wireless = false, carBtConnected = true))
        // but the car's BT must be the connected one
        assertFalse(AutoRecordPolicy.triggerPresent(cfg, charging = false, wireless = false, carBtConnected = false))
    }

    @Test fun bluetoothIgnoredWhenChargingRequired() {
        // requireCharging defaults true → BT alone is not enough
        val cfg = AutoRecordPolicy.Config(enabled = true, useBluetooth = true)
        assertFalse(AutoRecordPolicy.triggerPresent(cfg, charging = false, wireless = false, carBtConnected = true))
        assertTrue(AutoRecordPolicy.triggerPresent(cfg, charging = true, wireless = false, carBtConnected = true))
    }
}
