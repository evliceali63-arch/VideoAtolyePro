package com.videoatolye.pro;

import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import android.graphics.*;
import java.lang.reflect.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class EditorSimulationTest {
    @Test public void projectRoundtripPreservesArabicTextAndTrim()throws Exception {
        EditProject p=new EditProject();p.name="Ali'nin projesi";
        EditProject.Clip c=new EditProject.Clip("content://test/1","deneme",12000);
        c.caption="وَلَسَوْفَ يُعْطِيكَ • Türkçe";c.start=2000;c.end=9000;c.speed=2;c.flip=true;
        p.clips.add(c);p.music="content://audio/2";p.ratio=1;
        assertEquals(p.encode(),EditProject.decode(p.encode()).encode());assertEquals(3500,p.totalMs());
    }
    @Test public void draftLengthAndSourceBoundsHoldForUnevenClips()throws Exception {
        EditProject p=new EditProject();p.clips.add(new EditProject.Clip("file:///a","a",30000));p.clips.add(new EditProject.Clip("file:///b","b",150));
        for(int i=0;i<3;i++){EditProject d=p.draft(10000,i);assertEquals(10000,d.totalMs());for(EditProject.Clip c:d.clips)c.validate();}
        assertEquals(30150,p.totalMs());assertEquals(30150,p.draft(60000,0).totalMs());
    }
    @Test public void rejectsInvalidTimeAndSpeed()throws Exception {
        EditProject.Clip c=new EditProject.Clip("file:///a","a",1000);c.speed=Float.NaN;
        try{c.validate();fail();}catch(IllegalArgumentException expected){}
        c.speed=1;c.start=1000;try{c.validate();fail();}catch(IllegalArgumentException expected){}
        try{EditProject.decode("{\"format\":\"wrong\"}");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void rendersCropBrightnessAndKeepsSourcePixels()throws Exception {
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();Bitmap source=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888);source.eraseColor(Color.rgb(100,120,140));
            set(a,"photo",source);set(a,"photoPreviewSource",source);set(a,"brightness",20);set(a,"rotation",90);set(a,"cropMode",1);
            Bitmap result=(Bitmap)invoke(a,"renderPhoto");assertEquals(200,result.getWidth());assertEquals(200,result.getHeight());
            assertEquals(120,Color.red(result.getPixel(10,10)));assertEquals(Color.rgb(100,120,140),source.getPixel(10,10));result.recycle();
        }
    }
    @Test public void restoresPhotoEditState()throws Exception {
        try(ActivityController<MainActivity> ctrl=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=ctrl.get();String original=(String)invoke(a,"photoState");set(a,"warmth",37);set(a,"photoCaption","Satır 1; satır 2");
            Method restore=MainActivity.class.getDeclaredMethod("restorePhotoState",String.class);restore.setAccessible(true);restore.invoke(a,original);
            assertEquals(original,invoke(a,"photoState"));
        }
    }
    @Test public void speedDurationSurvivesAudioProcessorReset()throws Exception {
        EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip("file:///speed","speed",3000);c.start=500;c.end=1500;c.speed=2;p.clips.add(c);
        androidx.media3.transformer.EditedMediaItem item=VideoExporter.composition(p).sequences.get(0).editedMediaItems.get(0);
        long duration=1_000_000;
        for(androidx.media3.common.audio.AudioProcessor processor:item.effects.audioProcessors){processor.reset();duration=processor.getDurationAfterProcessorApplied(duration);}
        assertEquals(500_000,duration);
    }
    private static Object invoke(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}
    private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
}
