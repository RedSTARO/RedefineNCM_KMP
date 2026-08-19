package com.leejlredstar.redefinencm.kmp.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioOutputDevicesTest {

    private val speakers = AudioOutputDevice(
        id = audioOutputDeviceId("Speakers (Realtek(R) Audio)", "Unknown Vendor"),
        displayName = "Speakers (Realtek(R) Audio)",
    )
    private val headset = AudioOutputDevice(
        id = audioOutputDeviceId("Headset (WH-1000XM4)", "Unknown Vendor"),
        displayName = "Headset (WH-1000XM4)",
    )

    @Test
    fun noStoredChoiceMeansTheSystemDefault() {
        assertNull(resolveAudioOutputSelection(SYSTEM_DEFAULT_AUDIO_OUTPUT_ID, listOf(speakers)))
        assertNull(resolveAudioOutputSelection("   ", listOf(speakers)))
    }

    @Test
    fun storedChoiceSurvivesReorderedEnumeration() {
        // Plugging a device in reshuffles AudioSystem.getMixerInfo(); an index would retarget.
        assertEquals(speakers, resolveAudioOutputSelection(speakers.id, listOf(speakers, headset)))
        assertEquals(speakers, resolveAudioOutputSelection(speakers.id, listOf(headset, speakers)))
    }

    @Test
    fun vendorOnlyMismatchStillPointsAtTheSameDevice() {
        val renamedVendor = AudioOutputDevice(
            id = audioOutputDeviceId("Speakers (Realtek(R) Audio)", "Realtek"),
            displayName = "Speakers (Realtek(R) Audio)",
        )

        assertEquals(renamedVendor, resolveAudioOutputSelection(speakers.id, listOf(renamedVendor)))
    }

    @Test
    fun unpluggedDeviceFallsBackToTheSystemDefault() {
        assertNull(resolveAudioOutputSelection(headset.id, listOf(speakers)))
        assertNull(resolveAudioOutputSelection(headset.id, emptyList()))
    }

    @Test
    fun deviceIdRoundTripsItsNameEvenWithoutAVendor() {
        val vendorless = audioOutputDeviceId("Primary Sound Driver", "")

        assertEquals("Primary Sound Driver", vendorless)
        assertEquals("Primary Sound Driver", audioOutputDeviceName(vendorless))
        assertEquals("Speakers (Realtek(R) Audio)", audioOutputDeviceName(speakers.id))
    }
}
