package com.example.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The policy shown in the app and the policy published for Play must say the same thing. Google
 * requires a hosted URL, so the text necessarily exists twice; this is what stops the two copies
 * from drifting apart the next time either one is edited.
 */
class PrivacyPolicyTest {

    private val published: String by lazy {
        val file = File("../docs/privacy-policy.md")
        assertTrue("Missing ${file.absolutePath}", file.isFile)
        file.readText()
    }

    @Test
    fun publishedPolicyContainsEverySectionShownInTheApp() {
        PrivacyPolicy.sections.forEach { (heading, body) ->
            assertTrue("docs/privacy-policy.md is missing the heading \"$heading\"", published.contains(heading))
            assertTrue("docs/privacy-policy.md text differs under \"$heading\"", published.contains(body))
        }
    }

    @Test
    fun publishedPolicyCarriesTheSameDate() {
        assertTrue(
            "docs/privacy-policy.md does not state ${PrivacyPolicy.LAST_UPDATED}",
            published.contains(PrivacyPolicy.LAST_UPDATED),
        )
    }
}
