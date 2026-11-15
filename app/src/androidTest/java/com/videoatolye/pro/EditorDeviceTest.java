package com.videoatolye.pro;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.widget.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

public class EditorDeviceTest extends InstrumentationTestCase {
    private Context app(){return getInstrumentation().getTargetContext();}
    private File fixture(String name)throws Exception{
        File file=new File(app().getCacheDir(),name);
        try(InputStream in=getInstrumentation().getContext().getAssets().open(name);OutputStream out=new FileOutputStream(file)){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }return file;
    }
    private EditProject.Clip clip(String name,long duration)throws Exception{
        return new EditProject.Clip(Uri.fromFile(fixture(name)).toString(),name,duration);
    }
    public void test01ProjectRoundtripAndDrafts()throws Exception{
        EditProject p=new EditProject();p.clips.add(clip("landscape.mp4",3000));p.clips.add(clip("portrait.mp4",2000));
        p.name="Türkçe proje";p.clips.get(0).caption="وَلَسَوْفَ • Merhaba";p.ratio=1;p.musicVolume=.4f;
        EditProject loaded=EditProject.decode(p.encode());assertEquals(p.encode(),loaded.encode());
        for(int i=0;i<3;i++){EditProject draft=p.draft(2500,i);assertEquals(2500,draft.totalMs());for(EditProject.Clip c:draft.clips)c.validate();}
        assertEquals(5000,p.draft(10000,0).totalMs());
        EditProject shortClip=new EditProject();shortClip.clips.add(clip("landscape.mp4",3000));shortClip.clips.add(new EditProject.Clip("file:///short","short",100));
        assertEquals(2500,shortClip.draft(2500,0).totalMs());
        try{EditProject.decode("{\"format\":\"bad\"}");fail("Invalid project accepted");}catch(IllegalArgumentException expected){}
        loaded.clips.get(0).start=loaded.clips.get(0).end;
        try{loaded.clips.get(0).validate();fail("Empty range accepted");}catch(IllegalArgumentException expected){}
    }
    public void test02PhotoRenderAndUndoState()throws Exception{
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(app(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final Throwable[] error={null};
        getInstrumentation().runOnMainSync(()->{try{
            Bitmap source=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888);source.eraseColor(Color.rgb(100,120,140));
            field(activity,"photo",source);field(activity,"photoPreviewSource",source);
            Method state=MainActivity.class.getDeclaredMethod("photoState");state.setAccessible(true);String original=(String)state.invoke(activity);
            field(activity,"rotation",90);field(activity,"brightness",20);field(activity,"cropMode",1);field(activity,"photoCaption","Türkçe yazı\nİkinci satır");
            Method render=MainActivity.class.getDeclaredMethod("renderPhoto");render.setAccessible(true);Bitmap output=(Bitmap)render.invoke(activity);
            assertEquals(200,output.getWidth());assertEquals(200,output.getHeight());
            assertTrue(Color.red(output.getPixel(10,10))>=115);assertEquals(Color.rgb(100,120,140),source.getPixel(10,10));output.recycle();
            Method restore=MainActivity.class.getDeclaredMethod("restorePhotoState",String.class);restore.setAccessible(true);restore.invoke(activity,original);
            assertEquals(original,state.invoke(activity));
            Method editor=MainActivity.class.getDeclaredMethod("photoEditor");editor.setAccessible(true);editor.invoke(activity);
            activity.finish();
        }catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private void field(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private void export(EditProject p,String name)throws Exception{
        File output=new File(app().getCacheDir(),name);output.delete();CountDownLatch done=new CountDownLatch(1);
        Throwable[] error={null};Transformer[] running={null};
        getInstrumentation().runOnMainSync(()->{try{
            running[0]=VideoExporter.create(app(),new Transformer.Listener(){
                @Override public void onCompleted(Composition c,ExportResult r){done.countDown();}
                @Override public void onError(Composition c,ExportResult r,ExportException e){error[0]=e;done.countDown();}
            });running[0].start(VideoExporter.composition(p),output.getAbsolutePath());
        }catch(Throwable e){error[0]=e;done.countDown();}});
        if(!done.await(300,TimeUnit.SECONDS)){getInstrumentation().runOnMainSync(()->{if(running[0]!=null)running[0].cancel();});fail("Export timeout");}
        if(error[0]!=null)throw new AssertionError("Render failed",error[0]);
        assertTrue(output.length()>1000);
        MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(output.getAbsolutePath());
        long duration=Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        assertTrue("duration="+duration+", expected="+p.totalMs(),Math.abs(duration-p.totalMs())<250);
        assertEquals("yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
        int rotation=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        String displayHeight=r.extractMetadata(rotation%180==0?MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT:MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        assertEquals("480",displayHeight);
        Bitmap frame=r.getFrameAtTime(200000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);frame.recycle();r.release();
    }
    public void test03MixedVideoMusicTextSpeedExport()throws Exception{
        EditProject p=new EditProject();p.height=480;p.ratio=1;
        EditProject.Clip a=clip("landscape.mp4",3000);a.start=500;a.end=1500;a.speed=2;a.volume=.4f;a.caption="Merhaba Ali";a.brightness=.05f;
        EditProject.Clip b=clip("portrait.mp4",2000);b.end=1000;b.rotation=90;b.flip=true;b.volume=0;b.saturation=0;
        p.clips.add(a);p.clips.add(b);p.music=Uri.fromFile(fixture("music.m4a")).toString();p.musicDuration=700;p.musicVolume=.25f;
        export(p,"test-montage.mp4");
        MediaExtractor extractor=new MediaExtractor();extractor.setDataSource(new File(app().getCacheDir(),"test-montage.mp4").getPath());
        boolean audio=false,video=false;for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);audio|="audio/mp4a-latm".equals(mime);video|="video/avc".equals(mime);}extractor.release();assertTrue(audio);assertTrue(video);
    }
    public void test04SlowSpeedAndMutedExport()throws Exception{
        EditProject p=new EditProject();p.height=480;p.ratio=2;
        EditProject.Clip a=clip("landscape.mp4",3000);a.start=1000;a.end=2000;a.speed=.5f;a.volume=0;p.clips.add(a);
        export(p,"test-slow.mp4");
        MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(new File(app().getCacheDir(),"test-slow.mp4").getPath());
        assertNull(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));r.release();
    }
    public void test05StudioLaunchAndRecovery()throws Exception{
        EditProject p=new EditProject();p.clips.add(clip("landscape.mp4",3000));
        app().getSharedPreferences("StudioActivity",0).edit().putString("project",p.encode()).commit();
        StudioActivity activity=(StudioActivity)getInstrumentation().startActivitySync(new Intent(app(),StudioActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        Throwable[] error={null};
        getInstrumentation().runOnMainSync(()->{try{
            Field playerField=StudioActivity.class.getDeclaredField("player");playerField.setAccessible(true);
            androidx.media3.exoplayer.ExoPlayer player=(androidx.media3.exoplayer.ExoPlayer)playerField.get(activity);player.seekTo(1500);
            Button split=findButton(activity.getWindow().getDecorView(),"Önizlemedeki yerden ikiye böl");assertNotNull(split);split.performClick();
            Field projectField=StudioActivity.class.getDeclaredField("project");projectField.setAccessible(true);EditProject result=(EditProject)projectField.get(activity);
            assertEquals(2,result.clips.size());assertEquals(1500,result.clips.get(0).end);assertEquals(1500,result.clips.get(1).start);assertEquals(3000,result.totalMs());
            findButton(activity.getWindow().getDecorView(),"2. landscape.mp4").performClick();findButton(activity.getWindow().getDecorView(),"↑ Önceki sıraya taşı").performClick();
            result=(EditProject)projectField.get(activity);assertEquals(1500,result.clips.get(0).start);
            findButton(activity.getWindow().getDecorView(),"↶ Geri al").performClick();result=(EditProject)projectField.get(activity);assertEquals(0,result.clips.get(0).start);
            activity.finish();
        }catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    public void test06FrameQualityDrafts()throws Exception{
        EditProject p=new EditProject();p.clips.add(clip("landscape.mp4",3000));p.clips.add(clip("portrait.mp4",2000));
        EditProject[] drafts=HighlightAnalyzer.analyze(app(),p,2500);
        assertEquals(3,drafts.length);
        for(EditProject draft:drafts){assertEquals(2500,draft.totalMs());for(EditProject.Clip c:draft.clips)c.validate();}
        assertEquals(5000,p.totalMs());
    }
    public void test07PhotoPngAndJpegSave()throws Exception{
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(app(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final Throwable[] error={null};
        for(int request:new int[]{103,107}){
            File output=new File(app().getCacheDir(),request==103?"photo-test.png":"photo-test.jpg");output.delete();
            getInstrumentation().runOnMainSync(()->{try{
                Field f=MainActivity.class.getDeclaredField("photo");f.setAccessible(true);
                if(f.get(activity)==null){Bitmap photo=Bitmap.createBitmap(320,200,Bitmap.Config.ARGB_8888);photo.eraseColor(Color.BLUE);field(activity,"photo",photo);field(activity,"photoPreviewSource",photo);}
                activity.onActivityResult(request,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(output)));
            }catch(Throwable t){error[0]=t;}});
            if(error[0]!=null)throw new AssertionError(error[0]);
            long deadline=System.currentTimeMillis()+30000;boolean saving=true;
            while(saving&&System.currentTimeMillis()<deadline){Thread.sleep(100);Field f=MainActivity.class.getDeclaredField("photoBusy");f.setAccessible(true);saving=f.getBoolean(activity);}
            assertFalse("Photo export timeout",saving);Bitmap image=BitmapFactory.decodeFile(output.getPath());assertNotNull(image);assertEquals(320,image.getWidth());assertEquals(200,image.getHeight());image.recycle();
        }
        getInstrumentation().runOnMainSync(()->activity.finish());
    }
    public void test08CancelRender()throws Exception{
        EditProject p=new EditProject();p.clips.add(clip("landscape.mp4",3000));
        app().getSharedPreferences("StudioActivity",0).edit().putString("project",p.encode()).commit();
        StudioActivity activity=(StudioActivity)getInstrumentation().startActivitySync(new Intent(app(),StudioActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Throwable[] error={null};File destination=new File(app().getCacheDir(),"cancel-test.mp4");destination.delete();
        getInstrumentation().runOnMainSync(()->{try{
            Method start=StudioActivity.class.getDeclaredMethod("startExport",Uri.class);start.setAccessible(true);start.invoke(activity,Uri.fromFile(destination));
            Method cancel=StudioActivity.class.getDeclaredMethod("cancelExport");cancel.setAccessible(true);cancel.invoke(activity);activity.finish();
        }catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new AssertionError(error[0]);assertFalse(destination.exists());
    }
    private Button findButton(android.view.View view,String prefix){
        if(view instanceof Button&&((Button)view).getText().toString().startsWith(prefix))return (Button)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=findButton(group.getChildAt(i),prefix);if(b!=null)return b;}}
        return null;
    }
}
