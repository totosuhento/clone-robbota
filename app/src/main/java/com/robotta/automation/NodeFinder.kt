package com.robotta.automation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

enum class MatchMode { EXACT, STARTS_WITH, CONTAINS }

/** Utilitas pencarian elemen UI di pohon aksesibilitas. */
object NodeFinder {

    private const val MAX_NODES = 4000

    fun texts(node: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>(3)
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
        }
        return out
    }

    fun label(node: AccessibilityNodeInfo): String = texts(node).joinToString(" | ")

    fun normalize(s: String): String =
        s.replace(' ', ' ').trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun matchText(text: String, label: String, mode: MatchMode): Boolean {
        val a = normalize(text)
        val b = normalize(label)
        if (a.isEmpty() || b.isEmpty()) return false
        return when (mode) {
            MatchMode.EXACT -> a == b
            MatchMode.STARTS_WITH -> a == b || a.startsWith("$b ") || a.startsWith("$b,") || a.startsWith("$b:") || a.startsWith(b)
            MatchMode.CONTAINS -> a.contains(b)
        }
    }

    fun matches(node: AccessibilityNodeInfo, labels: List<String>, mode: MatchMode): Boolean =
        texts(node).any { t -> labels.any { l -> matchText(t, l, mode) } }

    fun isEditable(node: AccessibilityNodeInfo): Boolean =
        node.isEditable || node.className?.toString()?.contains("EditText") == true

    fun findAll(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            try {
                if (predicate(node)) result += node
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            } catch (_: Exception) {
                // Node bisa kedaluwarsa saat layar berubah; abaikan.
            }
        }
        return result
    }

    /**
     * Mencari node berlabel tertentu (bukan kolom isian). Dicoba bertahap:
     * cocok persis -> diawali label -> (opsional) mengandung label.
     * Node yang terlihat di layar diprioritaskan.
     */
    fun findByLabels(
        roots: List<AccessibilityNodeInfo>,
        labels: List<String>,
        maxMode: MatchMode = MatchMode.STARTS_WITH,
        excludeEditable: Boolean = true
    ): AccessibilityNodeInfo? {
        val modes = when (maxMode) {
            MatchMode.EXACT -> listOf(MatchMode.EXACT)
            MatchMode.STARTS_WITH -> listOf(MatchMode.EXACT, MatchMode.STARTS_WITH)
            MatchMode.CONTAINS -> listOf(MatchMode.EXACT, MatchMode.STARTS_WITH, MatchMode.CONTAINS)
        }
        for (mode in modes) {
            // Label dengan prioritas lebih tinggi dicek dulu.
            for (label in labels) {
                val found = roots.asSequence()
                    .flatMap { root ->
                        findAll(root) { n ->
                            (!excludeEditable || !isEditable(n)) && matches(n, listOf(label), mode)
                        }.asSequence()
                    }
                    .sortedByDescending { it.isVisibleToUser }
                    .firstOrNull()
                if (found != null) return found
            }
        }
        return null
    }

    /** Mencari kolom isian berdasarkan hint/teks/label di dekatnya. */
    fun findEditable(roots: List<AccessibilityNodeInfo>, labels: List<String>): AccessibilityNodeInfo? {
        for (root in roots) {
            val edits = findAll(root) { isEditable(it) }
            if (edits.isEmpty()) continue

            edits.firstOrNull { matches(it, labels, MatchMode.STARTS_WITH) }?.let { return it }

            edits.firstOrNull { e ->
                try {
                    e.labeledBy?.let { matches(it, labels, MatchMode.STARTS_WITH) } == true
                } catch (_: Exception) {
                    false
                }
            }?.let { return it }

            edits.firstOrNull { ancestorHasLabel(it, labels) }?.let { return it }
        }
        return null
    }

    private fun ancestorHasLabel(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 2) {
            if (matches(parent, labels, MatchMode.STARTS_WITH)) return true
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                if (!isEditable(child) && child.childCount == 0 && matches(child, labels, MatchMode.EXACT)) return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also { node.getBoundsInScreen(it) }

    /** Kunci unik kasar untuk sebuah node: id + posisi di layar. */
    fun key(node: AccessibilityNodeInfo): String =
        "${node.viewIdResourceName}|${bounds(node).flattenToString()}"

    fun visibleEditables(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> =
        roots.flatMap { r -> findAll(r) { isEditable(it) && it.isVisibleToUser } }

    /** True jika node atau salah satu induk terdekatnya bisa diklik. */
    fun hasClickableAncestor(node: AccessibilityNodeInfo, depth: Int = 4): Boolean {
        var current: AccessibilityNodeInfo? = node
        var d = 0
        while (current != null && d <= depth) {
            if (current.isClickable) return true
            current = current.parent
            d++
        }
        return false
    }

    /** Semua teks di dalam induk node (untuk membaca nilai yang tampil di samping label). */
    fun contextText(node: AccessibilityNodeInfo, levelsUp: Int = 1): String {
        var target: AccessibilityNodeInfo = node
        repeat(levelsUp) { target = target.parent ?: return@repeat }
        return findAll(target) { texts(it).isNotEmpty() }
            .take(30)
            .flatMap { texts(it) }
            .joinToString(" ")
    }

    fun area(node: AccessibilityNodeInfo): Int = bounds(node).let { it.width() * it.height() }
}
