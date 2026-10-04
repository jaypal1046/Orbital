package com.orbital.automation

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObstacleClearanceEngineTest {

    private lateinit var obstacleEngine: ObstacleClearanceEngine

    @Before
    fun setUp() {
        obstacleEngine = ObstacleClearanceEngine()
    }

    @Test
    fun `detectObstacle returns null on clean app screen`() {
        val snapshot = ScreenHierarchySnapshot(
            packageName = "com.orbital.demo",
            activityTitle = "Main Screen",
            elements = listOf(
                UIElement(
                    text = "Welcome",
                    contentDescription = null,
                    viewId = "tv_welcome",
                    className = "android.widget.TextView",
                    isClickable = false,
                    isEditable = false,
                    bounds = Rect(0, 0, 100, 50)
                ),
                UIElement(
                    text = "Search",
                    contentDescription = null,
                    viewId = "btn_search",
                    className = "android.widget.Button",
                    isClickable = true,
                    isEditable = false,
                    bounds = Rect(0, 50, 100, 100)
                )
            )
        )

        val obstacle = obstacleEngine.detectObstacle(snapshot)
        assertThat(obstacle).isNull()
    }

    @Test
    fun `detectObstacle identifies Android runtime permission dialog`() {
        val snapshot = ScreenHierarchySnapshot(
            packageName = "com.google.android.permissioncontroller",
            activityTitle = "Permission Request",
            elements = listOf(
                UIElement(
                    text = "Allow Orbital to access this device's location?",
                    contentDescription = null,
                    viewId = "permission_message",
                    className = "android.widget.TextView",
                    isClickable = false,
                    isEditable = false,
                    bounds = Rect(20, 100, 400, 200)
                ),
                UIElement(
                    text = "While using the app",
                    contentDescription = null,
                    viewId = "permission_allow_foreground_only_button",
                    className = "android.widget.Button",
                    isClickable = true,
                    isEditable = false,
                    bounds = Rect(50, 250, 350, 300)
                ),
                UIElement(
                    text = "Don't allow",
                    contentDescription = null,
                    viewId = "permission_deny_button",
                    className = "android.widget.Button",
                    isClickable = true,
                    isEditable = false,
                    bounds = Rect(50, 310, 350, 360)
                )
            )
        )

        val obstacle = obstacleEngine.detectObstacle(snapshot)
        assertThat(obstacle).isNotNull()
        assertThat(obstacle?.type).isEqualTo(ObstacleType.PERMISSION_DIALOG)
        assertThat(obstacle?.dismissActionLabel).isEqualTo("While using the app")
    }

    @Test
    fun `detectObstacle identifies promo modal with dismiss button`() {
        val snapshot = ScreenHierarchySnapshot(
            packageName = "com.some.shopping.app",
            activityTitle = "Special Offer",
            elements = listOf(
                UIElement(
                    text = "Get 50% Off Today!",
                    contentDescription = null,
                    viewId = "promo_title",
                    className = "android.widget.TextView",
                    isClickable = false,
                    isEditable = false,
                    bounds = Rect(20, 100, 400, 200)
                ),
                UIElement(
                    text = "Not now",
                    contentDescription = null,
                    viewId = "btn_dismiss",
                    className = "android.widget.Button",
                    isClickable = true,
                    isEditable = false,
                    bounds = Rect(50, 400, 200, 450)
                )
            )
        )

        val obstacle = obstacleEngine.detectObstacle(snapshot)
        assertThat(obstacle).isNotNull()
        assertThat(obstacle?.type).isEqualTo(ObstacleType.PROMO_MODAL)
        assertThat(obstacle?.dismissActionLabel).isEqualTo("Not now")
    }

    @Test
    fun `detectObstacle identifies close icon button`() {
        val snapshot = ScreenHierarchySnapshot(
            packageName = "com.some.app",
            activityTitle = "Rating Prompt",
            elements = listOf(
                UIElement(
                    text = "Rate our app",
                    contentDescription = null,
                    viewId = "dialog_title",
                    className = "android.widget.TextView",
                    isClickable = false,
                    isEditable = false,
                    bounds = Rect(20, 100, 400, 200)
                ),
                UIElement(
                    text = "",
                    contentDescription = "Close dialog",
                    viewId = "btn_close",
                    className = "android.widget.ImageButton",
                    isClickable = true,
                    isEditable = false,
                    bounds = Rect(350, 50, 400, 100)
                )
            )
        )

        val obstacle = obstacleEngine.detectObstacle(snapshot)
        assertThat(obstacle).isNotNull()
        assertThat(obstacle?.dismissActionLabel).isEqualTo("Close dialog")
    }
}
