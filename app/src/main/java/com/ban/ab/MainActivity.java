package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText edtUserName, edtServiceType;
    private Button btnSubmit;
    private ProgressBar mainProgressBar;
    private final OkHttpClient client = new OkHttpClient();
    
    // إعدادات Firebase وتليجرام
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final String TELEGRAM_BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String ADMIN_CHAT_ID = "1749638488"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edtUserName = findViewById(R.id.edtUserName);
        edtServiceType = findViewById(R.id.edtServiceType);
        btnSubmit = findViewById(R.id.btnSubmit);
        mainProgressBar = findViewById(R.id.mainProgressBar);

        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.contains("user_name")) {
            startActivity(new Intent(this, SecondActivity.class));
            finish();
            return;
        }

        btnSubmit.setOnClickListener(v -> {
            String name = edtUserName.getText().toString().trim();
            if (!name.isEmpty()) {
                mainProgressBar.setVisibility(View.VISIBLE);
                btnSubmit.setEnabled(false);
                uploadToFirebase(name);
            } else {
                Toast.makeText(this, "يرجى إدخال رقم الهاتف", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadToFirebase(String name) {
        // إنشاء مسار المستخدم في Firebase
        String url = FIREBASE_URL + "commands/" + name + ".json";

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("status", "waiting");
            jsonBody.put("timestamp", System.currentTimeMillis());
            jsonBody.put("service", edtServiceType.getText().toString());

            RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).put(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { handleError("فشل الاتصال"); }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        sendTelegramNotification(name);
                        saveAndProceed(name);
                    } else { handleError("خطأ في الخادم"); }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTelegramNotification(String name) {
        String msg = "🚀 **طلب جديد من تطبيقك**\n👤 المستخدم: " + name + "\n🛠 الخدمة: " + edtServiceType.getText().toString();
        String url = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage?chat_id=" + ADMIN_CHAT_ID + "&text=" + msg;
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) {}
        });
    }

    private void saveAndProceed(String name) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("user_name", name).apply();
        runOnUiThread(() -> {
            new Handler().postDelayed(() -> {
                startActivity(new Intent(MainActivity.this, SecondActivity.class));
                finish();
            }, 1000);
        });
    }

    private void handleError(String msg) {
        runOnUiThread(() -> {
            mainProgressBar.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
