package com.arena.shizuku_shell_local;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity invisível que executa comandos vindos de Intent (para IAs / automação).
 *
 * Uso via adb / Tasker / Termux (am broadcast):
 *
 *   am broadcast -a com.arena.shizuku_shell_local.RUN \
 *     --es cmd "id && whoami" \
 *     --es out /sdcard/mcp_out.txt
 *
 * Extras:
 *   cmd     (obrigatório) comando shell a executar via Shizuku
 *   out     (opcional) caminho do arquivo onde gravar STDOUT+STDERR+exitCode
 *   timeout (opcional) segundos de timeout (default 60)
 *
 * Resposta: broadcast com action
 *   com.arena.shizuku_shell_local.RESULT
 * extras: cmd, exitCode (int), stdout, stderr, timedOut (boolean).
 * Se "out" foi pedido, o mesmo conteúdo vai para o arquivo.
 */
public class McpRunActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String cmd = in != null ? in.getStringExtra("cmd") : null;
        String out = in != null ? in.getStringExtra("out") : null;
        int timeout = in != null ? in.getIntExtra("timeout", 60) : 60;
        if (cmd == null || cmd.trim().isEmpty()) {
            sendResult("", -2, "", "cmd vazio", false, out);
            finish();
            return;
        }
        final String fcmd = McpReceiver.normalize(cmd);
        final String fout = out;
        final int ftimeout = Math.max(1, Math.min(600, timeout));
        new Thread(() -> {
            McpReceiver.Result r = McpReceiver.exec(fcmd, ftimeout);
            sendResult(fcmd, r.exitCode, r.stdout, r.stderr, r.timedOut, fout);
            finish();
        }).start();
    }

    private void sendResult(String cmd, int exitCode, String stdout, String stderr, boolean timedOut, String outPath) {
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
            sendBroadcast(r);
        } catch (Throwable ignored) {}
    }
}
