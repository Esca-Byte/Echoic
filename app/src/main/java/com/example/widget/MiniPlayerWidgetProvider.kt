package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.PlaybackSessionStore
import com.example.data.model.SongItem
import com.example.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class MiniPlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAllWidgetsFromPrefs(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_WIDGET_PLAY_PAUSE ||
            action == ACTION_WIDGET_NEXT ||
            action == ACTION_WIDGET_FAVORITE
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasWidgetSong = !prefs.getString(KEY_SONG_ID, null).isNullOrBlank()
            val hasSavedSessionSong = hasWidgetSong ||
                PlaybackService.activeServiceSong != null ||
                PlaybackSessionStore.loadSession(context)?.currentTrack != null
            if (!hasSavedSessionSong) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(launchIntent)
                return
            }

            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            if (PlaybackService.activeServiceSong != null) {
                runCatching { context.startService(serviceIntent) }
                    .onFailure { ContextCompat.startForegroundService(context, serviceIntent) }
            } else {
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.echoic.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.echoic.widget.ACTION_NEXT"
        const val ACTION_WIDGET_FAVORITE = "com.example.echoic.widget.ACTION_FAVORITE"

        private const val PREFS_NAME = "echoic_widget_prefs"
        private const val KEY_SONG_ID = "song_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ARTWORK_URL = "artwork_url"
        private const val KEY_IS_PLAYING = "is_playing"
        private const val KEY_IS_FAVORITE = "is_favorite"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val artClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
        }

        @Volatile
        private var cachedArtworkUrl: String = ""

        @Volatile
        private var cachedArtworkBitmap: Bitmap? = null

        fun syncWidget(
            context: Context,
            song: SongItem?,
            isPlaying: Boolean
        ) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_SONG_ID, song?.id.orEmpty())
                .putString(KEY_TITLE, song?.title.orEmpty())
                .putString(KEY_ARTIST, song?.artist.orEmpty())
                .putString(KEY_ARTWORK_URL, song?.artworkUrl.orEmpty())
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .putBoolean(KEY_IS_FAVORITE, song?.isFavorite == true)
                .apply()

            val manager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MiniPlayerWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            updateAllWidgetsFromPrefs(appContext, manager, ids)

            val artUrl = song?.artworkUrl.orEmpty()
            if (artUrl.isNotBlank() && artUrl != cachedArtworkUrl) {
                widgetScope.launch {
                    val bitmap = loadRoundedArtworkBitmap(appContext, artUrl)
                    if (bitmap != null) {
                        cachedArtworkUrl = artUrl
                        cachedArtworkBitmap = bitmap
                        updateAllWidgetsFromPrefs(appContext, manager, ids)
                    }
                }
            } else if (artUrl.isBlank()) {
                cachedArtworkUrl = ""
                cachedArtworkBitmap = null
            }
        }

        private fun updateAllWidgetsFromPrefs(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fallbackSessionTrack = if (prefs.getString(KEY_SONG_ID, null).isNullOrBlank()) {
                PlaybackSessionStore.loadSession(context)?.currentTrack
            } else {
                null
            }
            val title = prefs.getString(KEY_TITLE, "").orEmpty()
                .ifBlank { fallbackSessionTrack?.title.orEmpty() }
                .ifBlank { context.getString(R.string.widget_empty_title) }
            val artist = prefs.getString(KEY_ARTIST, "").orEmpty()
                .ifBlank { fallbackSessionTrack?.artist.orEmpty() }
                .ifBlank { context.getString(R.string.widget_empty_artist) }
            val artUrl = prefs.getString(KEY_ARTWORK_URL, "").orEmpty()
                .ifBlank { fallbackSessionTrack?.artworkUrl.orEmpty() }
            val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
            val isFavorite = prefs.getBoolean(KEY_IS_FAVORITE, false) || (fallbackSessionTrack?.isFavorite == true)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                Intent(context, MiniPlayerWidgetProvider::class.java).setAction(ACTION_WIDGET_PLAY_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                Intent(context, MiniPlayerWidgetProvider::class.java).setAction(ACTION_WIDGET_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val favPendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                Intent(context, MiniPlayerWidgetProvider::class.java).setAction(ACTION_WIDGET_FAVORITE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val activeBitmap = if (artUrl.isNotBlank() && artUrl == cachedArtworkUrl) {
                cachedArtworkBitmap
            } else {
                null
            }

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_mini_player).apply {
                    setTextViewText(R.id.widget_title, title)
                    setTextViewText(R.id.widget_artist, artist)

                    if (activeBitmap != null) {
                        setImageViewBitmap(R.id.widget_artwork, activeBitmap)
                    } else {
                        setImageViewResource(R.id.widget_artwork, R.drawable.spiral_logo)
                    }

                    setImageViewResource(
                        R.id.widget_btn_play_pause,
                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                    )
                    setImageViewResource(
                        R.id.widget_btn_favorite,
                        if (isFavorite) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart_outline
                    )
                    setInt(
                        R.id.widget_btn_favorite,
                        "setColorFilter",
                        if (isFavorite) 0xFFFB7185.toInt() else 0xFFF8FAFC.toInt()
                    )

                    setOnClickPendingIntent(R.id.widget_artwork, openAppPendingIntent)
                    setOnClickPendingIntent(R.id.widget_track_info, openAppPendingIntent)
                    setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)
                    setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)
                    setOnClickPendingIntent(R.id.widget_btn_favorite, favPendingIntent)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun loadRoundedArtworkBitmap(context: Context, artworkUrl: String): Bitmap? {
            return runCatching {
                val rawBytes: ByteArray? = if (artworkUrl.startsWith("file://") || artworkUrl.startsWith("/")) {
                    val file = File(artworkUrl.removePrefix("file://"))
                    if (file.exists()) file.readBytes() else null
                } else {
                    val request = Request.Builder().url(artworkUrl).build()
                    artClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.bytes() else null
                    }
                }
                if (rawBytes == null || rawBytes.isEmpty()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)

                var sampleSize = 1
                val targetPx = 144
                while (bounds.outWidth / sampleSize > targetPx * 2 && bounds.outHeight / sampleSize > targetPx * 2) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return null
                val scaled = Bitmap.createScaledBitmap(decoded, targetPx, targetPx, true)
                if (scaled != decoded) decoded.recycle()

                val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rect = Rect(0, 0, targetPx, targetPx)
                val rectF = RectF(rect)
                val radius = (14f * context.resources.displayMetrics.density).coerceAtMost(targetPx / 3f)
                canvas.drawRoundRect(rectF, radius, radius, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, rect, rect, paint)
                if (scaled != output) scaled.recycle()
                output
            }.getOrNull()
        }
    }
}
