package com.chaman.voicetest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WakeService extends Service {
    // Shared state read by the UI (simple polling, fine for a test app)
    public static volatile boolean running = false;
    public static volatile float threshold = 0.5f;
    public static volatile float lastScore = 0f, maxScore = 0f;
    public static volatile int detections = 0;
    public static volatile long startedAt = 0;
    public static volatile String status = "Stopped";
    public static final List<String> logs = new ArrayList<>();

    static void log(String s) {
        synchronized (logs) {
            logs.add(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + s);
            while (logs.size() > 200) logs.remove(0);
        }
    }

    private Thread worker;
    private PowerManager.WakeLock wl;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (running) return START_STICKY;
        NotificationChannel ch = new NotificationChannel("wake", "Wake word", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "wake")
                .setContentTitle("Chaman Voice Test").setContentText("Listening for wake word...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(1, n);

        wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chaman:wake");
        wl.acquire();
        running = true; detections = 0; maxScore = 0; lastScore = 0;
        startedAt = SystemClock.elapsedRealtime();
        status = "Loading model...";
        worker = new Thread(this::loop, "wake-loop");
        worker.start();
        return START_STICKY;
    }

    private void loop() {
        AudioRecord rec = null; WakeWordDetector det = null;
        try {
            det = new WakeWordDetector(this, "hey_jarvis_v0.1.onnx");
            int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, WakeWordDetector.CHUNK * 8));
            rec.startRecording();
            status = "Listening (hey jarvis)";
            log("Started listening");
            short[] buf = new short[WakeWordDetector.CHUNK];
            long cooldownUntil = 0;
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            while (running) {
                int got = 0;
                while (got < buf.length && running) {
                    int r = rec.read(buf, got, buf.length - got);
                    if (r < 0) throw new RuntimeException("AudioRecord error " + r);
                    got += r;
                }
                if (!running) break;
                float s = det.process(buf);
                lastScore = s;
                if (s > maxScore) maxScore = s;
                long now = SystemClock.elapsedRealtime();
                if (s >= threshold && now > cooldownUntil) {
                    detections++;
                    cooldownUntil = now + 2000;
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    log(String.format(Locale.US, "WAKE detected (score %.2f, thr %.2f)", s, threshold));
                }
            }
            tone.release();
        } catch (Throwable t) {
            log("ERROR: " + t);
            status = "Error: " + t.getMessage();
        } finally {
            if (rec != null) { try { rec.stop(); } catch (Exception ignored) {} rec.release(); }
            if (det != null) det.close();
            running = false;
            if (!status.startsWith("Error")) status = "Stopped";
        }
    }

    @Override public void onDestroy() {
        running = false;
        if (worker != null) try { worker.join(1500); } catch (InterruptedException ignored) {}
        if (wl != null && wl.isHeld()) wl.release();
        log("Service stopped");
        super.onDestroy();
    }
}
