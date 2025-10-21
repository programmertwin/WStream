package com.example.wstream

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WStreamService : AccessibilityService() {

    companion object {
        private const val TAG = "WStreamService"
        private const val PORT = 7912
        private const val SNAPSHOT_MS = 500L   // هر 500ms یک فریم (هارت‌بیت/آیتم‌ها)
    }

    // --- سرور و خروجی ---
    private val serverStarted = AtomicBoolean(false)
    private val ioPool = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var out: BufferedWriter? = null

    // --- زمان‌بندی اسنپ‌شات دوره‌ای ---
    private val scheduler: ScheduledExecutorService = ScheduledThreadPoolExecutor(1)
    private val frameId = AtomicLong(0)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        startServerIfNeeded()
        // شروع هارت‌بیت/اسنپ‌شات دوره‌ای
        scheduler.scheduleAtFixedRate(
            { safeSnapshotAndSend() },
            0L,
            SNAPSHOT_MS,
            TimeUnit.MILLISECONDS
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                safeSnapshotAndSend() // با هر تغییر، سریع یک فریم بده
            }
        }
    }

    override fun onInterrupt() {
        // nothing
    }

    override fun onDestroy() {
        super.onDestroy()
        try { scheduler.shutdownNow() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        Log.i(TAG, "Service destroyed")
    }

    // ----------------- سرور -----------------

    private fun startServerIfNeeded() {
        if (serverStarted.getAndSet(true)) return
        ioPool.execute {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "Server listening on $PORT")
                while (true) {
                    val sock = serverSocket!!.accept()
                    Log.i(TAG, "Client connected")
                    synchronized(this) {
                        try { client?.close() } catch (_: Exception) {}
                        client = sock
                        out = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                serverStarted.set(false)
            }
        }
    }

    // ----------------- اسنپ‌شات و ارسال -----------------

    /** امن: هیچ استثنایی به بیرون پرتاب نکند. */
    private fun safeSnapshotAndSend() {
        try {
            val root = rootInActiveWindow
            if (root == null) {
                // هیچ پنجره فعالی نیست، هارت‌بیت خالی بفرست
                sendFrame(emptyList())
                return
            }
            val list = NodeUtils.findScrollableList(root)
            val items = if (list != null) NodeUtils.snapshotVisibleItems(list) else emptyList()
            sendFrame(items)
        } catch (e: Exception) {
            Log.w(TAG, "snapshot error", e)
            // حتی در خطا هم یک هارت‌بیت خالی بدهیم تا کلاینت زنده بودن را ببیند
            sendFrame(emptyList())
        }
    }

    /** items را به‌صورت NDJSON یک‌خطی بفرست. */
    private fun sendFrame(items: List<NodeUtils.Item>) {
        val obj = JSONObject().apply {
            put("frame", frameId.getAndIncrement())
            put("ts", System.currentTimeMillis())
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            put("items", arr)
        }
        val line = obj.toString()

        try {
            val writer = out
            if (writer != null) {
                synchronized(this) {
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "write failed; dropping client", e)
            synchronized(this) {
                try { client?.close() } catch (_: Exception) {}
                client = null
                out = null
            }
        }
    }
}
