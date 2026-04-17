package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText nameInput, phoneInput, packageField;
    private Button sendBtn;
    
    // املأ بيانات البوت الخاص بك هنا
    private final String BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";
    private final String CHAT_ID = "YOUR_CHAT_ID_HERE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. فحص الحالة: إذا كان المستخدم قد أرسل الطلب الأول مسبقاً، يذهب للصفحة الثانية فوراً
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.getInt("current_step", 0) == 1) {
            startActivity(new Intent(this, SecondActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // 2. تعريف العناصر
        nameInput = findViewById(R.id.nameInput);
        packageField = findViewById(R.id.packageField);
        sendBtn = findViewById(R.id.sendBtn);

        // 3. جلب الحزمة المثبتة تلقائياً عند فتح التطبيق
        String detectedPkg = getInstalledSupportedPackage();
        packageField.setText(detectedPkg);

        // 4. برمجة زر الإرسال
        sendBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String pkg = packageField.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال الاسم أولاً", Toast.LENGTH_SHORT).show();
            } else {
                sendToTelegram(name, pkg);
            }
        });
    }

    // دالة لفحص نسخ الواتساب المثبتة
    private String getInstalledSupportedPackage() {
        String[] supportedPackages = {
            "com.universe.messenger",
            "com.nasa",
            "com.whatsapp",
            "com.abdullahtamemi"
        };

        PackageManager pm = getPackageManager();
        for (String pkg : supportedPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg; // يعيد أول حزمة يجدها
            } catch (PackageManager.NameNotFoundException e) {
                // الحزمة غير موجودة، يكمل البحث
            }
        }
        return "لم يتم العثور على حزمة متوافقة";
    }

    // دالة إرسال البيانات لتليجرام عبر OkHttp
    private void sendToTelegram(String name, String pkg) {
        String messageText = "📦 *طلب جديد من التطبيق* \n\n" +
                             "👤 *الاسم:* " + name + "\n" +
                             "📱 *الحزمة المكتشفة:* " + pkg;

        OkHttpClient client = new OkHttpClient();

        HttpUrl url = HttpUrl.parse("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                .newBuilder()
                .addQueryParameter("chat_id", CHAT_ID)
                .addQueryParameter("text", messageText)
                .addQueryParameter("parse_mode", "Markdown")
                .build();

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "فشل الاتصال بالإنترنت", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // حفظ الحالة ونقله للمرحلة التالية
                    SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                    editor.putInt("current_step", 1);
                    editor.apply();

                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "تم إرسال طلبك بنجاح", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, SecondActivity.class));
                        finish();
                    });
                }
            }
        });
    }
}
