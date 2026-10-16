package com.icespiritai.offline.rules

import androidx.annotation.VisibleForTesting
import com.icespiritai.offline.domain.RuleHit

/**
 * Test-only RuleMatcher that returns a canned hit list for exact query text.
 *
 * Lives in the **test** sourceSet (`app/src/test/java/...`) — it used to sit in
 * `main` under the (incorrect) belief that test-sourceSet classes cannot import
 * each other. They can: every file under `app/src/test/java` is compiled as one
 * Kotlin module, which is exactly how `FakeOcrEngine` is shared by the tests
 * that consume it. Keeping it out of `main` means the release dex carries no
 * test scaffolding.
 *
 * [VisibleForTesting] is retained as the signal to readers (and lint) that this
 * is a stub, not a production matcher; `internal` keeps it off any other
 * module's compile surface.
 */
@VisibleForTesting
internal class FakeRuleMatcher(
    private val hitsByQuery: Map<String, List<RuleHit>> = emptyMap()
) : RuleMatcher {
    override fun scan(text: String): List<RuleHit> = hitsByQuery[text] ?: emptyList()
}
