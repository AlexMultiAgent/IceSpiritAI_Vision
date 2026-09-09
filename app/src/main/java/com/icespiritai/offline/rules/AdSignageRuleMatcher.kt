package com.icespiritai.offline.rules

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie
import com.icespiritai.offline.domain.CategoryDisplay
import com.icespiritai.offline.domain.RuleHit
import com.icespiritai.offline.domain.TextNormalizer
import java.util.TreeMap

/**
 * 广告招牌 domain matcher. Subclasses [AhoCorasickMatcher] to inherit
 * Phase 1/2/2.5 keyword scan + dedup, then layers the domain's
 * three-gate filter on top:
 *
 *  - **sourceMarkers** (absence-gate): rule fires iff the scanned
 *    text does NOT contain any of the rule's source markers. Use
 *    case: data-citation rules (ad_signage_restricted_food_function,
 *    ad_signage_restricted_education_ranking) — the claim is legal
 *    if and only if a proper citation is given, so the rule should
 *    NOT fire when "据某" / "数据来源" is present.
 *  - **categoryAnchors** (presence-gate): rule fires iff the scanned
 *    text DOES contain at least one of the rule's anchors. Use case:
 *    domain-specific rules (pesticide / veterinary / medical /
 *    cosmetic / minor) whose keywords may overlap with general
 *    categories (e.g.「不如」「按摩」「美白」「儿童」) and would
 *    otherwise fire on non-domain ads. The anchor gate scopes the
 *    rule to its real domain.
 *  - **categoryAnchorsAbsent** (inverse-polarity presence-gate):
 *    rule fires iff the scanned text contains NONE of the rule's
 *    absent-anchors. Use case: rules whose legal target is
 *    "non-medical-institution ads advertising disease treatment"
 *    (广告法 §17). The keywords are chronic disease names + TCM
 *    medical terminology — those substrings are ALSO legitimately
 *    used by registered medical institutions (hospital / clinic /
 *    OTC product) to describe their own indications. The absent
 *    gate scopes it to the illegal-target subset.
 *
 *  Gate composition is AND across all three (Bug 2 fix 2026-09-10): a
 *  rule with BOTH categoryAnchors AND categoryAnchorsAbsent populated
 *  must pass both the positive-anchor presence test AND the
 *  absent-anchor suppression test. Pre-fix this used a Kotlin `when`
 *  whose first matching arm short-circuited the rest, so a rule
 *  declaring both gates would only be evaluated against
 *  categoryAnchors and the categoryAnchorsAbsent suppression was
 *  silently skipped. The fix rewrites the gate as three independent
 *  boolean terms AND-ed together; each gate's empty list evaluates
 *  to its no-op branch (`true` for required-presence, `true` for
 *  forbidden-presence) so single-gate rules keep their old
 *  byte-equivalent behavior. The 3 production rules currently
 *  declaring both gates are ad_signage_pesticide_art7_suggestive,
 *  ad_signage_pesticide_art8_pseudoscience,
 *  ad_signage_re_art18_hukou_education — pin this regression so a
 *  future refactor doesn't reintroduce the `when` short-circuit.
 */
class AdSignageRuleMatcher(rules: List<AdSignageRule>) : AhoCorasickMatcher<AdSignageRule>(rules) {

    private val sourceMarkerTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val anchorTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val anchorAbsentTrie = AhoCorasickDoubleArrayTrie<List<String>>()
    private val hasSourceMarkerTrie: Boolean
    private val hasAnchorTrie: Boolean
    private val hasAnchorAbsentTrie: Boolean
    private val hasAnyAbsenceRule: Boolean

    init {
        // Mirrors the keyword-pass variant generation in the base class:
        // sourceMarkers + categoryAnchors + categoryAnchorsAbsent are
        // NOT auto-decomposed into 1-char-deletion variants (they are
        // short official terms like "农药" / "兽药" / "据某" — variant
        // generation would expand them into adjacent substrings that
        // lose semantic specificity, e.g. "农药" → "农" / "药" neither
        // of which is domain-specific). Exact substring match only.
        val sourceMarkerToRuleIds = TreeMap<String, List<String>>()
        val anchorToRuleIds = TreeMap<String, List<String>>()
        val anchorAbsentToRuleIds = TreeMap<String, List<String>>()
        for (rule in rules) {
            for (sm in rule.sourceMarkers) {
                val key = TextNormalizer.forMatching(sm)
                if (key.isNotEmpty()) {
                    sourceMarkerToRuleIds[key] = (sourceMarkerToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
            for (anchor in rule.categoryAnchors) {
                val key = TextNormalizer.forMatching(anchor)
                if (key.isNotEmpty()) {
                    anchorToRuleIds[key] = (anchorToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
            for (anchor in rule.categoryAnchorsAbsent) {
                val key = TextNormalizer.forMatching(anchor)
                if (key.isNotEmpty()) {
                    anchorAbsentToRuleIds[key] =
                        (anchorAbsentToRuleIds[key] ?: emptyList()) + rule.id
                }
            }
        }
        hasSourceMarkerTrie = sourceMarkerToRuleIds.isNotEmpty()
        hasAnchorTrie = anchorToRuleIds.isNotEmpty()
        hasAnchorAbsentTrie = anchorAbsentToRuleIds.isNotEmpty()
        if (hasSourceMarkerTrie) sourceMarkerTrie.build(sourceMarkerToRuleIds)
        if (hasAnchorTrie) anchorTrie.build(anchorToRuleIds)
        if (hasAnchorAbsentTrie) anchorAbsentTrie.build(anchorAbsentToRuleIds)
        // Absence composite logic only runs when at least one rule declared
        // sourceMarkers. 117 existing rules all default to sourceMarkers =
        // emptyList(), so the legacy single-pass code path stays byte-equivalent.
        hasAnyAbsenceRule = rules.any { it.sourceMarkers.isNotEmpty() }
    }

    override fun scan(text: String): List<RuleHit> {
        if (text.isEmpty()) return emptyList()
        val normalized = TextNormalizer.forMatching(text)
        if (normalized.isEmpty()) return emptyList()

        // Source marker pass: only collect which rule ids had any source marker
        // hit. We don't materialize RuleHit for source matches — claim hits are
        // the user-visible output; source markers are just a gate.
        val sourceMarkerHitRules = mutableSetOf<String>()
        val anchorHitRules = mutableSetOf<String>()
        val anchorAbsentHitRules = mutableSetOf<String>()

        val sourceMarkerHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) sourceMarkerHitRules += ruleId
        }

        // Category-anchor pass: collect rule ids whose text contained at least
        // one anchor substring. Used by the gate at Phase 3 to drop hits for
        // anchor-gated rules whose text did NOT contain any anchor. Inverse
        // polarity to sourceMarkers (which suppress on presence; anchors
        // require presence).
        val anchorHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) anchorHitRules += ruleId
        }

        // Inverse-polarity anchor pass: collect rule ids whose text contained
        // at least one of their `categoryAnchorsAbsent` substrings. These are
        // the rules we must SUPPRESS — the text is from a legitimate medical
        // context, so the "non-medical institution + disease names" rule
        // should NOT fire. (The rule fires iff NONE of its absent-anchors are
        // in text — absence of a hit-rule means "anchor absent", which is the
        // trigger condition. `anchorAbsentHitRules` thus names the suppressed
        // set.)
        val anchorAbsentHandler = AhoCorasickDoubleArrayTrie.IHit<List<String>> { _, _, ruleIds ->
            for (ruleId in ruleIds) anchorAbsentHitRules += ruleId
        }

        // Same parseText guard as the base class — calling parseText on an
        // unbuilt HankCS trie throws `Cannot load from int array because
        // "this.base" is null`. Shell profile ships an empty rules JSON that
        // leaves these tries unbuilt.
        if (hasAnyAbsenceRule && hasSourceMarkerTrie) {
            sourceMarkerTrie.parseText(normalized, sourceMarkerHandler)
        }
        if (hasAnchorTrie) {
            anchorTrie.parseText(normalized, anchorHandler)
        }
        if (hasAnchorAbsentTrie) {
            anchorAbsentTrie.parseText(normalized, anchorAbsentHandler)
        }

        // Phase 1 + 2 + 2.5: keyword AC pass, dedup. Inherited from
        // AhoCorasickMatcher.runKeywordScan.
        val substringDeduped = runKeywordScan(normalized)

        // Phase 3 emit step 1: absence rule dedup. When a rule has
        // sourceMarkers OR categoryAnchorsAbsent, at most one hit per
        // ruleId (the rule as a whole either fires or doesn't; the
        // matchedText is whichever claim keyword was longest). Both gate
        // types scope the rule to a single firing condition (source
        // citation present / medical-context absent), so showing N
        // keyword-matches for one ruleId adds noise without information.
        // Non-gated rules keep one-per-(ruleId, originalKeyword) from
        // Phase 2. Insertion order preserved so the deterministic
        // HashMap iteration of [ruleById] does not shuffle the
        // user-visible hit list.
        val seenAbsenceRules = mutableSetOf<String>()
        val hits = mutableListOf<RuleHit>()
        for ((key, matched) in substringDeduped) {
            val (ruleId, _) = key
            val rule = ruleById[ruleId] ?: continue
            val dedupOncePerRule = rule.sourceMarkers.isNotEmpty() ||
                rule.categoryAnchorsAbsent.isNotEmpty()
            if (dedupOncePerRule) {
                if (!seenAbsenceRules.add(rule.id)) continue
            }
            hits.add(
                RuleHit(
                    ruleId = rule.id,
                    matchedText = matched,
                    category = rule.category,
                    regulation = rule.regulation,
                    lawText = rule.lawText,
                    severity = rule.severity,
                    domain = CategoryDisplay.DOMAIN_AD,
                )
            )
        }

        // Phase 3 emit step 2: gate filter.
        //
        // For absence rules, suppress claim hits whenever the rule's source
        // marker was matched anywhere in the text (one source marker present
        // = claim considered cited, regardless of which claim keyword
        // matched). Non-absence rules (sourceMarkers empty) pass through
        // unchanged.
        //
        // For category-anchor-gated rules (categoryAnchors non-empty), keep
        // the hit only when at least one anchor substring was found anywhere
        // in the scanned text. Inverse polarity to source-marker suppression:
        // source marker presence suppresses; anchor presence is required.
        //
        // For category-anchor-absent-gated rules (categoryAnchorsAbsent
        // non-empty), keep the hit only when NONE of the absent-anchors
        // appeared in the scanned text. Inverse polarity to the positive
        // anchor gate: positive anchor presence is required; absent-anchor
        // presence is forbidden.
        //
        // Gate composition is AND across all three — see class KDoc Bug 2
        // fix note.
        return hits.filter { hit ->
            val rule = ruleById[hit.ruleId] ?: return@filter true
            val suppressBySourceMarker =
                rule.sourceMarkers.isEmpty() || rule.id !in sourceMarkerHitRules
            val requireAnchor =
                rule.categoryAnchors.isEmpty() || rule.id in anchorHitRules
            val suppressByAbsentAnchor =
                rule.categoryAnchorsAbsent.isEmpty() || rule.id !in anchorAbsentHitRules
            suppressBySourceMarker && requireAnchor && suppressByAbsentAnchor
        }
    }
}