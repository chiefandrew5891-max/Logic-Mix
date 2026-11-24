package com.akynex.logicmix.core.review

import android.app.Activity
import com.google.android.play.core.review.testing.FakeReviewManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayReviewLauncherTest {
    @Test fun fakeReviewManagerCompletesTheFlowWithoutError() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var result: Throwable? = IllegalStateException("not finished")
        var called = false
        PlayReviewLauncher(FakeReviewManager(RuntimeEnvironment.getApplication())).launch(activity) { result = it; called = true }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(called)
        assertEquals(null, result)
    }

    @Test fun storeLinksUseTheApplicationId() {
        assertEquals("market://details?id=com.akynex.logicmix", PlayStoreLinks.market("com.akynex.logicmix"))
        assertEquals("https://play.google.com/store/apps/details?id=com.akynex.logicmix", PlayStoreLinks.web("com.akynex.logicmix"))
    }

    @Test fun openerFallsBackToTheBrowserThenToNothing() {
        val ctx = RuntimeEnvironment.getApplication()
        // Robolectric has no handler for market:// or https by default: nothing opens, and nothing throws.
        assertEquals(null, PlayStoreOpener.open(ctx, "com.akynex.logicmix"))
    }
}
