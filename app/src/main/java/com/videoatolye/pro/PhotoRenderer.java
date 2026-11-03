package com.videoatolye.pro;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import android.text.*;
import java.io.*;

public final class PhotoRenderer {
    private PhotoRenderer() {}
    public static int[] dimensions(File f) {
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
        return new int[]{o.outWidth,o.outHeight};
    }
    public static Bitmap load(File f,int maxEdge,boolean exactSource) throws IOException {
        int[] dim=dimensions(f);
        if(dim[0]<=0||dim[1]<=0)throw new IOException("Bu dosya çözümlenemedi. JPEG, PNG veya desteklenen bir fotoğraf seç.");
        if(exactSource&&(long)dim[0]*dim[1]>16000000L)throw new IOException("Tam çözünürlük sınırı 16 MP. 4096 veya 2048 piksel seçeneğini kullan.");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;o.inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB);
        if(!exactSource)while(Math.max(dim[0],dim[1])/(o.inSampleSize==0?1:o.inSampleSize)>maxEdge*2) o.inSampleSize=o.inSampleSize==0?2:o.inSampleSize*2;
        Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);
        if(b==null)throw new IOException("Fotoğraf açılamadı");
        if(!exactSource&&Math.max(b.getWidth(),b.getHeight())>maxEdge){
            float scale=(float)maxEdge/Math.max(b.getWidth(),b.getHeight());
            Bitmap small=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);
            if(small!=b)b.recycle();b=small;
        }
        int orientation=1;
        try{orientation=new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
        Matrix m=new Matrix();
        switch(orientation){
            case 2:m.setScale(-1,1);break;
            case 3:m.setRotate(180);break;
            case 4:m.setScale(1,-1);break;
            case 5:m.setRotate(90);m.postScale(-1,1);break;
            case 6:m.setRotate(90);break;
            case 7:m.setRotate(270);m.postScale(-1,1);break;
            case 8:m.setRotate(270);break;
        }
        if(!m.isIdentity()){Bitmap out=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(out!=b)b.recycle();b=out;}
        return b;
    }
    public static Bitmap oriented(Bitmap src,PhotoState s){
        Matrix m=new Matrix();m.postRotate(s.rotation);m.postScale(s.flipX?-1:1,s.flipY?-1:1);
        return Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);
    }
    public static Bitmap render(Bitmap src,PhotoState s){
        Bitmap orient=oriented(src,s);
        int x=Math.min(orient.getWidth()-1,Math.max(0,Math.round(s.left*orient.getWidth())));
        int y=Math.min(orient.getHeight()-1,Math.max(0,Math.round(s.top*orient.getHeight())));
        int w=Math.max(1,Math.min(orient.getWidth()-x,Math.round((s.right-s.left)*orient.getWidth())));
        int h=Math.max(1,Math.min(orient.getHeight()-y,Math.round((s.bottom-s.top)*orient.getHeight())));
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);c.drawBitmap(orient,new Rect(x,y,x+w,y+h),new Rect(0,0,w,h),null);
        if(orient!=src)orient.recycle();
        int[] row=new int[w];
        double gain=Math.pow(2,s.exposure/100.0), contrast=(100+s.contrast)/100.0;
        double sat=s.mono?0:(100+s.saturation)/100.0;
        for(int yy=0;yy<h;yy++){
            if(Thread.currentThread().isInterrupted()){out.recycle();throw new IllegalStateException("İşlem iptal edildi");}
            out.getPixels(row,0,w,0,yy,w,1);
            for(int xx=0;xx<w;xx++){
                int p=row[xx];double r=(p>>16)&255,g=(p>>8)&255,b=p&255;
                double l=(.2126*r+.7152*g+.0722*b)/255;
                double tone=s.shadows*1.1*(1-l)*(1-l)+s.highlights*1.1*l*l;
                r=(r*gain-127.5)*contrast+127.5+s.brightness+tone+s.warmth*.32+s.tint*.12;
                g=(g*gain-127.5)*contrast+127.5+s.brightness+tone-s.tint*.22;
                b=(b*gain-127.5)*contrast+127.5+s.brightness+tone-s.warmth*.32+s.tint*.12;
                double lum=.2126*r+.7152*g+.0722*b;r=lum+(r-lum)*sat;g=lum+(g-lum)*sat;b=lum+(b-lum)*sat;
                if(s.vignette>0){double dx=2.0*xx/Math.max(1,w-1)-1,dy=2.0*yy/Math.max(1,h-1)-1;double v=1-s.vignette/100.0*.6*Math.pow(Math.min(1,(dx*dx+dy*dy)/2),1.5);r*=v;g*=v;b*=v;}
                row[xx]=(p&0xff000000)|(clamp(r)<<16)|(clamp(g)<<8)|clamp(b);
            }
            out.setPixels(row,0,w,0,yy,w,1);
        }
        if(!s.text.trim().isEmpty()){
            TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(s.textColor);
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(Math.max(8,w*s.textSize));
            paint.setShadowLayer(Math.max(1,w*.002f),0,w*.001f,0xcc000000);
            int textWidth=Math.max(1,(int)(w*.84f));
            StaticLayout text=StaticLayout.Builder.obtain(s.text,0,s.text.length(),paint,textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
            float tx=Math.max(0,Math.min(w-textWidth,w*s.textX-textWidth/2f));
            float ty=Math.max(0,Math.min(Math.max(0,h-text.getHeight()),h*s.textY-text.getHeight()/2f));
            c.save();c.translate(tx,ty);text.draw(c);c.restore();
        }
        return out;
    }
    private static int clamp(double n){return Math.max(0,Math.min(255,(int)Math.round(n)));}
    public static int[][] histogram(Bitmap b){
        int[][] hist=new int[3][256];int[] row=new int[b.getWidth()];
        for(int y=0;y<b.getHeight();y+=3){b.getPixels(row,0,b.getWidth(),0,y,b.getWidth(),1);for(int x=0;x<row.length;x+=3){int p=row[x];hist[0][(p>>16)&255]++;hist[1][(p>>8)&255]++;hist[2][p&255]++;}}
        return hist;
    }
}
