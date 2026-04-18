package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
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
    
    // ملاحظة: الـ Token والـ Repo Path يتم حقنهما أو كتابتهما هنا
    private final String GITHUB_TOKEN = "YOUR_TOKEN_HERE"; 
    private final String GITHUB_REPO_PATH = "abdullah14120/ban";

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
                checkAndCreateJson(name);
            } else {
                Toast.makeText(this, "يرجى إدخال رقم الهوية", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAndCreateJson(String name) {
        String url = "https://api.github.com/repos/" + GITHUB_REPO_PATH + "/contents/commands/" + name + ".json";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + GITHUB_TOKEN)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleError("فشل الاتصال بالسيرفر");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String sha = null;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        sha = json.getString("sha");
                    } catch (Exception ignored) {}
                }
                uploadToGithub(url, name, sha);
            }
        });
    }

    private void uploadToGithub(String url, String name, String sha) {
        try {
            JSONObject contentJson = new JSONObject();
            contentJson.put("status", "waiting");
            contentJson.put("timestamp", System.currentTimeMillis());

            String encodedContent = Base64.encodeToString(contentJson.toString().getBytes(), Base64.NO_WRAP);

            JSONObject payload = new JSONObject();
            payload.put("message", "New Request from App");
            payload.put("content", encodedContent);
            if (sha != null) payload.put("sha", sha);

            RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "token " + GITHUB_TOKEN)
                    .put(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { handleError("فشل الرفع"); }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        saveAndProceed(name);
                    } else {
                        handleError("خطأ في استجابة GitHub");
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveAndProceed(String name) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("user_name", name).apply();
        runOnUiThread(() -> {
            new Handler().postDelayed(() -> {
                startActivity(new Intent(MainActivity.this, SecondActivity.class));
                finish();
            }, 1500);
        });
    }

    private void handleError(String msg) {
        runOnUiThread(() -> {
            mainProgressBar.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
