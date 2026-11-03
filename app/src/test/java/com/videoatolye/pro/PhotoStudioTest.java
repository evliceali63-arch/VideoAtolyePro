package com.videoatolye.pro;

import android.graphics.*;
import android.media.ExifInterface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoStudioTest {
    private Bitmap pattern(){Bitmap b=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);int[] px={Color.RED,Color.GREEN,Color.BLUE,Color.WHITE,Color.CYAN,Color.MAGENTA,Color.YELLOW,Color.BLACK,0xff123456,0xffabcdef,0xff808080,0xff654321};b.setPixels(px,0,4,0,0,4,3);return b;}
    @Test public void defaultRecipePreservesEveryPixelAndSource(){Bitmap src=pattern(),out=PhotoRenderer.render(src,new PhotoState());assertTrue(src.sameAs(out));assertFalse(src.isRecycled());assertNotSame(src,out);}
    @Test public void cropUsesChosenRegionNotOnlyCenter(){Bitmap src=pattern();PhotoState s=new PhotoState();s.left=.5f;s.top=1f/3;s.bottom=1;Bitmap out=PhotoRenderer.render(src,s);assertEquals(2,out.getWidth());assertEquals(2,out.getHeight());assertEquals(src.getPixel(2,1),out.getPixel(0,0));assertEquals(src.getPixel(3,2),out.getPixel(1,1));}
    @Test public void quarterRotationAndMirroringKeepContent(){Bitmap src=pattern();PhotoState s=new PhotoState();s.rotation=90;Bitmap out=PhotoRenderer.render(src,s);assertEquals(3,out.getWidth());assertEquals(4,out.getHeight());assertEquals(src.getPixel(0,2),out.getPixel(0,0));s=new PhotoState();s.flipX=true;out=PhotoRenderer.render(src,s);assertEquals(src.getPixel(3,0),out.getPixel(0,0));}
    @Test public void monochromeChangesOnlyColorAndKeepsSource(){Bitmap src=pattern();PhotoState s=new PhotoState();s.mono=true;Bitmap out=PhotoRenderer.render(src,s);int p=out.getPixel(0,0);assertEquals(Color.red(p),Color.green(p));assertEquals(Color.red(p),Color.blue(p));assertEquals(Color.RED,src.getPixel(0,0));assertEquals(src.getWidth(),out.getWidth());}
    @Test public void exposureAdjustsMidGrayAsExpected(){Bitmap src=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);src.eraseColor(0xff404040);PhotoState s=new PhotoState();s.exposure=100;Bitmap out=PhotoRenderer.render(src,s);assertEquals(128,Color.red(out.getPixel(0,0)));}
    @Test public void pngRoundTripPreservesResult()throws Exception{Bitmap src=pattern();PhotoState s=new PhotoState();s.brightness=12;s.warmth=15;s.contrast=8;Bitmap out=PhotoRenderer.render(src,s);ByteArrayOutputStream bytes=new ByteArrayOutputStream();assertTrue(out.compress(Bitmap.CompressFormat.PNG,100,bytes));byte[] data=bytes.toByteArray();Bitmap decoded=BitmapFactory.decodeByteArray(data,0,data.length);assertTrue(out.sameAs(decoded));}
    @Test public void recipeRoundTripAndIndependentUndoSnapshot()throws Exception{PhotoState s=new PhotoState();s.text="Ali’nin fotoğrafı 🌄";s.rotation=90;s.left=.2f;s.shadows=31;s.textColor=Color.YELLOW;PhotoState snapshot=s.copy();assertTrue(s.same(snapshot));s.shadows=0;s.text="";assertEquals(31,snapshot.shadows);assertEquals("Ali’nin fotoğrafı 🌄",snapshot.text);PhotoState recovered=PhotoState.fromJson(new JSONObject(snapshot.toJson().toString()));assertTrue(recovered.same(snapshot));}
    @Test public void invalidCropAndSettingsAreClamped()throws Exception{PhotoState s=PhotoState.fromJson(new JSONObject("{\"left\":2,\"right\":-1,\"top\":-2,\"bottom\":0,\"exposure\":999}"));assertTrue(s.right>s.left);assertTrue(s.bottom>s.top);assertEquals(200,s.exposure);Bitmap out=PhotoRenderer.render(pattern(),s);assertTrue(out.getWidth()>0);assertTrue(out.getHeight()>0);}
    @Test public void inputOrientationExifIsApplied()throws Exception{File f=File.createTempFile("orientation",".jpg");try(FileOutputStream os=new FileOutputStream(f)){Bitmap b=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);b.compress(Bitmap.CompressFormat.JPEG,100,os);}ExifInterface exif=new ExifInterface(f.getPath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();Bitmap loaded=PhotoRenderer.load(f,1280,false);assertEquals(80,loaded.getWidth());assertEquals(120,loaded.getHeight());f.delete();}
    @Test public void downscaleNeverUpscalesAndHonorsMaxEdge()throws Exception{File f=File.createTempFile("sample",".png");try(FileOutputStream os=new FileOutputStream(f)){Bitmap b=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888);b.compress(Bitmap.CompressFormat.PNG,100,os);}Bitmap small=PhotoRenderer.load(f,200,false);assertEquals(200,small.getWidth());assertEquals(100,small.getHeight());Bitmap original=PhotoRenderer.load(f,2048,false);assertEquals(800,original.getWidth());f.delete();}
    @Test public void textOverlayAndVignetteActuallyRender(){Bitmap src=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);src.eraseColor(Color.LTGRAY);PhotoState s=new PhotoState();s.text="Video Atölye Pro";s.vignette=45;Bitmap out=PhotoRenderer.render(src,s);assertFalse(src.sameAs(out));assertTrue(Color.red(out.getPixel(0,0))<Color.red(out.getPixel(200,150)));}
    @Test public void invalidImageGivesReadableError()throws Exception{
        File f=File.createTempFile("broken", ".jpg");
        try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3,4});}
        try{PhotoRenderer.load(f,1280,false);fail("Invalid image was accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("çözümlenemedi"));}finally{f.delete();}
    }
    @Test public void histogramSamplesChannels(){int[][] h=PhotoRenderer.histogram(pattern());assertEquals(2,h[0][255]);assertEquals(1,h[1][255]);assertEquals(1,h[2][255]);}
    private void flushWorker(PhotoEditorActivity a)throws Exception {
        Field f=a.getClass().getDeclaredField("worker");f.setAccessible(true);
        ((java.util.concurrent.ExecutorService)f.get(a)).submit(()->{}).get(30,java.util.concurrent.TimeUnit.SECONDS);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    private Object field(PhotoEditorActivity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    @Test public void importEditExportAndDraftReopen()throws Exception {
        File input=File.createTempFile("photo-test", ".png");
        Bitmap image=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);image.eraseColor(0xff405060);
        try(FileOutputStream os=new FileOutputStream(input)){image.compress(Bitmap.CompressFormat.PNG,100,os);}
        try(var controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup()){
            PhotoEditorActivity a=controller.get();
            Method imp=a.getClass().getDeclaredMethod("importPhoto",android.net.Uri.class);imp.setAccessible(true);imp.invoke(a,android.net.Uri.fromFile(input));flushWorker(a);flushWorker(a);
            assertNotNull(field(a,"source"));
            PhotoState state=(PhotoState)field(a,"state");
            Method change=a.getClass().getDeclaredMethod("change",Runnable.class);change.setAccessible(true);change.invoke(a,(Runnable)()->{state.brightness=20;state.left=.25f;});flushWorker(a);
            File output=new File(a.getCacheDir(),"test-export.jpg");
            Method export=a.getClass().getDeclaredMethod("export",android.net.Uri.class);export.setAccessible(true);export.invoke(a,android.net.Uri.fromFile(output));flushWorker(a);
            assertTrue(output.isFile());Bitmap decoded=BitmapFactory.decodeFile(output.getPath());assertNotNull(decoded);assertEquals(90,decoded.getWidth());assertEquals(80,decoded.getHeight());
            assertTrue(Color.red(decoded.getPixel(10,10))>64);
            assertEquals(120,BitmapFactory.decodeFile(input.getPath()).getWidth());
            assertEquals(20,new JSONObject(a.getPreferences(0).getString("recipe","{}")).getInt("brightness"));
        }
        input.delete();
    }
    @Test public void studioFitsSmallPhoneAndRenders()throws Exception {
        try(var controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup()){
            PhotoEditorActivity a=controller.get();Field source=a.getClass().getDeclaredField("source");source.setAccessible(true);source.set(a,pattern());
            Method tools=a.getClass().getDeclaredMethod("showTools");tools.setAccessible(true);tools.invoke(a);
            Method render=a.getClass().getDeclaredMethod("renderPreview");render.setAccessible(true);render.invoke(a);flushWorker(a);
            View root=a.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(851,View.MeasureSpec.EXACTLY));root.layout(0,0,393,851);
            Bitmap screen=Bitmap.createBitmap(393,851,Bitmap.Config.ARGB_8888);root.draw(new Canvas(screen));
            File f=new File("/tmp/android-build/photo-studio-qa.png");try(FileOutputStream out=new FileOutputStream(f)){screen.compress(Bitmap.CompressFormat.PNG,100,out);}
            PhotoCanvas preview=(PhotoCanvas)field(a,"preview");assertTrue(preview.getHeight()>150);
        }
    }
    @Test @Config(sdk=35) public void studioStartsOnAndroid15()throws Exception{
        try(var controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup()){
            PhotoEditorActivity a=controller.get();assertNotNull(field(a,"preview"));
            View root=a.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(851,View.MeasureSpec.EXACTLY));root.layout(0,0,393,851);
        }
    }
    @Test public void activityOpensAndPhotoStudioNavigationWorks()throws Exception{
        try(var controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup()){
            PhotoEditorActivity a=controller.get();Field source=a.getClass().getDeclaredField("source");source.setAccessible(true);source.set(a,pattern());
            Field state=a.getClass().getDeclaredField("state");state.setAccessible(true);PhotoState s=(PhotoState)state.get(a);
            Method change=a.getClass().getDeclaredMethod("change",Runnable.class);change.setAccessible(true);change.invoke(a,(Runnable)()->s.exposure=75);
            Method history=a.getClass().getDeclaredMethod("history",boolean.class);history.setAccessible(true);history.invoke(a,false);assertEquals(0,((PhotoState)state.get(a)).exposure);history.invoke(a,true);assertEquals(75,((PhotoState)state.get(a)).exposure);
        }
    }
}
