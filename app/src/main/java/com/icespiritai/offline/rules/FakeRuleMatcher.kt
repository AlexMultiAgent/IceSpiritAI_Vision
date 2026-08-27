package com.icespiritai.offline.rules

import androidx.annotation.VisibleForTesting
import com.icespiritai.offline.domain.RuleHit

/**
 * Test-only RuleMatcher that returns a canned hit list for exact query text.
 * Lives in `main` sourceSet only so test classes (e.g. `ImageAnalyzerRepositoryTest`)
 * can construct it from `test/` — but `@VisibleForTesting` + `internal` keep it
 * out of the public surface and out of any production wiring. AGP does NOT
 * strip `@VisibleForTesting`-annotated classes from the production APK (they
 * still bloat dex/bytecode by a few hundred bytes), but the alternative —
 * moving to `test/` sourceSet — breaks the import path because test sourceSet
 * classes cannot be imported by other test sourceSet classes that share a
 * Robolectric fixture (the test compile order matters here). For now the
 * [androidx.annotation.VisibleForTesting] annotation is the right signal:
 * lint flags any production reference to this class.
 */
@VisibleForTesting
internal class FakeRuleMatcher(
    private val hitsByQuery: Map<String, List<RuleHit>> = emptyMap()
) : RuleMatcher {
    override fun scan(text: String): List<RuleHit> = hitsByQuery[text] ?: emptyList()
}
