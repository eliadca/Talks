package com.eliadca.talks.ui.notes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eliadca.talks.MainActivity
import com.eliadca.talks.R

/** Started only by an explicit Listen action; minimising any screen never cancels capture. */
class NotesRecordingService : Service() {
    private var stoppedByUser = false
    private var ownedSession: NotesViewModel? = null
    override fun onCreate() { super.onCreate(); activeService = this }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PAUSE) { controller?.pause(); stopSelf(); return START_NOT_STICKY }
        val session = controller
        ownedSession = session; stoppedByUser = false
        if (session == null || !session.listening) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Transcripción de notas", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).setAction(OPEN_NOTE).putExtra("note", session.recordingNoteId ?: 0L)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val pause = PendingIntent.getService(this, 1, Intent(this, NotesRecordingService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_note_microphone)
                .setContentTitle("Transcribiendo · ${session.recordingTitle}")
                .setContentText("Puedes seguir escribiendo o minimizar Talks. Toca Pausar para detener.")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_pause, "Pausar", pause).build()
            ServiceCompat.startForeground(this, 41, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            session.beginCapture()
        } catch (e: Exception) { session.recordingFailure(e); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        if (!stoppedByUser) ownedSession?.takeIf { it === controller && it.listening }?.pause()
        if (activeService === this) { activeService = null; controller = null }
        super.onDestroy()
    }
    companion object {
        const val OPEN_NOTE = "com.eliadca.talks.OPEN_RECORDING_NOTE"
        private const val CHANNEL = "notes-transcription"
        private const val PAUSE = "com.eliadca.talks.PAUSE_NOTES"
        private var activeService: NotesRecordingService? = null
        private var controller: NotesViewModel? = null
        fun start(context: Context, session: NotesViewModel) {
            controller = session
            ContextCompat.startForegroundService(context, Intent(context, NotesRecordingService::class.java))
        }
        fun stop(context: Context) { activeService?.stoppedByUser = true; context.stopService(Intent(context, NotesRecordingService::class.java)) }
    }
}
