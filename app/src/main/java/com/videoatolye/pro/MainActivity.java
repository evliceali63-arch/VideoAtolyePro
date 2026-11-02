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
    private int contrast = 100, saturation = 100;
    private boolean flipHorizontal = false;
    private boolean flipVertical = false;
    private int cropMode = 0;
    private int grayscale = 0;
    private String photoCaption = "";
    private int captionSize = 42;
    private boolean videoMuted = false;
    private float videoSpeed = 1f;
    private boolean exportMute = false;
    private int videoRotation = 0;
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
        label("Video Atölye Pro • Geliştirme sürümü", 15);
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
    private LinearLayout photoTools;
    private void toolButton(LinearLayout target,String name,Runnable action) {
        Button b=new Button(this); b.setText(name); b.setAllCaps(false);
        target.addView(b); b.setOnClickListener(v->action.run());
    }
    private SeekBar toolSlider(LinearLayout target,String name,int max,int value,java.util.function.IntConsumer changed) {
        TextView t=new TextView(this); t.setText(name);t.setTextColor(0xffffffff);t.setTextSize(15);t.setPadding(10,8,10,2);target.addView(t);
        SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(value);target.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar v,int p,boolean fromUser) {if(fromUser) changed.accept(p);}
            public void onStartTrackingTouch(SeekBar v) {}
            public void onStopTrackingTouch(SeekBar v) {}
        });
        return bar;
    }
    private void photoTab(String tab) {
        photoTools.removeAllViews();
        if(tab.equals("Kadraj")) {
            toolButton(photoTools,"90° döndür",()->{rotation=(rotation+90)%360;showPhoto();});
            toolButton(photoTools,"Yatay çevir",()->{flipHorizontal=!flipHorizontal;showPhoto();});
            toolButton(photoTools,"Dikey çevir",()->{flipVertical=!flipVertical;showPhoto();});
            String[] names={"Serbest","1:1 kare","4:5","9:16","16:9"};
            for(int i=0;i<names.length;i++) {
                final int mode=i;
                toolButton(photoTools,names[i],()->{cropMode=mode;showPhoto();});
            }
        } else if(tab.equals("Renk")) {
            toolSlider(photoTools,"Parlaklık",200,brightness+100,p->{brightness=p-100;showPhoto();});
            toolSlider(photoTools,"Kontrast",200,contrast,p->{contrast=p;showPhoto();});
            toolSlider(photoTools,"Doygunluk",200,saturation,p->{saturation=p;showPhoto();});
            toolButton(photoTools,"Siyah-beyaz",()->{grayscale=grayscale==0?1:0;showPhoto();});
        } else if(tab.equals("Yazı")) {
            EditText field=new EditText(this);
            field.setSingleLine(true);field.setTextColor(0xffffffff);field.setHintTextColor(0xffbbbbbb);
            field.setHint("Fotoğraf üzerine yazı");field.setText(photoCaption);photoTools.addView(field);
            toolButton(photoTools,"Yazıyı uygula",()->{photoCaption=field.getText().toString();showPhoto();});
            toolButton(photoTools,"Yazıyı kaldır",()->{photoCaption="";field.setText("");showPhoto();});
            toolSlider(photoTools,"Yazı boyutu",100,captionSize,p->{captionSize=Math.max(12,p);showPhoto();});
        } else if(tab.equals("Kaydet")) {
            toolButton(photoTools,"PNG olarak kaydet",()->savePhoto("image/png","video_atolye_fotograf.png",SAVE));
            toolButton(photoTools,"JPEG olarak kaydet",()->savePhoto("image/jpeg","video_atolye_fotograf.jpg",107));
            toolButton(photoTools,"Bütün ayarları sıfırla",()->{
                rotation=0;brightness=0;contrast=100;saturation=100;
                flipHorizontal=false;flipVertical=false;cropMode=0;grayscale=0;photoCaption="";captionSize=42;
                showPhoto();photoTab("Kaydet");
            });
        }
    }
    private void savePhoto(String type,String name,int code) {
        if(photo==null){toast("Önce fotoğraf seç");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,code);
    }
    private void photoEditor() {
        screen("Fotoğraf editörü");
        button("Galeriden fotoğraf aç",()->pick("image/*",PHOTO));
        preview=new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(0xff080a10);
        layout.addView(preview,new LinearLayout.LayoutParams(-1,photo==null?340:540));
        if(photo!=null)showPhoto();
        HorizontalScrollView tabs=new HorizontalScrollView(this);
        tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow=new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabRow);layout.addView(tabs);
        for(String tab:new String[]{"Kadraj","Renk","Yazı","Kaydet"}) {
            Button item=new Button(this);item.setText(tab);item.setAllCaps(false);
            tabRow.addView(item);item.setOnClickListener(v->photoTab(tab));
        }
        photoTools=new LinearLayout(this);
        photoTools.setOrientation(LinearLayout.VERTICAL);
        layout.addView(photoTools);
        photoTab("Kadraj");
    }
    private Bitmap rotated() {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        if (flipHorizontal || flipVertical) matrix.postScale(flipHorizontal ? -1f : 1f, flipVertical ? -1f : 1f);
        Bitmap transformed = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);
        if (cropMode == 0) return transformed;
        float[] ratios = {0f,1f,4f/5f,9f/16f,16f/9f};
        float target = ratios[cropMode];
        int w = transformed.getWidth(), h = transformed.getHeight();
        int cropW = w, cropH = h;
        if ((float)w/h > target) cropW = Math.max(1,Math.round(h*target));
        else cropH = Math.max(1,Math.round(w/target));
        Bitmap cropped = Bitmap.createBitmap(transformed,(w-cropW)/2,(h-cropH)/2,cropW,cropH);
        if (cropped != transformed && transformed != photo) transformed.recycle();
        return cropped;
    }
    private ColorMatrixColorFilter photoFilter() {
        float c = contrast / 100f;
        float offset = (1f-c)*127.5f + brightness;
        ColorMatrix matrix = new ColorMatrix(new float[]{
            c,0,0,0,offset,
            0,c,0,0,offset,
            0,0,c,0,offset,
            0,0,0,1,0
        });
        ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(grayscale == 1 ? 0f : saturation / 100f);
        matrix.postConcat(saturationMatrix);
        return new ColorMatrixColorFilter(matrix);
    }
    private Bitmap renderPhoto() {
        Bitmap bitmap = rotated();
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(photoFilter());
        canvas.drawBitmap(bitmap,0,0,paint);
        if (!photoCaption.isEmpty()) {
            android.graphics.Paint shadow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float size = Math.max(20f, output.getWidth() * captionSize / 900f);
            shadow.setTextSize(size);
            shadow.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            shadow.setTextAlign(android.graphics.Paint.Align.CENTER);
            shadow.setColor(0xffffffff);
            shadow.setShadowLayer(size/8f,2f,2f,0xff000000);
            float x = output.getWidth()/2f, y = output.getHeight()*0.86f;
            canvas.drawText(photoCaption,x,y,shadow);
        }
        if (bitmap != photo) bitmap.recycle();
        return output;
    }
    private void showPhoto() {
        if (preview != null && photo != null) {
            preview.setImageBitmap(renderPhoto());
            preview.clearColorFilter();
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
        button("⏪ 10 saniye geri", () -> videoPreview.seekTo(Math.max(0,videoPreview.getCurrentPosition()-10000)));
        button("⏩ 10 saniye ileri", () -> videoPreview.seekTo(Math.min(videoPreview.getDuration(),videoPreview.getCurrentPosition()+10000)));
        button("🔇 Sesi aç / kapat", () -> {
            videoMuted = !videoMuted;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                videoPreview.setOnPreparedListener(mp -> {
                    mp.setVolume(videoMuted ? 0f : 1f,videoMuted ? 0f : 1f);
                    mp.setLooping(true);
                    mp.start();
                });
                videoPreview.setVideoURI(video);
            } else toast("Ses kontrolü bu Android sürümünde desteklenmiyor");
        });
        button("Başa sar", () -> videoPreview.seekTo(0));
        button("Dışa aktarmada sesi kaldır: " + (exportMute ? "EVET" : "HAYIR"), () -> { exportMute = !exportMute; videoEditor(); });
        button("Dışa aktarmada 90° döndür", () -> { videoRotation=(videoRotation+90)%360; toast("Döndürme: "+videoRotation+" derece"); });
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
                muxer.setOrientationHint(videoRotation);
                int count = extractor.getTrackCount();
                int[] mapping = new int[count];
                for (int i=0;i<count;i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && (mime.startsWith("video/") || (!exportMute && mime.startsWith("audio/")))) {
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
                contrast = 100; saturation = 100; flipHorizontal = false; flipVertical = false; cropMode = 0; grayscale = 0; photoCaption = ""; captionSize = 42;
                photoEditor();
            } else if (request == VIDEO) {
                video = uri;
                videoEditor();
            } else if (request == 104 && video != null) {
                trimVideo(uri);
            } else if ((request == SAVE || request == 107) && photo != null) {
                Bitmap bitmap = renderPhoto();
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Çıktı açılamadı");
                    bitmap.compress(request == 107 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, request == 107 ? 92 : 100, output);
                }
                bitmap.recycle();
                toast("Fotoğraf kaydedildi");
            }
        } catch (Exception error) { toast("İşlem başarısız: " + error.getMessage()); }
    }
}
