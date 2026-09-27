package com.arena.shizuku_shell_local;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço local (mesmo app) que executa scripts/arquivos e comandos longos.
 * IAs no próprio aparelho usam via "am startservice" ou bind local.
 *
 * Actions:
 *   com.arena.shizuku_shell_local.RUN_SCRIPT --es path /sdcard/script.sh [--es out ...] [--ei timeout 120]
 */
public class McpService extends Service {
    private final IBinder binder = new Binder();
    private static final AtomicLong SEQ = new AtomicLong(0);

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String path = intent.getStringExtra("path");
            String cmd = intent.getStringExtra("cmd");
            String out = intent.getStringExtra("out");
            int timeout = intent.getIntExtra("timeout", 120);
            final long id = SEQ.incrementAndGet();
            new Thread(() -> {
                McpReceiver.Result r;
                String label;
                if (path != null && !path.isEmpty()) {
                    label = "sh " + path;
                    r = McpReceiver.exec("/system/bin/sh " + path, Math.max(1, Math.min(600, timeout)));
                } else {
                    label = cmd != null ? cmd : "";
                    r = McpReceiver.exec(McpReceiver.normalize(label), Math.max(1, Math.min(600, timeout)));
                }
                String body = "$ " + label + "\nexitCode: " + r.exitCode
                        + "\n\nSTDOUT:\n" + (r.stdout.isEmpty() ? "(vazio)" : r.stdout)
                        + "\n\nSTDERR:\n" + (r.stderr.isEmpty() ? "(vazio)" : r.stderr);
                if (out != null && !out.isEmpty()) {
                    try {
                        java.io.FileWriter w = new java.io.FileWriter(out);
                        w.write(body);
                        w.close();
                    } catch (Throwable ignored) {}
                }
                Intent done = new Intent("com.arena.shizuku_shell_local.RESULT");
                done.putExtra("id", id);
                done.putExtra("cmd", label);
                done.putExtra("exitCode", r.exitCode);
                done.putExtra("stdout", r.stdout);
                done.putExtra("stderr", r.stderr);
                try { sendBroadcast(done); } catch (Throwable ignored) {}
                stopSelf(startId);
            }).start();
        }
        return START_NOT_STICKY;
    }
}
