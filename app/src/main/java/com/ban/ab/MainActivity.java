package com.ban.ab;

import android.annotation.SuppressLint;
import android.app.Dialog;
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
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText edtUserName, edtServiceType;
    private Button btnSubmit;
    private ProgressBar mainProgressBar;
    private final OkHttpClient client = new OkHttpClient();
    
    // إعدادات Firebase وتليجرام
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final String TELEGRAM_BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String ADMIN_CHAT_ID = "1749638488"; 

    // أدوات المحادثة والتسجيل
    private MediaRecorder recorder;
    private String voiceFileName;
    private FloatingActionButton fabChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkFilePermissions();

        edtUserName = findViewById(R.id.edtUserName);
        edtServiceType = findViewById(R.id.edtServiceType);
        btnSubmit = findViewById(R.id.btnSubmit);
        mainProgressBar = findViewById(R.id.mainProgressBar);

        // التحقق من الجلسة السابقة
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.contains("user_name")) {
            startActivity(new Intent(this, SecondActivity.class));
            finish();
            return;
        }

        setupFloatingChatButton(); // إعداد زر الدعم الفني العائم

        btnSubmit.setOnClickListener(v -> {
            String name = edtUserName.getText().toString().trim();
            if (!name.isEmpty()) {
                mainProgressBar.setVisibility(View.VISIBLE);
                btnSubmit.setEnabled(false);
                uploadToFirebase(name);
            } else {
                Toast.makeText(this, "يرجى إدخال رقم الهاتف", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkFilePermissions() {
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

    // --- نظام المحادثة المنبثقة (نفس الموجود في SecondActivity لضمان التطابق) ---

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
        chatDialog.setContentView(R.layout.chat_layout); 
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
        String name = edtUserName.getText().toString().isEmpty() ? "زائر جديد" : edtUserName.getText().toString();
        String fullMsg = "💬 **رسالة دعم من:** " + name + "\n\n" + msg;
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode(fullMsg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تم إرسال رسالتك ✅", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startVoiceRecording() {
        try {
            voiceFileName = getExternalCacheDir().getAbsolutePath() + "/v_main_temp.m4a";
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
        String name = edtUserName.getText().toString().isEmpty() ? "زائر جديد" : edtUserName.getText().toString();
        File file = new File(filePath);
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("audio/m4a"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("voice", file.getName(), fileBody)
                .addFormDataPart("chat_id", ADMIN_CHAT_ID)
                .addFormDataPart("caption", "🎤 بصمة صوتية من: " + name)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendVoice")
                .post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تم إرسال الصوت ✅", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // --- نهاية نظام المحادثة ---

    private void uploadToFirebase(String name) {
        String url = FIREBASE_URL + "commands/" + name + ".json";
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("status", "waiting");
            jsonBody.put("timestamp", System.currentTimeMillis());
            jsonBody.put("service", edtServiceType.getText().toString());

            RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).put(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { handleError("فشل الاتصال"); }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        sendTelegramNotification(name);
                        saveAndProceed(name);
                    } else { handleError("خطأ في الخادم"); }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTelegramNotification(String name) {
        String msg = "🚀 **طلب جديد من تطبيقك**\n👤 المستخدم: " + name + "\n🛠 الخدمة: " + edtServiceType.getText().toString();
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode(msg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void saveAndProceed(String name) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("user_name", name).apply();
        runOnUiThread(() -> {
            new Handler().postDelayed(() -> {
                startActivity(new Intent(MainActivity.this, SecondActivity.class));
                finish();
            }, 1000);
        });
    }

    private void handleError(String msg) {
        runOnUiThread(() -> {
            mainProgressBar.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
