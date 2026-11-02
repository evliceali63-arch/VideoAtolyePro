package com.videoatolye.pro;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private LinearLayout layout;
    private ImageView preview;
    private Bitmap photo;
    private int rotation = 0;
    private Uri video;
    private static final int PHOTO = 101, VIDEO = 102, SAVE = 103;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        home();
    }
    private void screen(String heading) {
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 28, 24, 24);
        layout.setBackgroundColor(0xff10141e);
        scroll.addView(layout);
        setContentView(scroll);
        button("← Ana sayfa", () -> home());
        label(heading, 26);
    }
    private void label(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(0xffffffff);
        text.setTextSize(size);
        text.setPadding(10, 16, 10, 16);
        layout.addView(text);
    }
    private void button(String name, Runnable action) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        layout.addView(button);
        button.setOnClickListener(v -> action.run());
    }
    private void home() {
        screen("Video Atölye Pro");
        label("v0.1 • Android başlangıç sürümü", 15);
        button("📸 Fotoğraf düzenle", () -> photoEditor());
        button("🎬 Video seç", () -> videoEditor());
        button("✨ Otomatik montaj (geliştiriliyor)", () -> planned("Otomatik montaj"));
        button("✍️ Yazarak montaj (geliştiriliyor)", () -> planned("Yazarak montaj"));
        button("🎵 Ses ve efekt kütüphanesi (geliştiriliyor)", () -> planned("Ses ve efekt kütüphanesi"));
    }
    private void planned(String name) {
        screen(name);
        label("Bu özellik henüz uygulanmadı. Gelecek sürümde geliştirilecek.", 16);
    }
    private void pick(String mime, int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mime);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, request);
    }
    private void photoEditor() {
        screen("Fotoğraf editörü");
        button("Galeriden fotoğraf aç", () -> pick("image/*", PHOTO));
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setMinimumHeight(250);
        layout.addView(preview);
        if (photo != null) showPhoto();
        button("90° döndür", () -> { rotation = (rotation + 90) % 360; showPhoto(); });
        button("PNG olarak kaydet", () -> {
            if (photo == null) { toast("Önce fotoğraf seç"); return; }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("image/png");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "video_atolye_fotograf.png");
            startActivityForResult(intent, SAVE);
        });
    }
    private Bitmap rotated() {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);
    }
    private void showPhoto() {
        if (preview != null && photo != null) preview.setImageBitmap(rotated());
    }
    private void videoEditor() {
        screen("Video editörü");
        button("Galeriden video seç", () -> pick("video/*", VIDEO));
        label(video == null ? "Henüz video seçilmedi" : "Video seçildi: " + video.getLastPathSegment(), 16);
        label("Video kırpma, katmanlar ve dışa aktarma sonraki sürümde eklenecek.", 15);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == PHOTO) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, options);
                }
                int sample = 1;
                while (options.outWidth / sample > 2048 || options.outHeight / sample > 2048) sample *= 2;
                options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    photo = BitmapFactory.decodeStream(input, null, options);
                }
                rotation = 0;
                photoEditor();
            } else if (request == VIDEO) {
                video = uri;
                videoEditor();
            } else if (request == SAVE && photo != null) {
                Bitmap bitmap = rotated();
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Çıktı açılamadı");
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                if (bitmap != photo) bitmap.recycle();
                toast("Fotoğraf kaydedildi");
            }
        } catch (Exception error) { toast("İşlem başarısız: " + error.getMessage()); }
    }
}
