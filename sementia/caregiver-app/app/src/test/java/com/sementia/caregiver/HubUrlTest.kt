package com.sementia.caregiver

import com.sementia.caregiver.data.remote.normalizeHubBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class HubUrlTest {

    @Test
    fun emulatorHostGetsPort8000() {
        assertEquals("http://10.0.2.2:8000", normalizeHubBaseUrl("10.0.2.2"))
    }

    @Test
    fun preservesExplicitPort() {
        assertEquals("http://192.168.1.5:8001", normalizeHubBaseUrl("http://192.168.1.5:8001"))
    }

    @Test
    fun httpsDefaultPortUnchanged() {
        assertEquals("https://example.com", normalizeHubBaseUrl("https://example.com"))
    }
}
