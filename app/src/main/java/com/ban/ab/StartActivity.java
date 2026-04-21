package com.ban.ab;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class StartActivity extends AppCompatActivity {

    private String selectedProblem = "";
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تأكد أن الاسم مطابق لملف الـ XML (أحرف صغيرة)
        setContentView(R.layout.layout_start);

        btnStart = findViewById(R.id.btnStartNext);
        
        // إعداد المشاكل الثلاث (ربط المعرفات بشكل صحيح)
        setupProblemCard(R.id.cardProblem1, "مشكلة حظر واتساب الرسمي", R.drawable.img_prob1);
        setupProblemCard(R.id.cardProblem2, "مشكلة تسجيل الدخول غير متوفر", R.drawable.img_prob2);
        setupProblemCard(R.id.cardProblem3, "مشكلة حظر إنتهاك أو مشدد", R.drawable.img_prob3);

        // زر الفيديو الشرح (ضع رابط الفيديو الخاص بك هنا)
        findViewById(R.id.btnWatchTutorial).setOnClickListener(v -> 
                showVideoPopup("https://www.youtube.com/embed/YOUR_VIDEO_ID"));

        btnStart.setOnClickListener(v -> {
            if (selectedProblem.isEmpty()) {
                Toast.makeText(this, "يرجى اختيار نوع المشكلة أولاً", Toast.LENGTH_SHORT).show();
                return;
            }
            // الانتقال لصفحة MainActivity مع إرسال نوع المشكلة
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("selected_problem", selectedProblem);
            startActivity(intent);
        });
    }

    private void setupProblemCard(int cardId, String title, int imageRes) {
        MaterialCardView card = findViewById(cardId);
        
        // حل مشكلة الـ NullPointerException من خلال تحديد الـ IDs الصحيحة بناءً على البطاقة
        ImageView img;
        TextView txt;

        if (cardId == R.id.cardProblem1) {
            img = card.findViewById(R.id.imgThumb);
            txt = card.findViewById(R.id.txtTitle);
        } else if (cardId == R.id.cardProblem2) {
            img = card.findViewById(R.id.imgThumb2);
            txt = card.findViewById(R.id.txtTitle2);
        } else {
            img = card.findViewById(R.id.imgThumb3);
            txt = card.findViewById(R.id.txtTitle3);
        }

        // تعيين البيانات فقط إذا تم العثور على العناصر
        if (img != null) {
            img.setImageResource(imageRes);
            // عند الضغط على الصورة للتكبير
            img.setOnClickListener(v -> showImagePopup(imageRes));
        }
        
        if (txt != null) {
            txt.setText(title);
        }

        // عند الضغط على البطاقة للاختيار
        card.setOnClickListener(v -> {
            selectedProblem = title;
            resetCardsHighlight();
            // تلوين البطاقة المختارة بالأخضر الرسمي الهادئ
            card.setCardBackgroundColor(Color.parseColor("#E8F5E9")); 
            card.setStrokeColor(Color.parseColor("#2E7D32"));
            card.setStrokeWidth(6);
        });
    }

    private void resetCardsHighlight() {
        // إعادة جميع البطاقات للوضع الافتراضي (أبيض وبدون إطار)
        int[] ids = {R.id.cardProblem1, R.id.cardProblem2, R.id.cardProblem3};
        for (int id : ids) {
            MaterialCardView c = findViewById(id);
            if (c != null) {
                c.setStrokeWidth(0);
                c.setCardBackgroundColor(Color.WHITE);
            }
        }
    }

    private void showImagePopup(int resId) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup_image);
        ImageView fullImg = dialog.findViewById(R.id.imgFull);
        if (fullImg != null) fullImg.setImageResource(resId);
        if (dialog.getWindow() != null) 
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    private void showVideoPopup(String videoUrl) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup_video);
        WebView webView = dialog.findViewById(R.id.videoWebView);
        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebChromeClient(new WebChromeClient());
            webView.loadUrl(videoUrl);
        }
        if (dialog.getWindow() != null)
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }
}
