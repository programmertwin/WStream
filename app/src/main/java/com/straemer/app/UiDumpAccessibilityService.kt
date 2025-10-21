package com.straemer.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class UiDumpAccessibilityService : AccessibilityService() {
  companion object {
    val lastXml = AtomicReference("<hierarchy/>")
    val version = AtomicLong(0L)
    @Volatile var lastUpdateMillis: Long = 0L
  }
  override fun onServiceConnected() { dumpNow() }
  override fun onInterrupt() {}
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val now = SystemClock.uptimeMillis()
    if (now - lastUpdateMillis < 16) return
    lastUpdateMillis = now
    dumpNow()
  }
  private fun dumpNow() {
    val root = rootInActiveWindow ?: return
    val sb = StringBuilder(256 * 1024)
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><hierarchy>")
    fun esc(s: CharSequence?): String =
      (s?.toString() ?: "").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    fun walk(n: AccessibilityNodeInfo) {
      sb.append("<node")
      fun attr(k:String, v:String?) { if (!v.isNullOrEmpty()) sb.append(" $k=\"" + esc(v) + "\"") }
      attr("class", n.className?.toString())
      attr("package", n.packageName?.toString())
      attr("text", n.text?.toString())
      attr("content-desc", n.contentDescription?.toString())
      attr("resource-id", n.viewIdResourceName)
      val r = Rect(); n.getBoundsInScreen(r)
      attr("bounds", "[${r.left},${r.top}][${r.right},${r.bottom}]")
      attr("clickable", n.isClickable.toString())
      attr("focusable", n.isFocusable.toString())
      attr("enabled", n.isEnabled.toString())
      attr("checked", n.isChecked.toString())
      attr("selected", n.isSelected.toString())
      sb.append(">")
      for (i in 0 until n.childCount) { n.getChild(i)?.let { walk(it) } }
      sb.append("</node>")
    }
    walk(root)
    sb.append("</hierarchy>")
    lastXml.set(sb.toString()); version.incrementAndGet()
  }
}
