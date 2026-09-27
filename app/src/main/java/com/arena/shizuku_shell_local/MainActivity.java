// MIT License - see LICENSE file.
package com.arena.shizuku_shell_local;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSION_CODE = 6200;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private TextView statusText;
    private TextView outputText;
    private EditText commandEdit;
    private Button runButton;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode != REQUEST_PERMISSION_CODE) return;
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        mainHandler.post(() -> setStatus(granted ? "Permissão Shizuku concedida." : "Permissão Shizuku negada."));
    };

    static {
        try { NativeMarker.nativeLibraryName(); } catch (Throwable ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { Shizuku.addRequestPermissionResultListener(permissionListener); } catch (Throwable ignored) {}
        buildUi();
    }

    @Override
    protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permissionListener); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void buildUi() {
        int green = Color.rgb(0, 230, 118);
        int bg = Color.rgb(5, 8, 5);
        int panel = Color.rgb(10, 24, 17);

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(16));
        rootScroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(14), dp(14), dp(14));
        header.setBackground(round(panel, dp(18), green, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
        header.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = new TextView(this);
        title.setText("Shizuku Shell\nADB shell local via Shizuku");
        title.setTextColor(green);
        title.setTextSize(18);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(12);
        header.addView(title, titleParams);

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(185, 255, 202));
        statusText.setTextSize(13);
        statusText.setText("Toque em Status para verificar o Shizuku.");
        addGap(root, 12);
        root.addView(statusText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        addGap(root, 12);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        Button statusButton = makeButton("Status");
        statusButton.setOnClickListener(v -> refreshStatus());
        buttons.addView(statusButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button permissionButton = makeButton("Permissão");
        permissionButton.setOnClickListener(v -> requestPermission());
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(48), 1);
        p2.leftMargin = dp(10);
        buttons.addView(permissionButton, p2);

        commandEdit = new EditText(this);
        commandEdit.setText("adb shell id && whoami && getprop ro.build.version.release");
        commandEdit.setTextColor(Color.WHITE);
        commandEdit.setHintTextColor(Color.GRAY);
        commandEdit.setTextSize(14);
        commandEdit.setSingleLine(false);
        commandEdit.setMinLines(2);
        commandEdit.setMaxLines(5);
        commandEdit.setTypeface(Typeface.MONOSPACE);
        commandEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandEdit.setBackground(round(Color.rgb(1, 15, 10), dp(14), green, 1));
        commandEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        addGap(root, 14);
        root.addView(commandEdit, new LinearLayout.LayoutParams(-1, -2));

        runButton = makeButton("Executar");
        runButton.setOnClickListener(v -> runCommand());
        addGap(root, 12);
        root.addView(runButton, new LinearLayout.LayoutParams(-1, dp(52)));

        outputText = new TextView(this);
        outputText.setText("Saída do comando aparecerá aqui.");
        outputText.setTextColor(Color.rgb(185, 255, 202));
        outputText.setTextSize(13);
        outputText.setTypeface(Typeface.MONOSPACE);
        outputText.setTextIsSelectable(true);
        outputText.setPadding(dp(12), dp(12), dp(12), dp(12));
        outputText.setBackground(round(Color.rgb(1, 8, 5), dp(14), Color.rgb(0, 110, 60), 1));
        addGap(root, 14);
        root.addView(outputText, new LinearLayout.LayoutParams(-1, -2));

        setContentView(rootScroll);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.rgb(0, 255, 128));
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setBackground(round(Color.rgb(0, 42, 28), dp(14), Color.rgb(0, 230, 118), 1));
        return b;
    }

    private void refreshStatus() {
        if (!pingBinderSafe()) { setStatus("Shizuku não está ativo. Abra o app Shizuku e inicie o serviço."); return; }
        boolean granted = checkPermissionSafe();
        Integer uid = invokeShizukuInt("getUid");
        String mode = uid != null && uid == 0 ? "ROOT" : (uid != null && uid == 2000 ? "ADB shell" : "UID " + uid);
        setStatus("Shizuku ativo • permissão: " + (granted ? "concedida" : "não concedida") + " • modo: " + mode + " • v1.0.0");
    }

    private void requestPermission() {
        if (!pingBinderSafe()) { setStatus("Shizuku não está ativo. Inicie o Shizuku primeiro."); return; }
        if (checkPermissionSafe()) { setStatus("Permissão Shizuku já concedida."); return; }
        try {
            if (Shizuku.isPreV11()) { setStatus("Versão antiga do Shizuku não suportada."); return; }
            Shizuku.requestPermission(REQUEST_PERMISSION_CODE);
        } catch (Throwable t) { setStatus("Erro ao pedir permissão: " + t.getMessage()); }
    }

    private void runCommand() {
        String command = normalizeCommand(commandEdit.getText().toString());
        if (command.trim().isEmpty()) { setOutput("Digite um comando."); return; }
        if (!pingBinderSafe()) { setOutput("Shizuku não está ativo."); return; }
        if (!checkPermissionSafe()) { setOutput("Permissão Shizuku não concedida. Toque em Permissão primeiro."); return; }

        runButton.setEnabled(false);
        runButton.setText("Executando...");
        setOutput("Executando:\n$ " + command);

        executor.execute(() -> {
            String result;
            try { result = executeViaShizukuShell(command); }
            catch (Throwable t) { result = "Erro: " + t; }
            String finalResult = result;
            mainHandler.post(() -> { setOutput(finalResult); runButton.setEnabled(true); runButton.setText("Executar"); });
        });
    }

    private String executeViaShizukuShell(String command) throws Exception {
        Process process = newShizukuProcess(new String[]{"/system/bin/sh", "-c", command});
        Future<String> stdoutFuture = executor.submit(() -> readAll(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(() -> readAll(process.getErrorStream()));
        Future<Integer> waitFuture = executor.submit(() -> process.waitFor());
        boolean timedOut = false;
        int exitCode;
        try { exitCode = waitFuture.get(60, TimeUnit.SECONDS); }
        catch (TimeoutException timeout) { timedOut = true; process.destroy(); exitCode = -1; }
        String stdout = safeGet(stdoutFuture);
        String stderr = safeGet(stderrFuture);
        return "$ " + command + "\nexitCode: " + exitCode + (timedOut ? " • timeout" : "") + "\n\nSTDOUT:\n" + empty(stdout) + "\n\nSTDERR:\n" + empty(stderr);
    }

    private Process newShizukuProcess(String[] command) throws Exception {
        Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);
        return (Process) method.invoke(null, (Object) command, null, null);
    }

    private String readAll(java.io.InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString().trim();
        }
    }

    private String safeGet(Future<String> future) {
        try { return future.get(3, TimeUnit.SECONDS); } catch (Throwable t) { return ""; }
    }

    private String empty(String text) { return text == null || text.isEmpty() ? "(vazio)" : text; }

    private String normalizeCommand(String command) {
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("adb shell")) return "";
        if (lower.startsWith("adb shell ")) return trimmed.substring("adb shell ".length()).trim();
        return trimmed;
    }

    private boolean pingBinderSafe() { try { return Shizuku.pingBinder(); } catch (Throwable ignored) { return false; } }
    private boolean checkPermissionSafe() { try { return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable ignored) { return false; } }
    private Integer invokeShizukuInt(String methodName) { try { Object v = Shizuku.class.getDeclaredMethod(methodName).invoke(null); return v instanceof Integer ? (Integer) v : null; } catch (Throwable ignored) { return null; } }
    private void setStatus(String text) { statusText.setText(text); }
    private void setOutput(String text) { outputText.setText(text); }
    private void addGap(LinearLayout root, int dp) { View gap = new View(this); root.addView(gap, new LinearLayout.LayoutParams(1, dp(dp))); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(dp(strokeWidth), strokeColor); return g; }
}
