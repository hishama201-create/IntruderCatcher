package com.sabri.intrudercatcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * خدمة تعمل بالخلفية: تفتح الكاميرا الأمامية، تسجّل مقطع فيديو قصير
 * (بدون صوت) بدل صورة واحدة (V28) - يعطي سياق أوضح (محاولات متكررة،
 * تعابير وجه واضحة) - تحفظه بمجلد التطبيق الخاص، ثم تغلق الكاميرا
 * فورًا. لا تعرض أي واجهة أو معاينة على الشاشة. بعد الحفظ المحلي،
 * تُرسِل نسخة فورية لتيليجرام وترفع نسخة مشفّرة لـ Google Drive
 * (كلاهما اختياري وحسب إعدادات المستخدم بشاشة النسخ الاحتياطي).
 */
class CaptureService : Service() {

    private var recording: Recording? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, buildNotification(), foregroundServiceType())
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "صلاحية الكاميرا غير ممنوحة - لا يمكن التصوير")
            stopSelf()
            return START_NOT_STICKY
        }
        recordVideo()
        return START_NOT_STICKY
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else 0

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_launcher_fingerprint)
            .setContentIntent(openAppIntent)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    private fun recordVideo() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.SD, Quality.LOWEST),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
                val videoCapture = VideoCapture.withOutput(recorder)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                val fakeLifecycleOwner = ServiceLifecycleOwner()
                cameraProvider.bindToLifecycle(fakeLifecycleOwner, cameraSelector, videoCapture)
                fakeLifecycleOwner.start()

                val videoFile = createOutputFile()
                val outputOptions = FileOutputOptions.Builder(videoFile).build()

                recording = videoCapture.output
                    .prepareRecording(this, outputOptions)
                    .start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            cameraProvider.unbindAll()
                            if (!event.hasError()) {
                                Log.d(TAG, "تم حفظ الفيديو: ${videoFile.absolutePath}")
                                onCaptureFinished(videoFile)
                            } else {
                                Log.e(TAG, "فشل تسجيل الفيديو: ${event.cause?.message}")
                            }
                            stopSelf()
                        }
                    }

                mainHandler.postDelayed({ recording?.stop() }, RECORDING_DURATION_MS)
            } catch (e: Exception) {
                Log.e(TAG, "خطأ أثناء تشغيل الكاميرا: ${e.message}")
                stopSelf()
            }
        }, CameraXExecutors.mainThreadExecutor())
    }

    private fun onCaptureFinished(videoFile: File) {
        val caption = getString(
            R.string.telegram_caption,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
        TelegramSender.sendVideoAsync(this, videoFile, caption)
        DriveBackupManager.backupAsync(this, videoFile)
    }

    override fun onDestroy() {
        recording?.close()
        recording = null
        super.onDestroy()
    }

    private fun createOutputFile(): File {
        val dir = File(getExternalFilesDir(null), "Detector")
        if (!dir.exists()) dir.mkdirs()
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        return File(dir, "intruder_$timeStamp.mp4")
    }

    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL_ID = "detector_service_channel"
        private const val NOTIFICATION_ID = 101
        private const val RECORDING_DURATION_MS = 8000L

        fun enqueueCapture(context: Context) {
            val intent = Intent(context, CaptureService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
