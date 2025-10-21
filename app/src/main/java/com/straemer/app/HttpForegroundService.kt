package com.straemer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import fi.iki.elonen.NanoHTTPD

class HttpForegroundService : Service() {

    private lateinit var server: MiniServer

    override fun onCreate() {
        super.onCreate()
        startForeground(1, makeNotification())
        // فقط روی لوپ‌بک گوش بده
        server = MiniServer("127.0.0.1", 8713)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun makeNotification(): Notification {
        val chanId = "uixml_http"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(chanId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    chanId,
                    "UI XML Stream",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, chanId)
            .setContentTitle("UI XML Inspector در حال اجرا")
            .setContentText("پورت 8713 آماده است")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pending)
            .build()
    }

    class MiniServer(hostname: String, port: Int) : NanoHTTPD(hostname, port) {
        override fun serve(session: IHTTPSession): Response = when (session.uri) {
            "/latest" -> {
                val xml = UiDumpAccessibilityService.lastXml.get()
                val etag = "W/\"${UiDumpAccessibilityService.version.get()}\""
                val inm = session.headers["if-none-match"]
                if (etag == inm) {
                    newFixedLengthResponse(Response.Status.NOT_MODIFIED, "text/plain", "")
                        .apply { addHeader("ETag", etag) }
                } else {
                    newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", xml)
                        .apply {
                            addHeader("Cache-Control", "no-cache")
                            addHeader("ETag", etag)
                        }
                }
            }
            "/ping" -> newFixedLengthResponse("OK")
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain", "404"
            )
        }
    }
}
