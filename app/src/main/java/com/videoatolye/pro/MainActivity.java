package com.videoatolye.pro;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.media.MediaFormat;
import android.media.MediaCodec;
import android.media.MediaMetadataRetriever;
import android.widget.VideoView;
import android.widget.SeekBar;
import java.nio.ByteBuffer;
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
    private long videoDurationMs = 0;
    private int trimStart = 0, trimEnd = 100;
    private int brightness = 0;
    private VideoView videoPreview;
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
        label("Parlaklık", 16);
        SeekBar light = new SeekBar(this);
        light.setMax(200);
        light.setProgress(brightness + 100);
        layout.addView(light);
        light.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) { brightness = value - 100; showPhoto(); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
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
        if (preview != null && photo != null) {
            preview.setImageBitmap(rotated());
            ColorMatrix cm = new ColorMatrix(new float[]{1,0,0,0,brightness, 0,1,0,0,brightness, 0,0,1,0,brightness, 0,0,0,1,0});
            preview.setColorFilter(new ColorMatrixColorFilter(cm));
        }
    }
    private void videoEditor() {
        screen("Video editörü");
        button("Galeriden video seç", () -> pick("video/*", VIDEO));
        if (video == null) { label("Henüz video seçilmedi", 16); return; }
        videoPreview = new VideoView(this);
        layout.addView(videoPreview, new LinearLayout.LayoutParams(-1, 650));
        videoPreview.setVideoURI(video);
        videoPreview.setOnPreparedListener(mp -> {
            videoDurationMs = mp.getDuration();
            mp.setLooping(true);
            videoPreview.start();
        });
        button("▶ Oynat / Duraklat", () -> {
            if (videoPreview.isPlaying()) videoPreview.pause(); else videoPreview.start();
        });
        label("Başlangıç (%)", 16);
        SeekBar start = new SeekBar(this);
        start.setMax(99); start.setProgress(trimStart);
        layout.addView(start);
        label("Bitiş (%)", 16);
        SeekBar end = new SeekBar(this);
        end.setMax(100); end.setProgress(trimEnd);
        layout.addView(end);
        start.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar v,int p,boolean u) {
                trimStart = p;
                if (u && videoDurationMs > 0) videoPreview.seekTo((int)(videoDurationMs*p/100));
            }
            public void onStartTrackingTouch(SeekBar v) {}
            public void onStopTrackingTouch(SeekBar v) {}
        });
        end.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar v,int p,boolean u) { trimEnd = p; }
            public void onStartTrackingTouch(SeekBar v) {}
            public void onStopTrackingTouch(SeekBar v) {}
        });
        button("Kırpılmış MP4'ü kaydet", () -> {
            if (trimEnd <= trimStart) { toast("Bitiş başlangıçtan sonra olmalı"); return; }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("video/mp4");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "video_atolye_kirpildi.mp4");
            startActivityForResult(intent, 104);
        });
        label("Not: Kırpma en yakın anahtar kareden başlayabilir. Bu sürümde görüntü döndürme ve katmanlar henüz yok.", 14);
    }
    private void trimVideo(Uri destination) {
        final Uri source = video;
        final long startUs = videoDurationMs * 1000L * trimStart / 100L;
        final long endUs = videoDurationMs * 1000L * trimEnd / 100L;
        new Thread(() -> {
            MediaExtractor extractor = new MediaExtractor();
            MediaMuxer muxer = null;
            android.os.ParcelFileDescriptor output = null;
            try {
                extractor.setDataSource(this, source, null);
                output = getContentResolver().openFileDescriptor(destination, "w");
                if (output == null) throw new IllegalStateException("Hedef dosya açılamadı");
                muxer = new MediaMuxer(output.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int count = extractor.getTrackCount();
                int[] mapping = new int[count];
                for (int i=0;i<count;i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                        mapping[i] = muxer.addTrack(format);
                        extractor.selectTrack(i);
                    } else mapping[i] = -1;
                }
                muxer.start();
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                ByteBuffer buffer = ByteBuffer.allocateDirect(8*1024*1024);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    int track = extractor.getSampleTrackIndex();
                    if (track < 0) break;
                    long time = extractor.getSampleTime();
                    if (time >= endUs) break;
                    if (mapping[track] >= 0) {
                        buffer.clear();
                        int size = extractor.readSampleData(buffer,0);
                        if (size < 0) break;
                        info.offset=0; info.size=size;
                        info.presentationTimeUs=time;
                        info.flags=extractor.getSampleFlags();
                        muxer.writeSampleData(mapping[track],buffer,info);
                    }
                    if (!extractor.advance()) break;
                }
                muxer.stop();
                runOnUiThread(() -> toast("MP4 kaydedildi"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Video kaydedilemedi: " + e.getMessage()));
            } finally {
                extractor.release();
                if (muxer != null) try { muxer.release(); } catch(Exception ignored) {}
                if (output != null) try { output.close(); } catch(Exception ignored) {}
            }
        }).start();
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
                brightness = 0;
                photoEditor();
            } else if (request == VIDEO) {
                video = uri;
                videoEditor();
            } else if (request == 104 && video != null) {
                trimVideo(uri);
            } else if (request == SAVE && photo != null) {
                Bitmap bitmap = rotated();
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Çıktı açılamadı");
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{1,0,0,0,brightness,0,1,0,0,brightness,0,0,1,0,brightness,0,0,0,1,0})));
                    Bitmap processedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    new android.graphics.Canvas(processedBitmap).drawBitmap(bitmap, 0, 0, paint);
                    processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    processedBitmap.recycle();
                }
                if (bitmap != photo) bitmap.recycle();
                toast("Fotoğraf kaydedildi");
            }
        } catch (Exception error) { toast("İşlem başarısız: " + error.getMessage()); }
    }
}
