package com.example.wstream

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

class WStreamService : AccessibilityService() {

    companion object {
        private const val TAG = "WStreamService"
        private const val PORT = 7912
    }

    private val frameId = java.util.concurrent.atomic.AtomicLong(0)
    private val serverStarted = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var client: Socket? = null
    @Volatile private var out: BufferedWriter? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        maybeStartServer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            val root = rootInActiveWindow ?: return
            val recycler = NodeUtils.findScrollableList(root) ?: return
            val items = NodeUtils.snapshotVisibleItems(recycler)
            sendFrame(items)
        }
    }

    override fun onInterrupt() {}

    private fun maybeStartServer() {
        if (serverStarted.getAndSet(true)) return
        pool.execute {
            try {
                val server = ServerSocket(PORT)
                Log.i(TAG, "Server listening on $PORT")
                while (true) {
                    val sock = server.accept()
                    Log.i(TAG, "Client connected")
                    client = sock
                    out = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                serverStarted.set(false)
            }
        }
    }

    private fun sendFrame(items: List<NodeUtils.Item>) {
        val o = JSONObject().apply {
            put("frame", frameId.getAndIncrement())
            put("ts", System.currentTimeMillis())
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            put("items", arr)
        }
        try {
            out?.let { w ->
                w.write(o.toString())
                w.write("\n")
                w.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Write failed, dropping client", e)
            try { client?.close() } catch (_: Exception) {}
            client = null
            out = null
        }
    }
}
