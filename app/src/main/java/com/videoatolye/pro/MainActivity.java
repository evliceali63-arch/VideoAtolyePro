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
    private int warmth = 0;
    private int vignette = 0;
    private int photoPreset = 0;
    private boolean videoMuted = false;
    private float videoSpeed = 1f;
    private boolean exportMute = false;
    private int videoRotation = 0;
    private VideoView videoPreview;
    private static final int PHOTO = 101, VIDEO = 102, SAVE = 103, AUDIO = 108;
    private Uri audioUri;
    private android.media.MediaPlayer audioPlayer;

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
        if(android.os.Build.VERSION.SDK_INT>=35){
            scroll.setBackgroundColor(0xff10141e);
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
            });
        }
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
        button("📸 Fotoğraf Stüdyosu", () -> startActivity(new Intent(this, PhotoEditorActivity.class)));
        button("🎬 Video seç", () -> videoEditor());
        button("🎞 Çoklu video montaj", () -> montageStudio());
        button("✨ Otomatik montaj (geliştiriliyor)", () -> planned("Otomatik montaj"));
        button("✍️ Yazarak montaj (geliştiriliyor)", () -> planned("Yazarak montaj"));
        button("🎵 Ses stüdyosu", () -> audioStudio());
    }

    private final java.util.ArrayList<Uri> montageClips = new java.util.ArrayList<>();
    private static final int MONTAGE_PICK=109, MONTAGE_SAVE=110;
    private void montageStudio(){
        screen("Çoklu Video Montaj");
        label("Birden fazla klip seç, sırasını değiştir ve uyumlu klipleri tek MP4 dosyasında birleştir.",16);
        button("＋ Video ekle",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(i,MONTAGE_PICK);
        });
        for(int n=0;n<montageClips.size();n++){
            final int index=n;
            label((n+1)+". "+montageClips.get(n).getLastPathSegment(),14);
            button("↑ Öne taşı",()->{if(index>0){java.util.Collections.swap(montageClips,index,index-1);montageStudio();}});
            button("✕ Çıkar",()->{montageClips.remove(index);montageStudio();});
        }
        button("Listeyi temizle",()->{montageClips.clear();montageStudio();});
        button("⬇ MP4 birleştir",()->{
            if(montageClips.size()<2){toast("En az iki video ekle");return;}
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("video/mp4");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"video_atolye_montaj.mp4");
            startActivityForResult(i,MONTAGE_SAVE);
        });
        label("Hızlı birleştirme: aynı kodek, çözünürlük ve ses biçimindeki klipler desteklenir. Geçiş efektleri ve farklı biçimleri dönüştürme henüz yok.",14);
    }
    private void exportMontage(Uri destination){
        final java.util.ArrayList<Uri> clips=new java.util.ArrayList<>(montageClips);
        new Thread(()->{
            MediaMuxer muxer=null;
            android.os.ParcelFileDescriptor output=null;
            boolean started=false,done=false;
            try{
                output=getContentResolver().openFileDescriptor(destination,"w");
                if(output==null)throw new Exception("Çıktı açılamadı");
                muxer=new MediaMuxer(output.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                java.util.ArrayList<MediaFormat> reference=new java.util.ArrayList<>();
                int[] outputTracks=null;
                long timeline=0;
                for(int clip=0;clip<clips.size();clip++){
                    MediaExtractor extractor=new MediaExtractor();
                    try{
                        extractor.setDataSource(this,clips.get(clip),null);
                        java.util.ArrayList<MediaFormat> formats=new java.util.ArrayList<>();
                        java.util.ArrayList<Integer> tracks=new java.util.ArrayList<>();
                        for(int t=0;t<extractor.getTrackCount();t++){
                            MediaFormat format=extractor.getTrackFormat(t);
                            String mime=format.getString(MediaFormat.KEY_MIME);
                            if(mime!=null&&(mime.startsWith("video/")||mime.startsWith("audio/"))){
                                formats.add(format);tracks.add(t);
                            }
                        }
                        if(formats.isEmpty())throw new Exception("Klipte medya izi yok");
                        if(clip==0){
                            outputTracks=new int[formats.size()];
                            for(int t=0;t<formats.size();t++){
                                reference.add(formats.get(t));
                                outputTracks[t]=muxer.addTrack(formats.get(t));
                            }
                            muxer.start();started=true;
                        }else{
                            if(formats.size()!=reference.size())throw new Exception("Kliplerin iz sayısı farklı");
                            for(int t=0;t<formats.size();t++){
                                MediaFormat a=reference.get(t),b=formats.get(t);
                                if(!a.getString(MediaFormat.KEY_MIME).equals(b.getString(MediaFormat.KEY_MIME)))
                                    throw new Exception("Kliplerin kodekleri farklı");
                                for(String key:new String[]{MediaFormat.KEY_WIDTH,MediaFormat.KEY_HEIGHT,MediaFormat.KEY_SAMPLE_RATE,MediaFormat.KEY_CHANNEL_COUNT}){
                                    if(a.containsKey(key)!=b.containsKey(key)||(a.containsKey(key)&&a.getInteger(key)!=b.getInteger(key)))
                                        throw new Exception("Kliplerin biçimleri farklı");
                                }
                            }
                        }
                        long clipLength=0;
                        ByteBuffer buffer=ByteBuffer.allocateDirect(8*1024*1024);
                        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                        for(int t=0;t<tracks.size();t++){
                            extractor.selectTrack(tracks.get(t));
                            extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            long first=-1,last=-1;
                            while(extractor.getSampleTrackIndex()>=0){
                                if(extractor.getSampleTrackIndex()!=tracks.get(t)){
                                    extractor.advance();continue;
                                }
                                long stamp=extractor.getSampleTime();
                                if(first<0)first=stamp;
                                buffer.clear();
                                int size=extractor.readSampleData(buffer,0);
                                if(size<0)break;
                                info.offset=0;info.size=size;
                                info.presentationTimeUs=timeline+Math.max(0,stamp-first);
                                if((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new java.io.IOException("Şifreli medya desteklenmiyor");
                                info.flags=(extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC)!=0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                                if((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME)!=0)info.flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
                                muxer.writeSampleData(outputTracks[t],buffer,info);
                                last=stamp;
                                if(!extractor.advance())break;
                            }
                            if(last>=first&&first>=0)clipLength=Math.max(clipLength,last-first+33333);
                            extractor.unselectTrack(tracks.get(t));
                            extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        }
                        timeline+=Math.max(33333,clipLength);
                    }finally{extractor.release();}
                }
                muxer.stop();started=false;done=true;
                runOnUiThread(()->toast("Montaj MP4 kaydedildi"));
            }catch(Exception e){
                final String message=e.getMessage();
                runOnUiThread(()->toast("Birleştirme başarısız: "+message));
            }finally{
                if(muxer!=null)try{if(started)muxer.stop();}catch(Exception ignored){}
                if(muxer!=null)try{muxer.release();}catch(Exception ignored){}
                if(output!=null)try{output.close();}catch(Exception ignored){}
            }
        }).start();
    }
    private void audioStudio() {
        screen("Ses Stüdyosu");
        button("＋ Telefondan müzik veya ses seç", () -> pick("audio/*", AUDIO));
        if (audioUri == null) {
            label("Telefonundan bir ses dosyası seç. Oynat, duraklat ve ses seviyesini ayarla.", 16);
            return;
        }
        label("Seçili ses: " + audioUri.getLastPathSegment(), 15);
        button("▶ Oynat", () -> {
            try {
                if (audioPlayer == null) {
                    audioPlayer = new android.media.MediaPlayer();
                    audioPlayer.setDataSource(this, audioUri);
                    audioPlayer.prepare();
                }
                audioPlayer.start();
            } catch (Exception e) { releaseAudio(); toast("Ses açılamadı: " + e.getMessage()); }
        });
        button("⏸ Duraklat", () -> { if (audioPlayer != null && audioPlayer.isPlaying()) audioPlayer.pause(); });
        button("⏹ Durdur", () -> {
            if (audioPlayer != null) {
                audioPlayer.pause();
                audioPlayer.seekTo(0);
            }
        });
        label("Ses seviyesi", 16);
        SeekBar volume = new SeekBar(this);
        volume.setMax(100); volume.setProgress(100); layout.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (audioPlayer != null) audioPlayer.setVolume(value / 100f, value / 100f);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        label("Bu stüdyo seçilen ses dosyasını oynatır. Video üzerine ses ekleme henüz uygulanmadı.", 14);
    }
    private void releaseAudio() {
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
    }
    @Override protected void onDestroy() {
        releaseAudio();
        super.onDestroy();
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
        screen("Fotoğraf Stüdyosu");
        button("＋ Fotoğraf aç", () -> pick("image/*", PHOTO));
        if (photo == null) {
            label("Düzenlemek için galeriden bir fotoğraf seç.", 16);
            return;
        }
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(0xff080a0f);
        layout.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        showPhoto();
        button("↶ Tüm düzenlemeleri sıfırla", () -> {
            rotation=0; brightness=0; contrast=100; saturation=100;
            warmth=0; vignette=0; grayscale=0; cropMode=0;
            flipHorizontal=false; flipVertical=false;
            photoCaption=""; captionSize=42; photoPreset=0; photoEditor();
        });
        button("✂ Kadraj ve döndürme", () -> photoTools("crop"));
        button("☀ Işık ve renk", () -> photoTools("color"));
        button("✨ Filtreler ve efektler", () -> photoTools("effects"));
        button("T Yazı ekle", () -> photoTools("text"));
        button("⬇ PNG olarak dışa aktar", () -> savePhoto(false));
        button("⬇ JPG olarak dışa aktar", () -> savePhoto(true));
    }
    private void photoTools(String section) {
        screen(section.equals("crop") ? "Kadraj ve döndürme" :
            section.equals("color") ? "Işık ve renk" :
            section.equals("effects") ? "Filtreler ve efektler" : "Yazı");
        button("← Fotoğraf stüdyosuna dön", () -> photoEditor());
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        showPhoto();
        if(section.equals("crop")) {
            button("90° sağa döndür", () -> {rotation=(rotation+90)%360;showPhoto();});
            button("90° sola döndür", () -> {rotation=(rotation+270)%360;showPhoto();});
            button("Yatay çevir", () -> {flipHorizontal=!flipHorizontal;showPhoto();});
            button("Dikey çevir", () -> {flipVertical=!flipVertical;showPhoto();});
            button("Kadraj: "+new String[]{"Orijinal","1:1","4:5","9:16","16:9"}[cropMode], () -> {
                cropMode=(cropMode+1)%5;photoTools("crop");
            });
        } else if(section.equals("color")) {
            photoSlider("Parlaklık",brightness+100,200,v -> brightness=v-100);
            photoSlider("Kontrast",contrast,200,v -> contrast=v);
            photoSlider("Renk doygunluğu",saturation,200,v -> saturation=v);
            photoSlider("Sıcaklık",warmth+100,200,v -> warmth=v-100);
        } else if(section.equals("effects")) {
            button("Doğal", () -> {photoPreset=0;brightness=0;contrast=100;saturation=100;warmth=0;vignette=0;grayscale=0;photoTools("effects");});
            button("Canlı", () -> {photoPreset=1;brightness=6;contrast=115;saturation=135;warmth=5;vignette=0;grayscale=0;photoTools("effects");});
            button("Sinematik", () -> {photoPreset=2;brightness=-8;contrast=125;saturation=80;warmth=-12;vignette=30;grayscale=0;photoTools("effects");});
            button("Vintage", () -> {photoPreset=3;brightness=8;contrast=90;saturation=70;warmth=24;vignette=35;grayscale=0;photoTools("effects");});
            button("Siyah beyaz: "+(grayscale==1?"Açık":"Kapalı"), () -> {grayscale=1-grayscale;photoTools("effects");});
            photoSlider("Kenar karartma",vignette,100,v -> vignette=v);
        } else {
            EditText captionInput=new EditText(this);
            captionInput.setSingleLine(true);
            captionInput.setTextColor(0xffffffff);
            captionInput.setHintTextColor(0xffaaaaaa);
            captionInput.setHint("Fotoğrafa yazı ekle");
            captionInput.setText(photoCaption);
            layout.addView(captionInput);
            button("Yazıyı uygula", () -> {photoCaption=captionInput.getText().toString();showPhoto();});
            button("Yazıyı kaldır", () -> {photoCaption="";captionInput.setText("");showPhoto();});
            photoSlider("Yazı boyutu",captionSize,100,v -> captionSize=Math.max(12,v));
        }
        button("✓ Düzenlemeyi uygula", () -> photoEditor());
    }
    private interface SliderChange { void apply(int value); }
    private void photoSlider(String title,int value,int max,SliderChange change) {
        label(title,16);
        SeekBar slider=new SeekBar(this);
        slider.setMax(max);
        slider.setProgress(value);
        layout.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int v,boolean user){
                if(user){change.apply(v);showPhoto();}
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
    }
    private void savePhoto(boolean jpeg) {
        if(photo==null){toast("Önce fotoğraf seç");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(jpeg?"image/jpeg":"image/png");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,jpeg?"video_atolye_fotograf.jpg":"video_atolye_fotograf.png");
        startActivityForResult(intent,jpeg?107:SAVE);
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
            c,0,0,0,offset + warmth * 0.42f,
            0,c,0,0,offset + warmth * 0.08f,
            0,0,c,0,offset - warmth * 0.42f,
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
        if(vignette>0){
            float cx=output.getWidth()/2f,cy=output.getHeight()/2f;
            float radius=(float)Math.hypot(cx,cy);
            android.graphics.RadialGradient gradient=new android.graphics.RadialGradient(cx,cy,radius,
                new int[]{0x00000000,0x00000000,((Math.min(220,vignette*2))<<24)},
                new float[]{0f,0.50f,1f},android.graphics.Shader.TileMode.CLAMP);
            android.graphics.Paint shade=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            shade.setShader(gradient);
            canvas.drawRect(0,0,output.getWidth(),output.getHeight(),shade);
        }
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
        label("Önizleme oynatma hızı", 16);
        SeekBar speedControl = new SeekBar(this);
        speedControl.setMax(3);
        speedControl.setProgress(videoSpeed == 0.5f ? 0 : videoSpeed == 1.5f ? 2 : videoSpeed == 2f ? 3 : 1);
        layout.addView(speedControl);
        label("Hız seçenekleri: 0.5x / 1x / 1.5x / 2x", 14);
        speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                videoSpeed = new float[]{0.5f, 1f, 1.5f, 2f}[value];
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    try {
                        videoPreview.setOnPreparedListener(mp -> {
                            videoDurationMs = mp.getDuration();
                            mp.setLooping(true);
                            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(videoSpeed));
                            mp.start();
                        });
                        videoPreview.setVideoURI(video);
                        toast("Önizleme hızı: " + videoSpeed + "x");
                    } catch (Exception e) { toast("Hız değiştirilemedi: " + e.getMessage()); }
                }
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
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
        label("Not: Hız ayarı yalnız önizlemede geçerlidir. Kırpma anahtar kareden başlayabilir. Video döndürme dışa aktarımda yön bilgisiyle uygulanır; çok katmanlı kurgu henüz yok.", 14);
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
                        if((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new java.io.IOException("Şifreli medya desteklenmiyor");
                        info.flags=(extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC)!=0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        if((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME)!=0)info.flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
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
        if (result != RESULT_OK || data == null) return;
        if (request == MONTAGE_PICK) {
            if (data.getClipData() != null) {
                for (int i=0;i<data.getClipData().getItemCount();i++)
                    montageClips.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData()!=null) montageClips.add(data.getData());
            montageStudio();return;
        }
        if(data.getData()==null)return;
        Uri uri = data.getData();
        try {
            if (request == MONTAGE_SAVE) { exportMontage(uri); } else if (request == PHOTO) {
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
                contrast = 100; saturation = 100; flipHorizontal = false; flipVertical = false; cropMode = 0; grayscale = 0; photoCaption = ""; captionSize = 42; warmth=0; vignette=0; photoPreset=0;
                photoEditor();
            } else if (request == AUDIO) {
                releaseAudio();
                audioUri = uri;
                audioStudio();
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
