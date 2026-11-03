package com.videoatolye.pro;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Fitted preview with pinch zoom and one finger pan. Crop mode uses visible handles. */
public final class PhotoCanvas extends View {
    private Bitmap bitmap;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF image=new RectF(),crop=new RectF(0,0,1,1),bounds=new RectF();
    private final ScaleGestureDetector detector;
    private float zoom=1,panX,panY,lastX,lastY,aspect;
    private boolean cropping;
    private int handle;
    public PhotoCanvas(Context context){
        super(context);setContentDescription("Fotoğraf önizlemesi. İki parmakla yakınlaştır, tek parmakla kaydır.");
        detector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){if(!cropping){zoom=Math.max(1,Math.min(6,zoom*d.getScaleFactor()));invalidate();}return true;}
        });
    }
    public void setBitmap(Bitmap b){bitmap=b;invalidate();}
    public void resetZoom(){zoom=1;panX=panY=0;invalidate();}
    public void setCropMode(boolean value){cropping=value;resetZoom();}
    public void setCrop(RectF rect){crop.set(rect);invalidate();}
    public RectF getCrop(){return new RectF(crop);}
    public void setAspect(float ratio){
        aspect=ratio;
        if(bitmap==null)return;
        float w=.9f,h=.9f;
        if(ratio>0){float normalized=ratio*bitmap.getHeight()/bitmap.getWidth();if(normalized>1)h=w/normalized;else w=h*normalized;}
        crop.set((1-w)/2,(1-h)/2,(1+w)/2,(1+h)/2);invalidate();
    }
    private void fit(){
        if(bitmap==null)return;
        float scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight())*.96f;
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        image.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(0xff090c12);if(bitmap==null||bitmap.isRecycled())return;fit();
        c.save();if(!cropping){c.translate(panX,panY);c.scale(zoom,zoom,getWidth()/2f,getHeight()/2f);}
        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(Color.WHITE);c.drawBitmap(bitmap,null,image,p);c.restore();
        if(!cropping)return;
        RectF r=bounds;r.set(image.left+crop.left*image.width(),image.top+crop.top*image.height(),image.left+crop.right*image.width(),image.top+crop.bottom*image.height());
        p.setColor(0xa9000000);c.drawRect(image.left,image.top,image.right,r.top,p);c.drawRect(image.left,r.bottom,image.right,image.bottom,p);
        c.drawRect(image.left,r.top,r.left,r.bottom,p);c.drawRect(r.right,r.top,image.right,r.bottom,p);
        p.setColor(0xff71e6c1);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRect(r,p);
        p.setColor(0x99ffffff);p.setStrokeWidth(1);
        for(int i=1;i<3;i++){c.drawLine(r.left+i*r.width()/3,r.top,r.left+i*r.width()/3,r.bottom,p);c.drawLine(r.left,r.top+i*r.height()/3,r.right,r.top+i*r.height()/3,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(0xff71e6c1);
        float radius=7*getResources().getDisplayMetrics().density;c.drawCircle(r.left,r.top,radius,p);c.drawCircle(r.right,r.top,radius,p);c.drawCircle(r.left,r.bottom,radius,p);c.drawCircle(r.right,r.bottom,radius,p);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(bitmap==null)return false;detector.onTouchEvent(e);float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lastX=x;lastY=y;handle=0;
                if(cropping){
                    float nx=(x-image.left)/image.width(),ny=(y-image.top)/image.height();
                    float threshold=36*getResources().getDisplayMetrics().density/image.width();
                    boolean left=Math.abs(nx-crop.left)<threshold,right=Math.abs(nx-crop.right)<threshold;
                    boolean top=Math.abs(ny-crop.top)<threshold,bottom=Math.abs(ny-crop.bottom)<threshold;
                    if((left||right)&&(top||bottom))handle=(left?1:2)+(top?0:2);
                    else if(crop.contains(nx,ny))handle=5;
                }
                getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                float dx=x-lastX,dy=y-lastY;lastX=x;lastY=y;
                if(cropping&&handle>0){
                    dx/=image.width();dy/=image.height();
                    if(handle==5){dx=Math.max(-crop.left,Math.min(1-crop.right,dx));dy=Math.max(-crop.top,Math.min(1-crop.bottom,dy));crop.offset(dx,dy);}
                    else{
                        boolean left=handle==1||handle==3,top=handle<=2;
                        float anchorX=left?crop.right:crop.left,anchorY=top?crop.bottom:crop.top;
                        float movingX=left?crop.left+dx:crop.right+dx,movingY=top?crop.top+dy:crop.bottom+dy;
                        float w=Math.max(.04f,Math.abs(movingX-anchorX)),h=Math.max(.04f,Math.abs(movingY-anchorY));
                        float maxW=left?anchorX:1-anchorX,maxH=top?anchorY:1-anchorY;
                        if(aspect>0){float r=aspect*bitmap.getHeight()/bitmap.getWidth();h=w/r;if(h>maxH){h=maxH;w=h*r;}if(w>maxW){w=maxW;h=w/r;}}
                        else {w=Math.min(w,maxW);h=Math.min(h,maxH);}
                        crop.set(left?anchorX-w:anchorX,top?anchorY-h:anchorY,left?anchorX:anchorX+w,top?anchorY:anchorY+h);
                    }
                }else if(!cropping&&!detector.isInProgress()&&e.getPointerCount()==1){
                    float maxX=image.width()*(zoom-1)/2,maxY=image.height()*(zoom-1)/2;
                    panX=Math.max(-maxX,Math.min(maxX,panX+dx));panY=Math.max(-maxY,Math.min(maxY,panY+dy));
                }
                invalidate();return true;
            case MotionEvent.ACTION_UP:performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
