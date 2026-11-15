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
    private Bitmap photoPreviewSource,previewBitmap;
    private final java.util.ArrayDeque<String> photoUndo=new java.util.ArrayDeque<>(), photoRedo=new java.util.ArrayDeque<>();
    private boolean photoBusy=false,atHome=false;
    private String photoState() {
        return rotation+","+brightness+","+contrast+","+saturation+","+warmth+","+vignette+","+grayscale+","+cropMode+","+flipHorizontal+","+flipVertical+","+captionSize+";"+photoCaption;
    }
    private void restorePhotoState(String state) {
        String[] a=state.split(";",2),v=a[0].split(",");
        rotation=Integer.parseInt(v[0]);brightness=Integer.parseInt(v[1]);contrast=Integer.parseInt(v[2]);saturation=Integer.parseInt(v[3]);warmth=Integer.parseInt(v[4]);vignette=Integer.parseInt(v[5]);grayscale=Integer.parseInt(v[6]);cropMode=Integer.parseInt(v[7]);flipHorizontal=Boolean.parseBoolean(v[8]);flipVertical=Boolean.parseBoolean(v[9]);captionSize=Integer.parseInt(v[10]);photoCaption=a[1];
    }
    private void photoCheckpoint(String before){if(!before.equals(photoState())){photoUndo.push(before);if(photoUndo.size()>30)photoUndo.removeLast();photoRedo.clear();}}

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
        getWindow().setStatusBarColor(0xff10141e);
        getWindow().setNavigationBarColor(0xff10141e);
        home();
    }
    private void screen(String heading) {
        atHome=false;
        if(preview!=null)preview.setImageDrawable(null);
        if(previewBitmap!=null){previewBitmap.recycle();previewBitmap=null;}
        preview=null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff10141e);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 28, 24, 24);
        layout.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(24,28+insets.getSystemWindowInsetTop(),24,24+insets.getSystemWindowInsetBottom());return insets;});
        layout.setBackgroundColor(0xff10141e);
        scroll.addView(layout);
        setContentView(scroll);
        if(!heading.equals("Video Atölye Pro"))button("← Ana sayfa", () -> home());
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
        button.setTextColor(0xffeff6ff);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff26394f));
        layout.addView(button);
        button.setOnClickListener(v -> {
            if(photoBusy)return;
            String before=photoState();
            try {action.run();if(!name.startsWith("↶ Son")&&!name.startsWith("↷"))photoCheckpoint(before);}
            catch(Exception e){toast("İşlem tamamlanamadı: "+e.getMessage());}
        });
    }
    private void home() {
        screen("Video Atölye Pro");
        label("0.3 • Telefonunda düzenle, filigransız kaydet", 16);
        button("🎬 Video ve çoklu montaj stüdyosu", () -> startActivity(new Intent(this,StudioActivity.class)));
        button("📸 Fotoğraf stüdyosu", () -> photoEditor());
        label("Video: kes, böl, sırala, hız ve renk ayarla; müzik ve yazı ekle. Projeler otomatik saklanır.",16);
        label("Fotoğraf: ışık, renk, kadraj, filtre ve yazı. Kaynakların üzerine yazılmaz.",16);
        button("Sürümdeki sınırlar", () -> new android.app.AlertDialog.Builder(this).setTitle("0.3 sürümü")
            .setMessage("Yerel fotoğraf ve video düzenleme.\nOtomatik taslak seçimi süre kuralları kullanır.\nYazarak ayarlama desteklenen komutlarla sınırlıdır.\nİçerik anlayan AI, otomatik konuşma altyazısı, takip, nesne silme ve serbest çok katmanlı timeline bu sürümde yok.\nFotoğraf çalışma kopyası en fazla 4096 piksel uzun kenara indirilir; RAW iş akışı yok.")
            .setPositiveButton("Tamam",null).show());
        atHome=true;
    }

    private void pick(String mime,int request) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,request);
    }
    @Override protected void onDestroy() {
        if(preview!=null)preview.setImageDrawable(null);
        if(previewBitmap!=null)previewBitmap.recycle();
        if(photoPreviewSource!=null&&photoPreviewSource!=photo)photoPreviewSource.recycle();
        if(photo!=null&&!photoBusy)photo.recycle();
        super.onDestroy();
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
        label(photo.getWidth()+" × "+photo.getHeight()+" px çalışma kopyası",14);
        button("↶ Son değişikliği geri al",()->{if(!photoUndo.isEmpty()){photoRedo.push(photoState());restorePhotoState(photoUndo.pop());photoEditor();}});
        button("↷ Değişikliği yinele",()->{if(!photoRedo.isEmpty()){photoUndo.push(photoState());restorePhotoState(photoRedo.pop());photoEditor();}});
        button("Orijinal / düzenlenmiş karşılaştır",()->{if(preview.getTag()==null){preview.setImageBitmap(photoPreviewSource);preview.setTag("original");}else{preview.setTag(null);showPhoto();}});
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
            captionInput.setMinLines(2);
            captionInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
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
            private String before;
            public void onStartTrackingTouch(SeekBar bar){before=photoState();}
            public void onStopTrackingTouch(SeekBar bar){if(before!=null)photoCheckpoint(before);}
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
    private Bitmap rotated(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        if (flipHorizontal || flipVertical) matrix.postScale(flipHorizontal ? -1f : 1f, flipVertical ? -1f : 1f);
        Bitmap transformed = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (cropMode == 0) return transformed;
        float[] ratios = {0f,1f,4f/5f,9f/16f,16f/9f};
        float target = ratios[cropMode];
        int w = transformed.getWidth(), h = transformed.getHeight();
        int cropW = w, cropH = h;
        if ((float)w/h > target) cropW = Math.max(1,Math.round(h*target));
        else cropH = Math.max(1,Math.round(w/target));
        Bitmap cropped = Bitmap.createBitmap(transformed,(w-cropW)/2,(h-cropH)/2,cropW,cropH);
        if (cropped != transformed && transformed != source) transformed.recycle();
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
    private Bitmap renderPhoto() { return renderPhoto(photo); }
    private Bitmap renderPhoto(Bitmap source) {
        Bitmap bitmap = rotated(source);
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
            android.text.TextPaint text = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float size = Math.max(12f, output.getWidth() * captionSize / 900f);
            text.setTextSize(size);text.setColor(0xffffffff);
            text.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            text.setShadowLayer(size/8f,2f,2f,0xff000000);
            int width=Math.max(1,(int)(output.getWidth()*.90));
            android.text.StaticLayout block=android.text.StaticLayout.Builder.obtain(photoCaption,0,photoCaption.length(),text,width)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setMaxLines(8).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            canvas.save();canvas.translate((output.getWidth()-width)/2f,Math.max(0,output.getHeight()*.90f-block.getHeight()));block.draw(canvas);canvas.restore();
        }

        if (bitmap != source) bitmap.recycle();
        return output;
    }
    private void showPhoto() {
        if (preview != null && photo != null) {
            Bitmap next=renderPhoto(photoPreviewSource==null?photo:photoPreviewSource);
            preview.setImageBitmap(next);
            preview.setTag(null);
            if(previewBitmap!=null&&previewBitmap!=photo&&previewBitmap!=photoPreviewSource)previewBitmap.recycle();
            previewBitmap=next;
            preview.clearColorFilter();
        }
    }
    @Override public void onBackPressed(){if(photoBusy)return;if(atHome)super.onBackPressed();else home();}
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if(data.getData()==null)return;
        Uri uri = data.getData();
        try {
            if (request == PHOTO) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, options);
                }
                if(options.outWidth<1||options.outHeight<1)throw new java.io.IOException("Desteklenmeyen fotoğraf");
                long pixelBudget=Math.min(4096L*4096,Runtime.getRuntime().maxMemory()/32);
                int sample = 1;
                while (options.outWidth / sample > 4096 || options.outHeight / sample > 4096 || (long)(options.outWidth/sample)*(options.outHeight/sample)>pixelBudget) sample *= 2;
                Bitmap previousPhoto=photo;
                options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    Bitmap decoded=BitmapFactory.decodeStream(input,null,options);
                    if(decoded==null)throw new java.io.IOException("Fotoğraf çözümlenemedi");
                    photo=decoded;
                }
                if(photo==null)throw new java.io.IOException("Fotoğraf çözümlenemedi");
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    androidx.exifinterface.media.ExifInterface exif=new androidx.exifinterface.media.ExifInterface(input);
                    int orientation=exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1);
                    Matrix m=new Matrix();
                    switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
                    if(!m.isIdentity()){Bitmap upright=Bitmap.createBitmap(photo,0,0,photo.getWidth(),photo.getHeight(),m,true);if(upright!=photo)photo.recycle();photo=upright;}
                }catch(java.io.IOException ignored){}
                if(photoPreviewSource!=null&&photoPreviewSource!=photo)photoPreviewSource.recycle();
                float scale=Math.min(1f,1280f/Math.max(photo.getWidth(),photo.getHeight()));
                photoPreviewSource=Bitmap.createScaledBitmap(photo,Math.max(1,Math.round(photo.getWidth()*scale)),Math.max(1,Math.round(photo.getHeight()*scale)),true);
                if(previousPhoto!=null&&previousPhoto!=photo&&!previousPhoto.isRecycled())previousPhoto.recycle();
                photoUndo.clear();photoRedo.clear();
                rotation = 0;
                brightness = 0;
                contrast = 100; saturation = 100; flipHorizontal = false; flipVertical = false; cropMode = 0; grayscale = 0; photoCaption = ""; captionSize = 42; warmth=0; vignette=0; photoPreset=0;
                photoEditor();
            } else if ((request == SAVE || request == 107) && photo != null) {
                photoBusy=true;
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                android.app.ProgressDialog dialog=new android.app.ProgressDialog(this);dialog.setMessage("Fotoğraf kaydediliyor…");dialog.setCancelable(false);dialog.show();
                new Thread(()->{Bitmap bitmap=null;try{
                    bitmap=renderPhoto();
                    try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
                        if(output==null||!bitmap.compress(request==107?Bitmap.CompressFormat.JPEG:Bitmap.CompressFormat.PNG,request==107?95:100,output))throw new java.io.IOException("Fotoğraf yazılamadı");
                    }
                    runOnUiThread(()->toast("Fotoğraf kaydedildi"));
                }catch(Exception|OutOfMemoryError e){runOnUiThread(()->toast("Kaydedilemedi: "+e.getMessage()));}
                finally{if(bitmap!=null)bitmap.recycle();runOnUiThread(()->{photoBusy=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(!isFinishing())dialog.dismiss();});}},"photo-export").start();

            }
        } catch (Exception|OutOfMemoryError error) { toast("İşlem başarısız: " + error.getMessage()); }
    }
}
