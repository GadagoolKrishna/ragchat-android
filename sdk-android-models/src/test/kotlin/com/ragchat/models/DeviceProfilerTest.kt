package com.ragchat.models

import android.content.ContextWrapper
import com.ragchat.models.profiler.DeviceProfiler
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfilerTest {
    @Test
    fun testDeviceProfileProduction() {
        val context = ContextWrapper(null)
        val profiler = DeviceProfiler(context)

        val profile = profiler.profile()
        assertNotNull(profile)
        assertNotNull(profile.recommendedTier)
        assertTrue(profile.totalRamBytes >= 0L)
        assertTrue(profile.availableRamBytes >= 0L)
        assertTrue(profile.freeStorageBytes >= 0L)
    }
}
