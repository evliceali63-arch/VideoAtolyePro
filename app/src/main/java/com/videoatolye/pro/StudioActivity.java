package com.videoatolye.pro;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.media.MediaMetadataRetriever;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.transformer.*;
import java.util.*;
import java.io.*;
import org.json.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class StudioActivity extends Activity {
    private static final int ADD=301,MUSIC=302,EXPORT=303,SAVE_PROJECT=304,LOAD_PROJECT=305,COVER=306;
    private EditProject project=new EditProject();
    private int selected=0;
    private LinearLayout root;
    private ExoPlayer player;
    private Transformer transformer;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ProgressBar progress;
    private TextView status;
    private File renderFile;
    private Uri outputUri;
    private boolean busy=false,cancelled=false;
    private volatile int exportToken=0;
    private long coverMs;
    private EditProject.Clip coverClip;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final int BG=0xff0c121d,PANEL=0xff182538,ACCENT=0xff51e0c2;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        String saved=getPreferences(0).getString("project","");
        if(!saved.isEmpty())try{project=EditProject.decode(saved);}catch(Exception ignored){}
        draw();
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void persist(){try{getPreferences(0).edit().putString("project",project.encode()).apply();}catch(Exception e){error(e);}}
    private void checkpoint(){try{undo.push(project.encode());if(undo.size()>30)undo.removeLast();redo.clear();}catch(Exception e){error(e);}}
    private void change(Runnable action){checkpoint();action.run();persist();draw();}
    private void stopPlayer(){if(player!=null){player.release();player=null;}}
    private TextView text(LinearLayout target,String s,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xffecf3ff);t.setPadding(0,dp(8),0,dp(8));target.addView(t);return t;
    }
    private void button(LinearLayout target,String s,Runnable run){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(0xffeff6ff);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff2a4059));b.setOnClickListener(v->{if(!busy)run.run();});target.addView(b);
        if(s.startsWith("⬇ MP4")||s.equals("＋ Video ekle")){b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));b.setTextColor(BG);}
    }
    private LinearLayout card(String heading){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(8),dp(14),dp(12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(16));l.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);root.addView(l,lp);text(l,heading,19);return l;
    }
    private void draw(){
        if(busy)return;stopPlayer();
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(24));
        scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(16),dp(12)+insets.getSystemWindowInsetTop(),dp(16),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
        text(root,"VİDEO ATÖLYE / 0.3",13).setTextColor(ACCENT);
        text(root,project.name,28);
        text(root,project.clips.size()+" klip • "+seconds(project.totalMs())+" sn • Telefonunda işlenir",14);
        button(root,"＋ Video ekle",()->pick(ADD,"video/*",true));
        LinearLayout projects=card("Proje");
        button(projects,"Proje adını değiştir",()->input("Proje adı",project.name,value->{if(value.trim().isEmpty())throw new IllegalArgumentException("Ad boş olamaz");change(()->project.name=value.trim());}));
        button(projects,"Projeyi dosyaya kaydet",()->create(SAVE_PROJECT,"application/json","VideoAtolye-Proje.json"));
        button(projects,"Proje dosyası aç",()->pick(LOAD_PROJECT,"*/*",false));
        button(projects,"Yeni boş proje",()->new AlertDialog.Builder(this).setTitle("Yeni proje?").setMessage("Mevcut düzenleme geçmişte kalır. Kalıcı kopya için önce projeyi dosyaya kaydet.")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Yeni proje",(d,w)->change(()->{project=new EditProject();selected=0;})).show());
        button(projects,"↶ Geri al ("+undo.size()+")",()->history(false));
        button(projects,"↷ Yinele ("+redo.size()+")",()->history(true));
        if(project.clips.isEmpty()){
            text(root,"Videolarını ekle; sırala, böl, renk ve sesi düzenle, müzik ve yazıyla MP4 oluştur. Orijinaller korunur.",17);
            button(root,"← Ana sayfa / Fotoğraf",()->finish());return;
        }
        selected=Math.max(0,Math.min(selected,project.clips.size()-1));
        LinearLayout timeline=card("Zaman çizelgesi");
        long offset=0;
        for(int i=0;i<project.clips.size();i++){
            final int index=i;EditProject.Clip c=project.clips.get(i);
            button(timeline,(i==selected?"● ":"")+(i+1)+". "+c.name+"\n"+seconds(offset)+"–"+seconds(offset+c.outputMs())+" sn",()->{selected=index;draw();});
            offset+=c.outputMs();
        }
        EditProject.Clip c=project.clips.get(selected);
        LinearLayout preview=card("Klip "+(selected+1)+" • "+c.name);
        PlayerView pv=new PlayerView(this);pv.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        preview.addView(pv,new LinearLayout.LayoutParams(-1,dp(230)));
        try{
            player=new ExoPlayer.Builder(this).build();pv.setPlayer(player);
            EditProject single=EditProject.decode(project.encode());single.clips.clear();EditProject.Clip item=c.copy();item.speed=1;single.clips.add(item);single.music="";
            EditedMediaItem edited=VideoExporter.composition(single).sequences.get(0).editedMediaItems.get(0);
            player.setVideoEffects(edited.effects.videoEffects);player.setMediaItem(edited.mediaItem);player.setPlaybackSpeed(c.speed);player.setVolume(c.volume);
            player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){toast("Önizleme açılamadı: "+e.getErrorCodeName());}});
            player.prepare();
        }catch(Exception e){text(preview,"Önizleme: "+e.getMessage(),14);}
        text(preview,"Klip önizlemesi; ek müzik dışa aktarımda karıştırılır.",12);
        button(preview,"Bu kareyi kapak olarak kaydet",()->{
            coverClip=c;coverMs=c.start+(player==null?0:player.getCurrentPosition());create(COVER,"image/jpeg","VideoAtolye-Kapak.jpg");
        });
        LinearLayout edit=card("Klip düzenleme");
        text(edit,"Kaynak aralığı: "+seconds(c.start)+"–"+seconds(c.end)+" / "+seconds(c.duration)+" sn",15);
        button(edit,"✂ Başlangıç / bitiş saniyesi",()->trim(c));
        button(edit,"Önizlemedeki yerden ikiye böl",()->{
            long at=c.start+(player==null?0:player.getCurrentPosition());
            if(at-c.start<100||c.end-at<100){toast("Bölmek için klibin ortasına ilerle");return;}
            try{EditProject.Clip second=c.copy();second.start=at;change(()->{c.end=at;project.clips.add(selected+1,second);});}catch(Exception e){error(e);}
        });
        button(edit,"↑ Önceki sıraya taşı",()->{if(selected>0)change(()->{Collections.swap(project.clips,selected,selected-1);selected--;});});
        button(edit,"↓ Sonraki sıraya taşı",()->{if(selected<project.clips.size()-1)change(()->{Collections.swap(project.clips,selected,selected+1);selected++;});});
        button(edit,"Klibi çoğalt",()->{try{EditProject.Clip copy=c.copy();change(()->project.clips.add(selected+1,copy));}catch(Exception e){error(e);}});
        button(edit,"Klibi çıkar",()->change(()->project.clips.remove(selected)));
        button(edit,"Hız: "+c.speed+"×",()->choose("Gerçek video ve ses hızı",new String[]{"0.25×","0.5×","0.75×","1×","1.5×","2×","4×"},i->change(()->c.speed=new float[]{.25f,.5f,.75f,1,1.5f,2,4}[i])));
        button(edit,"Ses: %"+Math.round(c.volume*100),()->number("Klip sesi (%)",Math.round(c.volume*100),0,100,v->change(()->c.volume=v/100f)));
        button(edit,"90° döndür ("+c.rotation+"°)",()->change(()->c.rotation=(c.rotation+90)%360));
        button(edit,"Yatay aynala: "+(c.flip?"Açık":"Kapalı"),()->change(()->c.flip=!c.flip));
        button(edit,"Işık: "+Math.round(c.brightness*100),()->number("Parlaklık (-50…50)",Math.round(c.brightness*100),-50,50,v->change(()->c.brightness=v/100f)));
        button(edit,"Renk: %"+Math.round(c.saturation*100),()->number("Doygunluk (%)",Math.round(c.saturation*100),0,200,v->change(()->c.saturation=v/100f)));
        button(edit,"Yazı / altyazı",()->input("Bu klip boyunca gösterilecek yazı",c.caption,value->{if(value.length()>240)throw new IllegalArgumentException("En fazla 240 karakter");change(()->c.caption=wrap(value,32));}));
        LinearLayout sound=card("Müzik ve ses");
        text(sound,project.music.isEmpty()?"Ek müzik yok":project.musicName,14);
        button(sound,"Müzik seç / değiştir",()->pick(MUSIC,"audio/*",false));
        button(sound,"Müzik sesi: %"+Math.round(project.musicVolume*100),()->number("Müzik sesi (%)",Math.round(project.musicVolume*100),0,100,v->change(()->project.musicVolume=v/100f)));
        button(sound,"Müziği kaldır",()->change(()->{project.music="";project.musicName="";}));
        text(sound,"Müzik video süresine kadar döngüye alınır. Seslerin toplamını düşük tutarak bozulmayı önleyebilirsin.",13);
        LinearLayout auto=card("Hızlı kurgu");
        button(auto,"3 otomatik taslak oluştur",()->number("Hedef süre (saniye)",30,1,600,v->drafts(v*1000L)));
        text(auto,"Taslak seçimi ışık, kenar detayı ve kare değişimini inceler. Konuyu veya olayın önemini anlayan yapay zekâ değildir.",13);
        button(auto,"Yazarak ayarla",()->prompt());
        LinearLayout output=card("Dışa aktar");
        button(output,"Kadraj: "+new String[]{"16:9 yatay","9:16 dikey","1:1 kare","4:5"}[project.ratio],()->choose("Kadraj (görüntü sığdırılır)",new String[]{"16:9 yatay","9:16 dikey","1:1 kare","4:5"},i->change(()->project.ratio=i)));
        button(output,"Kalite: "+project.height+"p",()->choose("Çıktı yüksekliği",new String[]{"480p","720p","1080p"},i->change(()->project.height=new int[]{480,720,1080}[i])));
        button(output,"⬇ MP4 oluştur ve kaydet",()->create(EXPORT,"video/mp4","VideoAtolye-Montaj.mp4"));
        text(output,"H.264 + AAC • Filigransız • Kaydetme sırasında uygulamayı açık tut.",14);
        button(root,"← Ana sayfa / Fotoğraf",()->finish());
    }
    private void history(boolean forward){
        ArrayDeque<String> source=forward?redo:undo,target=forward?undo:redo;
        if(source.isEmpty())return;
        try{EditProject p=EditProject.decode(source.peek());target.push(project.encode());source.pop();project=p;persist();draw();}catch(Exception e){error(e);}
    }
    private interface Action<T>{void run(T value)throws Exception;}
    private void input(String title,String value,Action<String> apply){
        EditText t=new EditText(this);t.setText(value);t.setPadding(dp(20),dp(12),dp(20),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(t).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{apply.run(t.getText().toString());dialog.dismiss();}catch(Exception e){t.setError(e.getMessage());}}));dialog.show();
    }
    private void number(String title,int current,int min,int max,Action<Integer> apply){input(title,""+current,s->{int n;try{n=Integer.parseInt(s.trim());}catch(Exception e){throw new IllegalArgumentException("Sayı gir");}if(n<min||n>max)throw new IllegalArgumentException(min+"–"+max+" arasında olmalı");apply.run(n);});}
    private void choose(String title,String[] options,Action<Integer> apply){new AlertDialog.Builder(this).setTitle(title).setItems(options,(d,i)->{try{apply.run(i);}catch(Exception e){error(e);}}).show();}
    private static String seconds(long ms){return String.format(Locale.US,"%.2f",ms/1000d);}
    private static String wrap(String raw,int width){
        StringBuilder out=new StringBuilder();int length=0;
        for(String word:raw.trim().split("\\s+")){if(length>0&&length+word.length()+1>width){out.append('\n');length=0;}else if(length>0){out.append(' ');length++;}out.append(word);length+=word.length();}return out.toString();
    }
    private void trim(EditProject.Clip c){
        input("Başlangıç; bitiş (saniye)",seconds(c.start)+"; "+seconds(c.end),s->{
            String[] parts=s.replace(',','.').split(";");if(parts.length!=2)throw new IllegalArgumentException("Örnek: 1.5; 8.0");
            double a=Double.parseDouble(parts[0].trim()),b=Double.parseDouble(parts[1].trim());
            if(!Double.isFinite(a)||!Double.isFinite(b))throw new IllegalArgumentException("Geçersiz sayı");
            long start=Math.round(a*1000),end=Math.round(b*1000);
            if(start<0||end>c.duration||end-start<100)throw new IllegalArgumentException("Aralık 0–"+seconds(c.duration)+" sn; en az 0.1 sn");
            change(()->{c.start=start;c.end=end;});
        });
    }
    private void drafts(long target){
        try {
            EditProject source=EditProject.decode(project.encode());busyScreen("Kareler inceleniyor…",false);
            new Thread(()->{try{
                EditProject[] options=HighlightAnalyzer.analyze(this,source,target);
                String[] labels=new String[3];for(int i=0;i<3;i++)labels[i]="Taslak "+(i+1)+" • "+seconds(options[i].totalMs())+" sn • "+options[i].clips.size()+" klip";
                runOnUiThread(()->{if(isDestroyed())return;finishBusy();choose("Bir taslak seç (geri alınabilir)",labels,i->change(()->project=options[i]));});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){finishBusy();error(e);}});}},"highlight-analysis").start();
        }catch(Exception e){error(e);}
    }
    private void prompt(){
        input("Komut: dikey, yatay, kare, sessiz, siyah beyaz, 2x, 0.5x veya 30 saniye","",raw->{
            String s=raw.toLowerCase(new Locale("tr","TR")).trim();
            boolean known=s.contains("dikey")||s.contains("yatay")||s.contains("kare")||s.contains("sessiz")||s.contains("siyah beyaz")||s.contains("2x")||s.contains("0.5x")||s.matches(".*\\d+\\s*saniye.*");
            if(!known)throw new IllegalArgumentException("Desteklenen komutlardan birini yaz");
            EditProject p=EditProject.decode(project.encode());
            if(s.contains("dikey"))p.ratio=1;else if(s.contains("yatay"))p.ratio=0;else if(s.contains("kare"))p.ratio=2;
            for(EditProject.Clip c:p.clips){if(s.contains("sessiz"))c.volume=0;if(s.contains("siyah beyaz"))c.saturation=0;if(s.contains("0.5x"))c.speed=.5f;else if(s.contains("2x"))c.speed=2;}
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)\\s*saniye").matcher(s);if(m.find())p=p.draft(Long.parseLong(m.group(1))*1000,0);
            final EditProject result=p;
            new AlertDialog.Builder(this).setTitle("Komut sonucu").setMessage(new String[]{"Yatay","Dikey","Kare","4:5"}[p.ratio]+" • "+seconds(p.totalMs())+" sn\nYerel komut kuralları uygulanacak.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,w)->change(()->project=result)).show();
        });
    }
    private void pick(int request,String type,boolean multiple){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);
        try{startActivityForResult(i,request);}catch(Exception e){error(e);}
    }
    private void create(int request,String type,String name){
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),request);}catch(Exception e){error(e);}
    }
    private String displayName(Uri uri){
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
        }catch(Exception ignored){}return "Medya";
    }
    private void retain(Uri uri){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        Uri uri=data.getData();
        if(request==ADD){
            ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(uri!=null)uris.add(uri);
            if(project.clips.size()+uris.size()>100){toast("Bir projede en fazla 100 klip");return;}
            for(Uri u:uris)retain(u);
            busyScreen("Videolar okunuyor…",false);
            new Thread(()->{
                ArrayList<EditProject.Clip> clips=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();
                for(Uri u:uris){MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(this,u);long duration=Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    if(!"yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))throw new Exception("Video izi yok");
                    clips.add(new EditProject.Clip(u.toString(),displayName(u),duration));}catch(Exception e){errors.add(displayName(u)+": "+e.getMessage());}finally{try{r.release();}catch(Exception ignored){}}}
                runOnUiThread(()->{if(isDestroyed())return;busy=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);checkpoint();project.clips.addAll(clips);persist();draw();if(!errors.isEmpty())new AlertDialog.Builder(this).setTitle("Eklenemeyen dosyalar").setMessage(String.join("\n",errors)).setPositiveButton("Tamam",null).show();});
            },"media-import").start();return;
        }
        if(uri==null)return;
        try{
            if(request==MUSIC){retain(uri);long duration=readDuration(uri);if(duration<100)throw new IOException("Ses süresi okunamadı");change(()->{project.music=uri.toString();project.musicName=displayName(uri);project.musicDuration=duration;});}
            else if(request==EXPORT)startExport(uri);
            else if(request==SAVE_PROJECT){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Dosya açılamadı");out.write(project.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));}toast("Proje kaydedildi; kaynak videoları da sakla");}
            else if(request==LOAD_PROJECT){
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Dosya açılamadı");ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){buffer.write(chunk,0,n);if(buffer.size()>1024*1024)throw new IOException("Proje dosyası çok büyük");}byte[] bytes=buffer.toByteArray();EditProject p=EditProject.decode(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));change(()->project=p);}
            }else if(request==COVER)saveCover(uri);
        }catch(Exception e){error(e);}
    }
    private void busyScreen(String title,boolean allowCancel){
        stopPlayer();busy=true;cancelled=false;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(24),dp(80),dp(24),dp(24));panel.setBackgroundColor(BG);setContentView(panel);
        status=text(panel,title,23);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);panel.addView(progress);
        text(panel,"İşlem bitene kadar uygulamayı açık tut.",16);
        if(allowCancel){Button cancel=new Button(this);cancel.setText("İptal et");panel.addView(cancel);cancel.setOnClickListener(v->cancelExport());}
    }
    private void startExport(Uri destination) throws Exception {
        final int token=++exportToken;
        EditProject snapshot=EditProject.decode(project.encode());
        if(!snapshot.music.isEmpty()&&snapshot.musicDuration==0)snapshot.musicDuration=readDuration(Uri.parse(snapshot.music));
        Composition composition=VideoExporter.composition(snapshot);
        if(getCacheDir().getUsableSpace()<50L*1024*1024)throw new IOException("Dışa aktarmak için en az 50 MB boş alan gerekli");
        outputUri=destination;renderFile=File.createTempFile("render-",".mp4",getCacheDir());renderFile.delete();
        busyScreen("MP4 hazırlanıyor…",true);
        transformer=VideoExporter.create(this,new Transformer.Listener(){
            @Override public void onCompleted(Composition c,ExportResult result){
                if(token!=exportToken)return;
                transformer=null;handler.removeCallbacks(tick);status.setText("Dosya kaydediliyor…");
                final File file=renderFile;final Uri target=outputUri;
                new Thread(()->{try{
                    try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(target,"wt")){
                        if(out==null)throw new IOException("Hedef dosya açılamadı");byte[] buf=new byte[1024*1024];int n;
                        while((n=in.read(buf))!=-1){if(token!=exportToken)throw new IOException("İptal edildi");out.write(buf,0,n);}
                    }
                    runOnUiThread(()->{if(token!=exportToken||isDestroyed())return;finishBusy();new AlertDialog.Builder(StudioActivity.this).setTitle("MP4 hazır")
                        .setMessage("Montaj kaydedildi.\n"+seconds(result.durationMs)+" saniye")
                        .setNegativeButton("Tamam",null).setPositiveButton("Videoyu aç",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(target,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){error(e);}}).show();});
                }catch(Exception e){deleteOutput(target);runOnUiThread(()->{if(token==exportToken&&!isDestroyed()){finishBusy();error(e);}});}finally{file.delete();}},"export-save").start();
            }
            @Override public void onError(Composition c,ExportResult result,ExportException exception){
                if(token!=exportToken||isDestroyed())return;
                transformer=null;handler.removeCallbacks(tick);if(renderFile!=null)renderFile.delete();deleteOutput(outputUri);finishBusy();
                new AlertDialog.Builder(StudioActivity.this).setTitle("Dışa aktarma tamamlanamadı").setMessage(exception.getErrorCodeName()+"\n"+exception.getMessage()+"\nDaha düşük çözünürlükle tekrar deneyebilirsin.").setPositiveButton("Tamam",null).show();
            }
        });
        try{transformer.start(composition,renderFile.getAbsolutePath());handler.post(tick);}catch(Exception e){cancelExport();throw e;}
    }
    private final Runnable tick=new Runnable(){public void run(){if(transformer==null)return;ProgressHolder holder=new ProgressHolder();if(transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE){progress.setProgress(holder.progress);status.setText("MP4 oluşturuluyor • %"+holder.progress);}handler.postDelayed(this,500);}};
    private void deleteOutput(Uri uri){if(uri!=null)try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}}
    private long readDuration(Uri uri)throws Exception{
        MediaMetadataRetriever reader=new MediaMetadataRetriever();try{reader.setDataSource(this,uri);return Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}finally{reader.release();}
    }
    private void cancelExport(){
        cancelled=true;exportToken++;if(transformer!=null){transformer.cancel();transformer=null;}handler.removeCallbacks(tick);
        if(renderFile!=null)renderFile.delete();deleteOutput(outputUri);finishBusy();toast("Dışa aktarma iptal edildi");
    }
    private void finishBusy(){busy=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);draw();}
    private void saveCover(Uri destination){
        busyScreen("Kapak kaydediliyor…",false);
        final EditProject.Clip clip=coverClip;final long position=coverMs;
        new Thread(()->{MediaMetadataRetriever r=new MediaMetadataRetriever();Bitmap b=null;try{
            r.setDataSource(this,Uri.parse(clip.uri));b=r.getFrameAtTime(Math.min(position,clip.end-1)*1000,MediaMetadataRetriever.OPTION_CLOSEST);
            if(b==null)throw new IOException("Kare alınamadı");
            try(OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(out==null||!b.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IOException("Kapak kaydedilemedi");}
            runOnUiThread(()->{finishBusy();toast("Kaynak video karesi kapak olarak kaydedildi");});
        }catch(Exception e){deleteOutput(destination);runOnUiThread(()->{finishBusy();error(e);});}finally{if(b!=null)b.recycle();try{r.release();}catch(Exception ignored){}}},"cover-save").start();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(Exception e){toast("İşlem tamamlanamadı: "+e.getMessage());}
    @Override public void onBackPressed(){if(busy){if(transformer!=null)new AlertDialog.Builder(this).setTitle("Dışa aktarma iptal edilsin mi?").setNegativeButton("Devam et",null).setPositiveButton("İptal et",(d,w)->cancelExport()).show();return;}persist();super.onBackPressed();}
    @Override protected void onStop(){if(player!=null)player.pause();super.onStop();}
    @Override protected void onDestroy(){stopPlayer();exportToken++;handler.removeCallbacks(tick);if(transformer!=null)transformer.cancel();super.onDestroy();}
}
