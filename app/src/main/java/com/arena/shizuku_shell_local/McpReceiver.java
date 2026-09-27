package com.arena.shizuku_shell_local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rikka.shizuku.Shizuku;

/**
 * Receiver que permite a IAs / scripts externos executarem comandos via Shizuku.
 *
 * Disparo:
 *   am broadcast -a com.arena.shizuku_shell_local.RUN --es cmd "..." [--es out /sdcard/x.txt] [--ei timeout 60]
 *
 * Resposta via broadcast com action:
 *   com.arena.shizuku_shell_local.RESULT
 */
public class McpReceiver extends BroadcastReceiver {
    private static final ExecutorService EXEC = Executors.newCachedThreadPool();

    public static class Result {
        public int exitCode;
        public String stdout = "";
        public String stderr = "";
        public boolean timedOut;
    }

    public static String normalize(String command) {
        if (command == null) return "";
        String t = command.trim();
        String l = t.toLowerCase();
        if (l.equals("adb shell")) return "";
        if (l.startsWith("adb shell ")) return t.substring("adb shell ".length()).trim();
        return t;
    }

    public static Result exec(String command, int timeoutSec) {
        Result r = new Result();
        try {
            Process p = newProcess(new String[]{"/system/bin/sh", "-c", command});
            Future<String> so = EXEC.submit(() -> readAll(p.getInputStream()));
            Future<String> se = EXEC.submit(() -> readAll(p.getErrorStream()));
            Future<Integer> w = EXEC.submit(p::waitFor);
            try {
                r.exitCode = w.get(timeoutSec, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                r.timedOut = true;
                try { p.destroy(); } catch (Throwable ignored) {}
                r.exitCode = -1;
            }
            try { r.stdout = so.get(3, TimeUnit.SECONDS); } catch (Throwable ignored) {}
            try { r.stderr = se.get(3, TimeUnit.SECONDS); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            r.exitCode = -3;
            r.stderr = String.valueOf(t);
        }
        return r;
    }

    private static Process newProcess(String[] cmd) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, (Object) cmd, null, null);
    }

    private static String readAll(java.io.InputStream in) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString().trim();
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String cmd = normalize(intent.getStringExtra("cmd"));
        String out = intent.getStringExtra("out");
        int timeout = intent.getIntExtra("timeout", 60);
        if (timeout < 1) timeout = 1;
        if (timeout > 600) timeout = 600;
        if (cmd.isEmpty()) {
            reply(ctx, "", -2, "", "cmd vazio", false, out);
            return;
        }
        try {
            if (!Shizuku.pingBinder()) { reply(ctx, cmd, -4, "", "Shizuku inativo", false, out); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                reply(ctx, cmd, -5, "", "sem permissao Shizuku", false, out);
                return;
            }
        } catch (Throwable t) {
            reply(ctx, cmd, -3, "", String.valueOf(t), false, out);
            return;
        }
        final int ftimeout = timeout;
        final String fcmd = cmd, fout = out;
        EXEC.execute(() -> {
            Result r = exec(fcmd, ftimeout);
            reply(ctx, fcmd, r.exitCode, r.stdout, r.stderr, r.timedOut, fout);
        });
    }

    private void reply(Context ctx, String cmd, int exitCode, String stdout, String stderr, boolean timedOut, String outPath) {
        String body = "$ " + cmd + "\nexitCode: " + exitCode + (timedOut ? " \u2022 timeout" : "")
                + "\n\nSTDOUT:\n" + (stdout == null || stdout.isEmpty() ? "(vazio)" : stdout)
                + "\n\nSTDERR:\n" + (stderr == null || stderr.isEmpty() ? "(vazio)" : stderr);
        if (outPath != null && !outPath.isEmpty()) {
            try {
                java.io.FileWriter w = new java.io.FileWriter(outPath);
                w.write(body);
                w.close();
            } catch (Throwable ignored) {}
        }
        try {
            Intent r = new Intent("com.arena.shizuku_shell_local.RESULT");
            r.putExtra("cmd", cmd);
            r.putExtra("exitCode", exitCode);
            r.putExtra("stdout", stdout);
            r.putExtra("stderr", stderr);
            r.putExtra("timedOut", timedOut);
            ctx.sendBroadcast(r);
        } catch (Throwable ignored) {}
    }
}
