package com.videoatolye.pro;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import org.json.JSONObject;

/** Offline photo editor; original bytes + edit recipe are persisted separately. */
public class PhotoEditorActivity extends Activity {
    private static final int OPEN=501,EXPORT=502;
    private static final int BG=0xff10141d,PANEL=0xff1a2130,FG=0xffeef2f9,MUTED=0xffa5b2c8,ACCENT=0xff71e6c1;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<PhotoState> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private PhotoState state=new PhotoState(),gestureStart;
    private Bitmap source,rendered;
    private File sourceFile;
    private PhotoCanvas preview;
    private LinearLayout root,tools;
    private TextView info,status;
    private HistogramView histogram;
    private Button undoButton,redoButton;
    private String tab="Işık",exportMime="image/jpeg";
    private volatile int generation;
    private int quality=95,exportEdge;
    private boolean busy,compare;
    private volatile boolean destroyed;
    private PhotoState pendingExport;
    private final Runnable renderRequest=()->renderPreview();

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        sourceFile=new File(getFilesDir(),"photo-source.bin");
        build();
        if(sourceFile.isFile()){
            try{state=PhotoState.fromJson(new JSONObject(getPreferences(0).getString("recipe","{}")));}catch(Exception ignored){}
            loadExisting();
        }
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(7),dp(4),dp(7));return v;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void horizontal(LinearLayout parent,LinearLayout content){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(content);parent.addView(scroll);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Button button(LinearLayout target,String text,Runnable run){
        Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(FG);b.setMinHeight(dp(44));
        GradientDrawable bg=new GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(10));b.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(46));lp.setMargins(dp(3),dp(3),dp(3),dp(3));target.addView(b,lp);
        b.setPadding(dp(13),0,dp(13),0);b.setOnClickListener(v->{if(!busy)run.run();else toast("Devam eden işlemin bitmesini bekle.");});return b;
    }
    private void build(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(10),dp(6),dp(10),dp(4));setContentView(root);
        if(Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(dp(10)+bars.left,dp(6)+bars.top,dp(10)+bars.right,dp(4)+bars.bottom);return insets;
        });
        LinearLayout header=row();root.addView(header);
        button(header,"‹",()->finish());
        TextView title=label("FOTOĞRAF STÜDYOSU",16,FG);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        button(header,"Aç",()->openPhoto());
        button(header,"Kaydet",()->exportDialog());
        info=label("Orijinal korunur · Cihazda çalışır",12,MUTED);root.addView(info);
        preview=new PhotoCanvas(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout history=row();HorizontalScrollView historyScroll=new HorizontalScrollView(this);historyScroll.setHorizontalScrollBarEnabled(false);historyScroll.addView(history);root.addView(historyScroll);
        undoButton=button(history,"↶ Geri",()->history(false));redoButton=button(history,"↷ İleri",()->history(true));
        Button before=button(history,"Önce / sonra",()->{});
        before.setOnTouchListener((v,event)->{
            if(busy||source==null)return true;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){compare=true;preview.setBitmap(source);status.setText("ORİJİNAL · Karşılaştırmayı bitirmek için bırak");return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){compare=false;preview.setBitmap(rendered);status.setText("Düzenlenmiş önizleme");v.performClick();return true;}return true;
        });
        button(history,"Sığdır",()->preview.resetZoom());
        histogram=new HistogramView();root.addView(histogram,new LinearLayout.LayoutParams(-1,dp(28)));
        status=label("Başlamak için Aç düğmesine dokun.",12,MUTED);root.addView(status);
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout tabRow=row();tabs.addView(tabRow);root.addView(tabs);
        for(String name:new String[]{"Işık","Renk","Kadraj","Görünüm","Yazı","Proje"})button(tabRow,name,()->{tab=name;showTools();});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);tools=column();scroll.addView(tools);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(180)));showTools();updateHistory();
    }
    private void showTools(){
        tools.removeAllViews();tools.addView(label(tab.toUpperCase(new Locale("tr")),12,ACCENT));
        if(source==null){tools.addView(label("Araçları kullanmak için Aç ile bir fotoğraf seç.",14,MUTED));return;}
        if(tab.equals("Işık")){
            slider("Pozlama",-200,200,state.exposure,v->state.exposure=v);
            slider("Parlaklık",-100,100,state.brightness,v->state.brightness=v);
            slider("Kontrast",-100,100,state.contrast,v->state.contrast=v);
            slider("Parlak alanlar",-100,100,state.highlights,v->state.highlights=v);
            slider("Gölgeler",-100,100,state.shadows,v->state.shadows=v);
        }else if(tab.equals("Renk")){
            slider("Doygunluk",-100,100,state.saturation,v->state.saturation=v);
            slider("Sıcaklık",-100,100,state.warmth,v->state.warmth=v);
            slider("Ton · yeşil / magenta",-100,100,state.tint,v->state.tint=v);
            button(tools,"Siyah beyaz: "+(state.mono?"açık":"kapalı"),()->change(()->state.mono=!state.mono));
        }else if(tab.equals("Kadraj")){
            LinearLayout r=row();horizontal(tools,r);button(r,"↶ 90°",()->change(()->{state.rotation=(state.rotation+270)%360;state.resetCrop();}));
            button(r,"↷ 90°",()->change(()->{state.rotation=(state.rotation+90)%360;state.resetCrop();}));
            button(r,"Yatay çevir",()->change(()->{state.flipX=!state.flipX;float l=state.left;state.left=1-state.right;state.right=1-l;}));
            button(tools,"Dikey çevir",()->change(()->{state.flipY=!state.flipY;float t=state.top;state.top=1-state.bottom;state.bottom=1-t;}));
            button(tools,"Kadrajı elle ayarla",()->cropDialog());
            button(tools,"Kadrajı sıfırla",()->change(()->state.resetCrop()));
            tools.addView(label("Köşelerden boyutlandır, ortadan taşı. Döndürme kadrajı sıfırlar.",12,MUTED));
        }else if(tab.equals("Görünüm")){
            LinearLayout r=row();horizontal(tools,r);
            for(String name:new String[]{"Doğal","Dengeli","Sıcak","Siyah beyaz"})button(r,name,()->change(()->{
                state.exposure=state.brightness=state.highlights=state.shadows=state.contrast=state.saturation=state.warmth=state.tint=state.vignette=0;state.mono=false;
                if(name.equals("Dengeli")){state.contrast=8;state.shadows=10;state.highlights=-10;state.saturation=5;}
                if(name.equals("Sıcak")){state.warmth=16;state.contrast=4;state.saturation=-4;}
                if(name.equals("Siyah beyaz")){state.mono=true;state.contrast=12;}
            }));
            slider("Kenar karartma",0,100,state.vignette,v->state.vignette=v);
            tools.addView(label("Yalnız ışık ve renk değişir. Yüz, nesne veya arka plan üretilmez. Hazır görünüm renk ayarlarının yerini alır.",12,MUTED));
        }else if(tab.equals("Yazı")){
            EditText text=new EditText(this);text.setTextColor(FG);text.setHintTextColor(MUTED);text.setText(state.text);text.setHint("İsteğe bağlı yazı · en fazla 240 karakter");
            text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});text.setMaxLines(4);tools.addView(text);
            LinearLayout r=row();horizontal(tools,r);button(r,"Yazıyı uygula",()->change(()->state.text=text.getText().toString()));button(r,"Kaldır",()->change(()->state.text=""));
            for(int color:new int[]{Color.WHITE,Color.BLACK,0xffffd66b,ACCENT}){Button b=button(r,"●",()->change(()->state.textColor=color));b.setTextColor(color);}
            slider("Boyut",15,150,Math.round(state.textSize*1000),v->state.textSize=v/1000f);
            slider("Yatay konum",10,90,Math.round(state.textX*100),v->state.textX=v/100f);
            slider("Dikey konum",8,92,Math.round(state.textY*100),v->state.textY=v/100f);
        }else{
            tools.addView(label("Son fotoğraf ve ayarları cihazda otomatik taslak olarak tutulur. Kaydet her zaman yeni bir dosya oluşturur.",13,MUTED));
            button(tools,"Tüm ayarları sıfırla",()->new AlertDialog.Builder(this).setTitle("Düzenlemeler sıfırlansın mı?").setMessage("Bu işlem geri alınabilir. Orijinal dosya korunur.").setNegativeButton("Vazgeç",null).setPositiveButton("Sıfırla",(d,w)->change(()->state=new PhotoState())).show());
            button(tools,"Taslağı cihazdan kaldır",()->new AlertDialog.Builder(this).setTitle("Taslağı kaldır?").setMessage("Bu uygulamadaki kaynak kopyası ve düzenleme taslağı silinir. Galerideki orijinal ve kaydettiğin çıktılar kalır.").setNegativeButton("Vazgeç",null).setPositiveButton("Kaldır",(d,w)->clearDraft()).show());
            tools.addView(label("RAW geliştirme, katmanlar, rötuş ve yapay zekâ bu sürümde yok. Önizleme sRGB renklerinde işlenir; HDR korunmaz.",12,MUTED));
        }
    }
    private void slider(String name,int min,int max,int value,IntConsumer apply){
        LinearLayout r=row();TextView title=label(name,13,FG);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView amount=label(displayValue(name,value),13,ACCENT);r.addView(amount);horizontal(tools,r);
        SeekBar b=new SeekBar(this);b.setMax(max-min);b.setProgress(value-min);b.setContentDescription(name);tools.addView(b);
        amount.setOnClickListener(v->{
            if(busy)return;
            EditText input=new EditText(this);input.setText(name.equals("Pozlama")?String.format(Locale.US,"%.2f",(b.getProgress()+min)/100f):String.valueOf(b.getProgress()+min));input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            new AlertDialog.Builder(this).setTitle(name+" ("+displayValue(name,min)+" … "+displayValue(name,max)+")").setView(input).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,w)->{
                try{double entered=Double.parseDouble(input.getText().toString().replace(',','.'));if(!Double.isFinite(entered))throw new NumberFormatException();int n=(int)Math.round(entered*(name.equals("Pozlama")?100:1));n=Math.max(min,Math.min(max,n));final int val=n;change(()->apply.accept(val));}catch(NumberFormatException e){toast("Geçerli bir sayı gir.");}
            }).show();
        });
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            PhotoState start;
            public void onStartTrackingTouch(SeekBar bar){start=state.copy();gestureStart=start;}
            public void onProgressChanged(SeekBar bar,int n,boolean user){
                if(!user||busy)return;if(start==null){start=state.copy();gestureStart=start;}apply.accept(n+min);amount.setText(displayValue(name,n+min));scheduleRender();
            }
            public void onStopTrackingTouch(SeekBar bar){if(start!=null&&!start.same(state)){pushUndo(start);persist();}start=null;gestureStart=null;renderPreview();}
        });
    }
    private String displayValue(String name,int value){return name.equals("Pozlama")?String.format(Locale.getDefault(),"%+.2f EV",value/100f):String.valueOf(value);}
    private void change(Runnable action){if(source==null){toast("Önce fotoğraf aç.");return;}PhotoState before=state.copy();action.run();if(!before.same(state)){pushUndo(before);persist();showTools();renderPreview();}}
    private void pushUndo(PhotoState before){undo.addLast(before);while(undo.size()>40)undo.removeFirst();redo.clear();updateHistory();}
    private void history(boolean forward){if(source==null)return;ArrayDeque<PhotoState> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;to.addLast(state.copy());state=from.removeLast();persist();updateHistory();showTools();renderPreview();}
    private void updateHistory(){undoButton.setEnabled(!busy&&!undo.isEmpty());redoButton.setEnabled(!busy&&!redo.isEmpty());}
    private void persist(){getPreferences(0).edit().putString("recipe",state.toJson().toString()).apply();}
    private void scheduleRender(){main.removeCallbacks(renderRequest);main.postDelayed(renderRequest,90);}
    private void renderPreview(){
        main.removeCallbacks(renderRequest);if(source==null||destroyed||busy)return;
        final int ticket=++generation;final Bitmap input=source;final PhotoState recipe=state.copy();status.setText("Önizleme işleniyor…");
        worker.execute(()->{
            // Ignore intermediate slider frames before allocating output pixels.
            if(ticket!=generation||destroyed)return;
            try{Bitmap out=PhotoRenderer.render(input,recipe);int[][] hist=PhotoRenderer.histogram(out);
                main.post(()->{if(destroyed||ticket!=generation){out.recycle();return;}Bitmap old=rendered;rendered=out;if(!compare)preview.setBitmap(out);if(old!=null&&old!=source&&old!=out)old.recycle();histogram.setHistogram(hist);status.setText("Düzenlenmiş önizleme · İki parmakla yakınlaştır");});
            }catch(OutOfMemoryError e){main.post(()->status.setText("Bellek yetersiz. Daha küçük fotoğrafla tekrar dene."));}
            catch(Exception e){main.post(()->{if(ticket==generation)status.setText("Önizleme hatası: "+e.getMessage());});}
        });
    }
    private void setBusy(boolean value,String message){busy=value;status.setText(message);updateHistory();}
    private void openPhoto(){
        Runnable pick=()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);};
        if(source!=null)new AlertDialog.Builder(this).setTitle("Yeni fotoğraf aç?").setMessage("Geçerli taslak değişecek. Saklamak istiyorsan önce Kaydet'i kullan.").setNegativeButton("Vazgeç",null).setPositiveButton("Fotoğraf seç",(d,w)->pick.run()).show();else pick.run();
    }
    private void loadExisting(){
        setBusy(true,"Taslak açılıyor…");
        worker.execute(()->{try{Bitmap b=PhotoRenderer.load(sourceFile,1280,false);int[] dim=PhotoRenderer.dimensions(sourceFile);main.post(()->{if(destroyed){b.recycle();return;}source=b;info.setText(dim[0]+" × "+dim[1]+" kaynak · Önizleme en fazla 1280 px");setBusy(false,"Taslak açıldı");showTools();renderPreview();});}
            catch(Exception|OutOfMemoryError e){main.post(()->setBusy(false,"Taslak açılamadı. Yeni fotoğraf seç."));}});
    }
    private void importPhoto(Uri uri){
        setBusy(true,"Fotoğraf açılıyor…");++generation;
        worker.execute(()->{File temp=new File(getFilesDir(),"photo-import.tmp");Bitmap b=null;
            try{
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IOException("Dosya okunamadı");byte[] buffer=new byte[65536];long total=0;int n;
                    while((n=in.read(buffer))!=-1){total+=n;if(total>128L*1024*1024)throw new IOException("Dosya sınırı 128 MB");out.write(buffer,0,n);}
                }
                b=PhotoRenderer.load(temp,1280,false);int[] dim=PhotoRenderer.dimensions(temp);
                if(!temp.renameTo(sourceFile))throw new IOException("Taslak saklanamadı");
                final Bitmap loaded=b;main.post(()->{
                    if(destroyed){loaded.recycle();return;}
                    Bitmap oldSource=source,oldRender=rendered;preview.setBitmap(null);source=loaded;rendered=null;
                    if(oldSource!=null)oldSource.recycle();if(oldRender!=null&&oldRender!=oldSource)oldRender.recycle();
                    state=new PhotoState();undo.clear();redo.clear();persist();preview.resetZoom();
                    info.setText(dim[0]+" × "+dim[1]+" kaynak · Orijinal dosya korunuyor");setBusy(false,"Fotoğraf açıldı");showTools();renderPreview();
                });
            }catch(Exception|OutOfMemoryError e){if(b!=null)b.recycle();temp.delete();String message=e instanceof OutOfMemoryError?"Bellek yetersiz":e.getMessage();main.post(()->{if(destroyed)return;setBusy(false,"Fotoğraf açılamadı: "+message);if(source!=null)renderPreview();});}
        });
    }
    private void clearDraft(){
        ++generation;setBusy(true,"Taslak kaldırılıyor…");worker.execute(()->{
            boolean ok=!sourceFile.exists()||sourceFile.delete();
            main.post(()->{if(destroyed)return;if(!ok){setBusy(false,"Taslak kaldırılamadı");return;}preview.setBitmap(null);if(rendered!=null&&rendered!=source)rendered.recycle();if(source!=null)source.recycle();source=rendered=null;state=new PhotoState();undo.clear();redo.clear();getPreferences(0).edit().clear().apply();histogram.setHistogram(null);info.setText("Orijinal korunur · Cihazda çalışır");setBusy(false,"Taslak kaldırıldı. Aç ile yeni fotoğraf seç.");showTools();});
        });
    }
    private void cropDialog(){
        if(source==null){toast("Önce fotoğraf aç.");return;}
        PhotoState recipe=state.copy();Bitmap oriented=PhotoRenderer.oriented(source,recipe);
        PhotoCanvas canvas=new PhotoCanvas(this);canvas.setBitmap(oriented);canvas.setCropMode(true);canvas.setCrop(new RectF(state.left,state.top,state.right,state.bottom));
        LinearLayout body=column();body.setPadding(dp(8),0,dp(8),0);body.addView(label("Köşeleri sürükle, ortadan taşı.",14,Color.DKGRAY));
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout ratios=row();scroll.addView(ratios);body.addView(scroll);
        String[] names={"Serbest","1:1","4:5","9:16","16:9","3:2"};float[] values={0,1,4f/5,9f/16,16f/9,3f/2};
        for(int i=0;i<names.length;i++){final float r=values[i];button(ratios,names[i],()->canvas.setAspect(r));}
        body.addView(canvas,new LinearLayout.LayoutParams(-1,dp(330)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Kadraj").setView(body).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,w)->{
            RectF r=canvas.getCrop();change(()->{state.left=r.left;state.top=r.top;state.right=r.right;state.bottom=r.bottom;});
        }).create();
        dialog.setOnDismissListener(d->{canvas.setBitmap(null);if(oriented!=source)oriented.recycle();});dialog.show();
    }
    private void exportDialog(){
        if(source==null){toast("Önce fotoğraf aç.");return;}
        LinearLayout body=column();body.setPadding(dp(20),0,dp(20),0);
        body.addView(label("Biçim",14,Color.DKGRAY));Spinner format=new Spinner(this);format.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"JPEG · küçük dosya","PNG · kayıpsız piksel sıkıştırma"}));body.addView(format);
        body.addView(label("Boyut · kaynak büyütülmez",14,Color.DKGRAY));Spinner size=new Spinner(this);size.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Kaynak çözünürlüğü · en fazla 16 MP","Uzun kenar en fazla 4096 px","Uzun kenar en fazla 2048 px"}));body.addView(size);
        TextView q=label("JPEG kalitesi: "+quality,14,Color.DKGRAY);body.addView(q);SeekBar qualityBar=new SeekBar(this);qualityBar.setMax(50);qualityBar.setProgress(quality-50);body.addView(qualityBar);
        qualityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){q.setText("JPEG kalitesi: "+(p+50));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        body.addView(label("Yeni dosya oluşturulur. İşlem fotoğrafın ışık/renk ve kadrajını uygular. JPEG saydam alanları beyaz yapar. HDR/RAW ve EXIF metadata aktarımı yok; konum bilgisi çıktıya eklenmez.",12,Color.DKGRAY));
        new AlertDialog.Builder(this).setTitle("Fotoğrafı dışa aktar").setView(body).setNegativeButton("Vazgeç",null).setPositiveButton("Dosya konumu seç",(d,w)->{
            exportMime=format.getSelectedItemPosition()==0?"image/jpeg":"image/png";quality=qualityBar.getProgress()+50;exportEdge=new int[]{0,4096,2048}[size.getSelectedItemPosition()];pendingExport=state.copy();
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(exportMime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"Atolye_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+(exportMime.equals("image/png")?".png":".jpg"));startActivityForResult(i,EXPORT);
        }).show();
    }
    private void export(Uri destination){
        final PhotoState recipe=pendingExport==null?state.copy():pendingExport;pendingExport=null;
        final int edge=exportEdge,q=quality;final String mime=exportMime;setBusy(true,"Dışa aktarılıyor… Uygulamayı açık tut.");++generation;
        worker.execute(()->{
            Bitmap full=null,out=null;File temp=new File(getCacheDir(),"photo-export.tmp");
            try{
                full=PhotoRenderer.load(sourceFile,edge,edge==0);out=PhotoRenderer.render(full,recipe);
                if(mime.equals("image/jpeg"))new Canvas(out).drawColor(Color.WHITE,PorterDuff.Mode.DST_OVER);
                try(OutputStream stream=new FileOutputStream(temp)){
                    if(!out.compress(mime.equals("image/png")?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,q,stream))throw new IOException("Görüntü kodlanamadı");
                }
                try(InputStream in=new FileInputStream(temp);OutputStream target=getContentResolver().openOutputStream(destination,"wt")){
                    if(target==null)throw new IOException("Çıktı açılamadı");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)target.write(buffer,0,n);target.flush();
                }
                final String result=out.getWidth()+" × "+out.getHeight()+" · "+(mime.equals("image/png")?"PNG":"JPEG")+" kaydedildi";
                main.post(()->{if(destroyed)return;setBusy(false,result);toast(result);new AlertDialog.Builder(this).setTitle("Fotoğraf kaydedildi").setMessage(result).setNegativeButton("Tamam",null).setPositiveButton("Paylaş",(d,w)->share(destination,mime)).show();});
            }catch(Exception|OutOfMemoryError e){
                try{DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}
                String message=e instanceof OutOfMemoryError?"Bellek yetersiz. 2048 px seçeneğiyle tekrar dene.":e.getMessage();
                main.post(()->{if(destroyed)return;setBusy(false,"Kaydedilemedi: "+message);toast("Kaydedilemedi: "+message);});
            }finally{if(out!=null&&out!=full)out.recycle();if(full!=null)full.recycle();temp.delete();}
        });
    }
    private void share(Uri uri,String mime){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime);i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri("Fotoğraf",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Fotoğrafı paylaş"));}catch(Exception e){toast("Paylaşım açılamadı");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request==OPEN)importPhoto(data.getData());else if(request==EXPORT)export(data.getData());}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("mime",exportMime);out.putInt("edge",exportEdge);out.putInt("quality",quality);if(pendingExport!=null)out.putString("exportRecipe",pendingExport.toJson().toString());}
    @Override protected void onRestoreInstanceState(Bundle in){super.onRestoreInstanceState(in);exportMime=in.getString("mime","image/jpeg");exportEdge=in.getInt("edge");quality=in.getInt("quality",95);try{if(in.containsKey("exportRecipe"))pendingExport=PhotoState.fromJson(new JSONObject(in.getString("exportRecipe")));}catch(Exception ignored){}}
    @Override public void onBackPressed(){if(busy){toast("İşlem tamamlanınca geri dönebilirsin.");return;}super.onBackPressed();}
    @Override protected void onStop(){super.onStop();if(source!=null)persist();}
    @Override protected void onDestroy(){destroyed=true;++generation;main.removeCallbacks(renderRequest);worker.shutdown();super.onDestroy();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final class HistogramView extends View {
        private int[][] hist;
        private final Path path=new Path();
        private final int[] colors={0xffed6b77,0xff71e6a1,0xff6d9fff};
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        HistogramView(){super(PhotoEditorActivity.this);setContentDescription("RGB histogramı");}
        void setHistogram(int[][] value){hist=value;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(BG);if(hist==null)return;int max=1;for(int[] channel:hist)for(int n:channel)max=Math.max(max,n);
            paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.STROKE);
            for(int channel=0;channel<3;channel++){paint.setColor(colors[channel]);path.reset();for(int n=0;n<256;n++){float x=n*getWidth()/255f,y=getHeight()-2-(getHeight()-4)*((float)Math.log1p(hist[channel][n])/(float)Math.log1p(max));if(n==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,paint);}
        }
    }
}
