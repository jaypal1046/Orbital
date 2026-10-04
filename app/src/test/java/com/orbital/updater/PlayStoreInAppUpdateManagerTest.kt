package com.orbital.updater

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayStoreInAppUpdateManagerTest {

    private lateinit var context: Context
    private lateinit var updateManager: PlayStoreInAppUpdateManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        updateManager = PlayStoreInAppUpdateManager(context)
    }

    @Test
    fun `initial update status is IDLE`() {
        assertThat(updateManager.updateStatus.value).isEqualTo(InAppUpdateStatus.IDLE)
    }

    @Test
    fun `unregister listener does not crash`() {
        updateManager.unregister()
    }
}
