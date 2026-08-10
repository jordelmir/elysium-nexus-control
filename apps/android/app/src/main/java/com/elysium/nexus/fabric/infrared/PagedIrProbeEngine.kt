package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.ranking.CandidatePager

/**
 * V06-P27 — Paged variant of [IrProbeEngine] (MASTER_ORDER §75–§78:
 * candidate paging, bounded memory).
 *
 * The eager [IrProbeEngine] materializes the ENTIRE universal sweep in
 * memory (`filter → distinctBy fingerprint → sortedByDescending` over all
 * candidates). This variant walks the same sweep through a
 * [CandidatePager]: pages are ranked by the source (the catalog's §P0-8
 * probability ordering), filtered to `VOLUME_UP`, fingerprint-deduplicated
 * ACROSS pages (bounded set) and locally re-scored inside the page.
 *
 * Memory bound: only `pageSize * maxCachedPages` candidates are ever
 * materialized, whatever the sweep size (test-enforced).
 *
 * Honesty:
 * - `totalCandidates` is the SOURCE count (catalog-level), not the
 *   dedup-adjusted count — as in the eager engine, pre-filter rows count.
 * - Strict global re-ranking across all pages is not performed: each page
 *   is re-scored locally after the source's global ranking; candidates
 *   inside a page are ordered by [CandidateScorer].
 * - [selectById] searches the loaded window + at most `maxCachedPages`
 *   fresh pages (bounded). A candidate beyond that window is honestly
 *   reported as not found (returns false, position unchanged).
 */
class PagedIrProbeEngine(
    private val pager: CandidatePager<IrCodeSet>,
    targetModel: String? = null,
    private val penaltyMap: Map<String, Int> = emptyMap(),
    private val successMap: Map<String, Int> = emptyMap(),
    private val failMap: Map<String, Int> = emptyMap()
) {
    private val targetModel: String? = targetModel

    private val seenFingerprints: MutableSet<String> = HashSet()

    private var pageIndex = -1
    private var pageItems: List<IrCodeSet> = emptyList()
    private var itemIndex = 0
    private var consumed = 0

    /** Catalog-level sweep size (source count, not dedup-adjusted). */
    val totalCandidates: Int get() = pager.totalCount

    /** How many candidates the cursor has handed out. */
    val currentProbeNumber: Int get() = consumed

    val hasMore: Boolean
        get() = itemIndex < pageItems.size || (pageIndex + 1) < pager.pageCount

    /** Pages materialized so far (memory accounting). */
    val loadedPagesCount: Int get() = pager.loadedPages

    /** Candidates materialized so far (memory accounting). */
    val loadedItemsCount: Int get() = pager.loadedItems

    /** Get the currently selected candidate, or null if exhausted. */
    fun currentCandidate(): IrCodeSet? = pageItems.getOrNull(itemIndex)

    /** Advance to the next ranked candidate. Returns null when exhausted. */
    fun nextCandidate(): IrCodeSet? {
        while (true) {
            if (itemIndex < pageItems.size) {
                val current = pageItems[itemIndex]
                itemIndex++
                consumed++
                return current
            }
            if (!loadNextPage()) return null
        }
    }

    /**
     * Re-position the probe on a candidate by ID. Searches every page the
     * cursor has already visited (served from the bounded page cache) plus
     * at most [CandidatePager.maxCachedPages] fresh pages — the window is
     * bounded, so a candidate beyond it is honestly reported as not found
     * (returns false, position unchanged).
     *
     * Honesty: the id-lookup is fingerprint-agnostic, so re-selecting an
     * item whose fingerprint was already consumed earlier could re-hand it.
     * The auto-sweep re-positions to the LAST transmitted candidate, which
     * is never previously consumed.
     */
    fun selectById(candidateId: String): Boolean {
        val end = minOf(maxOf(pageIndex, 0) + pager.maxCachedPages + 1, pager.pageCount)
        for (p in 0 until end) {
            val view = pageView(p)
            val idx = view.indexOfFirst { it.id == candidateId }
            if (idx >= 0) {
                pageIndex = p
                pageItems = view
                itemIndex = idx
                consumed = p * pager.pageSize + idx + 1
                return true
            }
        }
        return false
    }

    /** Reset back to the beginning of the sweep. */
    fun reset() {
        pageIndex = -1
        pageItems = emptyList()
        itemIndex = 0
        consumed = 0
        seenFingerprints.clear()
    }

    // ── internals ─────────────────────────────────────────────

    private fun loadNextPage(): Boolean {
        pageIndex++
        if (pageIndex >= pager.pageCount) return false
        pageItems = indexedPage(pageIndex)
        itemIndex = 0
        return true
    }

    /** Filter + cross-page dedup + local page re-rank (cursor view). */
    private fun indexedPage(index: Int): List<IrCodeSet> {
        val page = pager.page(index)
        return page.asSequence()
            .filter { IrAction.VOLUME_UP in it.commands }
            .filter { seenFingerprints.add(IrProbeEngine.fingerprintSignal(it.commands.getValue(IrAction.VOLUME_UP))) }
            .sortedByDescending { score(it) }
            .toList()
    }

    /** Filter + local re-rank WITHOUT dedup (id-lookup view). */
    private fun pageView(index: Int): List<IrCodeSet> =
        pager.page(index)
            .asSequence()
            .filter { IrAction.VOLUME_UP in it.commands }
            .sortedByDescending { score(it) }
            .toList()

    private fun score(candidate: IrCodeSet): Int =
        CandidateScorer.scoreCandidate(
            candidate,
            targetModel,
            penaltyScore = penaltyMap[candidate.id] ?: 0,
            successCount = successMap[candidate.id] ?: 0,
            failCount = failMap[candidate.id] ?: 0
        ).score

    /** Fingerprint accessor for tests. */
    fun fingerprintOf(signal: IrSignal): String = IrProbeEngine.fingerprintSignal(signal)
}