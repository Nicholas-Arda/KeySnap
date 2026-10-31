package com.example.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class AppLabelResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resources = context.resources
    private val pkg = "com.akakce.akakce"

    private fun install(label: String = "Akakce") {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
            }
        })
    }

    @Test fun installedAppUsesApplicationLabelEvenWithoutALauncherActivity() {
        install()
        val result = AppLabelResolver(context.packageManager).resolve(pkg, resources)
        assertTrue(result.installed)
        assertEquals("Akakce", result.displayName(resources))
    }

    @Test fun missingAppShowsPackageAndHint() {
        val result = AppLabelResolver(context.packageManager).resolve(pkg, resources)
        assertFalse(result.installed)
        assertEquals("$pkg · Not installed", result.displayName(resources))
    }

    @Test fun installedLabelIsCachedAndRefreshesAfterAnUpdate() {
        install()
        var now = 0L
        val resolver = AppLabelResolver(context.packageManager) { now }
        assertEquals("Akakce", resolver.displayName(pkg, resources))
        install("New name")
        assertEquals("Akakce", resolver.displayName(pkg, resources))
        now = 60_000L
        assertEquals("New name", resolver.displayName(pkg, resources))
    }

    @Test fun missingResultIsCachedAndRefreshesAfterInstall() {
        var now = 0L
        val resolver = AppLabelResolver(context.packageManager) { now }
        assertFalse(resolver.resolve(pkg, resources).installed)
        install()
        assertFalse(resolver.resolve(pkg, resources).installed)
        now = 60_000L
        assertEquals("Akakce", resolver.displayName(pkg, resources))
    }

    @Test fun emptyOptionalAppHasNoMissingHint() {
        assertEquals("", AppLabelResolver(context.packageManager).displayName("", resources))
    }

    @Test fun actionSummariesResolvePackagesWithoutRewritingPayloadsOrStoredParams() {
        install()
        listOf("launch_app", "force_stop_app", "reply_to_notification", "send_message", "show_notification", "send_intent").forEach { type ->
            val key = if (type == "send_intent") "package" else "packageName"
            val params = mapOf(key to pkg, "onTap" to "app", "action" to "example.ACTION", "text" to pkg, "title" to pkg)
            val action = ScriptAction(type, params)
            val summary = ActionEditorForms.summarize(action, context).orEmpty()
            assertTrue("$type: $summary", summary.contains("Akakce"))
            assertEquals(pkg, action.params[key])
            if (type in listOf("reply_to_notification", "send_message", "show_notification")) {
                assertTrue("Literal message must remain intact", summary.contains(pkg))
            }
        }
    }

    @Test fun foregroundAndNotificationConditionsResolveAppAndPreserveOtherText() {
        install()
        listOf("app_in_foreground", "app_not_in_foreground", "notification_received").forEach { type ->
            val constraint = ScriptConstraint(type, mapOf("packageName" to pkg, "contains" to pkg))
            val summary = ConstraintEditorForms.summarize(constraint, context).orEmpty()
            assertTrue("$type: $summary", summary.startsWith("Akakce"))
            assertEquals(pkg, constraint.params["packageName"])
            if (type == "notification_received") assertTrue(summary.contains(pkg))
        }
        assertEquals("$pkg.missing · Not installed", ActionEditorForms.summarize(
            ScriptAction("launch_app", mapOf("packageName" to "$pkg.missing")), context,
        ))
        assertEquals("$pkg.missing · Not installed", ConstraintEditorForms.summarize(
            ScriptConstraint("app_not_in_foreground", mapOf("packageName" to "$pkg.missing")), context,
        ))
    }
}
