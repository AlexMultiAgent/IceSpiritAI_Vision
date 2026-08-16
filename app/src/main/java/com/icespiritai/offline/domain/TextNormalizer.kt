package com.icespiritai.offline.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes OCR output and rule keywords before Aho-Corasick matching so the
 * same advertising phrase is recognized across the variations real photos
 * introduce:
 *
 *  - full-width digits / percent sign (`１００％`) → ASCII (`100%`)
 *  - whitespace differences (`100% 有效` vs `100%有效`, full-width spaces)
 *  - a line break inside a phrase (`100%\n有效`)
 *  - Latin case (`FDA` vs `fda`)
 *
 * The original text is kept untouched for display/export; normalization only
 * feeds the matcher (and the highlight-overlay containment check).
 */
object TextNormalizer {
    fun forMatching(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filterNot { it.isWhitespace() }
}
