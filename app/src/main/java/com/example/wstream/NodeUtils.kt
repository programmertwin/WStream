package com.example.wstream

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.util.ArrayDeque

object NodeUtils {

    data class Item(
        val title: String,
        val subtitle: String?,
        val top: Int, val bottom: Int, val left: Int, val right: Int,
        val positionInSet: Int?
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("title", title)
            subtitle?.let { put("subtitle", it) }
            put("bounds", JSONObject().apply {
                put("l", left); put("t", top); put("r", right); put("b", bottom)
            })
            positionInSet?.let { put("pos", it) }
        }
    }

    fun findScrollableList(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            try {
                val cls = n.className?.toString() ?: ""
                if (n.isScrollable && (cls.contains("RecyclerView") || cls.contains("ListView") || cls.contains("AbsListView"))) {
                    return n
                }
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { q.add(it) }
                }
            } catch (_: Exception) {}
        }
        q.clear(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            try {
                if (n.isScrollable) return n
                for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
            } catch (_: Exception) {}
        }
        return null
    }

    fun snapshotVisibleItems(list: AccessibilityNodeInfo): List<Item> {
        val out = mutableListOf<Item>()
        for (i in 0 until list.childCount) {
            val row = list.getChild(i) ?: continue
            val r = Rect()
            row.getBoundsInScreen(r)
            if (r.height() <= 0) continue

            val (title, subtitle) = extractTexts(row)
            val pos = row.collectionItemInfo?.rowIndex
            out.add(Item(title = title, subtitle = subtitle,
                top = r.top, bottom = r.bottom, left = r.left, right = r.right,
                positionInSet = pos))
        }
        return out
    }

    private fun extractTexts(node: AccessibilityNodeInfo): Pair<String, String?> {
        var title: String? = node.text?.toString()?.trim()?.ifBlank { null }
        var subtitle: String? = node.contentDescription?.toString()?.trim()?.ifBlank { null }

        val q: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        for (i in 0 until node.childCount) node.getChild(i)?.let { q.add(it) }
        val texts = mutableListOf<String>()
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            try {
                n.text?.toString()?.trim()?.ifBlank { null }?.let { texts.add(it) }
                n.contentDescription?.toString()?.trim()?.ifBlank { null }?.let { texts.add(it) }
                for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
            } catch (_: Exception) {}
        }
        if (title == null && texts.isNotEmpty()) title = texts[0]
        if (subtitle == null && texts.size >= 2) subtitle = texts[1]
        return Pair(title ?: "", subtitle)
    }
}
