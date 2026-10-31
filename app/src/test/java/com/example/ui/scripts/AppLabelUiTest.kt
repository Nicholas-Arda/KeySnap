package com.example.ui.scripts

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.EditorField
import com.example.data.PrivilegedEditor
import com.example.data.ScriptAction
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import io.github.nicholasarda.keysnap.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-mdpi")
class AppLabelUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pkg = "com.akakce.akakce"

    private fun install() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = "Akakce"
            }
        })
    }

    @Test fun selectedAppShowsItsNameAndManualPackageFieldOpensOnlyOnRequest() {
        install()
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxWidth().height(800.dp)) {
                    AppFieldRow(
                        EditorField.AppPick("packageName", R.string.param_app),
                        mutableStateMapOf("packageName" to pkg),
                        {},
                    )
                }
            }
        }
        rule.onNodeWithText("Akakce").assertIsDisplayed()
        rule.onNodeWithText(pkg).assertDoesNotExist()
        rule.onNodeWithText("Not installed").assertDoesNotExist()
        rule.onNodeWithTag("config_field_packageName").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.action_config_enter_package)).performClick()
        rule.onNodeWithTag("config_field_packageName").assertIsDisplayed()
    }

    @Test fun missingSelectedAppShowsThePackageAndSubtleHint() {
        val missingPkg = "$pkg.missing"
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxWidth().height(800.dp)) {
                    AppFieldRow(
                        EditorField.AppPick("packageName", R.string.param_app),
                        mutableStateMapOf("packageName" to missingPkg),
                        {},
                    )
                }
            }
        }
        rule.onNodeWithText(missingPkg).assertIsDisplayed()
        rule.onNodeWithText("Not installed").assertIsDisplayed()
    }

    @Test fun intentEditorShowsAppNameAndKeepsLiteralCommand() {
        install()
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                PrivilegedEditorPage(
                    PrivilegedEditor.INTENT,
                    mapOf("package" to pkg, "action" to "example.ACTION", "target" to "broadcast"),
                    false, emptyList(), {}, {},
                )
            }
        }
        rule.onNodeWithText("Akakce").assertIsDisplayed()
        rule.onNodeWithText("am broadcast -a example.ACTION -p $pkg", substring = true).assertExists()
        rule.onNodeWithText(pkg).assertDoesNotExist()
    }

    @Test fun reviewShowsAppLabelAlongsideTheLiteralIntentCommand() {
        install()
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                ShortcutReviewDialog(
                    title = "Review", name = "Open app", automation = false,
                    actions = listOf(ScriptAction("send_intent", mapOf("package" to pkg, "action" to "example.ACTION"))),
                    note = "Review before importing", confirmLabel = "Continue", onConfirm = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("App: Akakce ($pkg)").assertIsDisplayed()
        rule.onNodeWithText("am broadcast -a example.ACTION -p $pkg").assertIsDisplayed()
        rule.onNodeWithText("App: $pkg").assertDoesNotExist()
    }
}
