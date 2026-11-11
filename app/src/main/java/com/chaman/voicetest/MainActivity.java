package com.chaman.voicetest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView statusTv, statsTv, sensTv, logTv;
    private Button toggle;
    private SeekBar seek;
    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private static final int BG = Color.parseColor("#0D0F14"), CARD = Color.parseColor("#171A22"),
            ACC = Color.parseColor("#4FD1C5"), TXT = Color.parseColor("#E6E8EE"), DIM = Color.parseColor("#8A90A0");

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(pad, pad * 2, pad, pad);

        root.addView(tv("Chaman Voice Test  v0.1", 20, ACC));
        statusTv = tv("Stopped", 15, TXT); root.addView(statusTv);
        statsTv = tv("", 13, DIM); root.addView(statsTv);

        sensTv = tv("", 14, TXT); sensTv.setPadding(0, pad, 0, 0); root.addView(sensTv);
        seek = new SeekBar(this); seek.setMax(100); seek.setProgress(sp.getInt("sens", 50));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { applySens(p); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { sp.edit().putInt("sens", s.getProgress()).apply(); }
        });
        root.addView(seek); applySens(seek.getProgress());

        toggle = new Button(this); toggle.setText("Start listening");
        toggle.setOnClickListener(v -> onToggle()); root.addView(toggle);
        Button batt = new Button(this); batt.setText("Battery optimization off karo (screen-off ke liye)");
        batt.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(batt);

        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(CARD);
        logTv = tv("", 12, TXT); logTv.setTypeface(android.graphics.Typeface.MONOSPACE); logTv.setPadding(pad, pad, pad, pad);
        sv.addView(logTv);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}, 1);
        tick();
    }

    private void applySens(int p) {
        // higher sensitivity -> lower threshold (0.90 .. 0.10)
        WakeService.threshold = 0.90f - 0.80f * (p / 100f);
        sensTv.setText(String.format(Locale.US, "Sensitivity %d%%   (threshold %.2f)", p, WakeService.threshold));
    }

    private void onToggle() {
        if (WakeService.running) {
            startService(new Intent(this, WakeService.class).setAction("STOP"));
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1); return;
            }
            startForegroundService(new Intent(this, WakeService.class));
        }
    }

    private void tick() {
        statusTv.setText(WakeService.status);
        toggle.setText(WakeService.running ? "Stop listening" : "Start listening");
        long sec = WakeService.running ? (SystemClock.elapsedRealtime() - WakeService.startedAt) / 1000 : 0;
        statsTv.setText(String.format(Locale.US, "score %.2f  |  max %.2f  |  detections %d  |  uptime %02d:%02d:%02d",
                WakeService.lastScore, WakeService.maxScore, WakeService.detections, sec / 3600, sec / 60 % 60, sec % 60));
        StringBuilder sb = new StringBuilder();
        synchronized (WakeService.logs) { for (int i = WakeService.logs.size() - 1; i >= 0; i--) sb.append(WakeService.logs.get(i)).append('\n'); }
        logTv.setText(sb);
        h.postDelayed(this::tick, 300);
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
