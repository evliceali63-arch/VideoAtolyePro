package com.videoatolye.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import java.util.*;

/** Local exposure, edge-detail and frame-change scoring. No semantic understanding. */
public final class HighlightAnalyzer {
    private static final class Sample {long ms;double score;Sample(long ms,double score){this.ms=ms;this.score=score;}}
    public static EditProject[] analyze(Context context,EditProject source,long targetMs)throws Exception {
        EditProject[] drafts={source.draft(targetMs,0),source.draft(targetMs,1),source.draft(targetMs,2)};
        HashMap<String,ArrayList<Sample>> scores=new HashMap<>();
        for(EditProject.Clip clip:source.clips){
            if(scores.containsKey(clip.uri))continue;
            MediaMetadataRetriever reader=new MediaMetadataRetriever();ArrayList<Sample> samples=new ArrayList<>();
            try {
                reader.setDataSource(context,Uri.parse(clip.uri));int[] previous=null;
                int count=(int)Math.max(3,Math.min(12,(clip.end-clip.start)/1000));
                for(int i=0;i<count;i++){
                    long ms=clip.start+(clip.end-clip.start)*(i+1)/(count+1);Bitmap frame=null,small=null;
                    try{
                        if(Build.VERSION.SDK_INT>=27)frame=reader.getScaledFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,128,72);
                        else frame=reader.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if(frame==null)continue;small=Bitmap.createScaledBitmap(frame,128,72,true);
                        int[] pixels=new int[128*72];small.getPixels(pixels,0,128,0,0,128,72);
                        int[] luma=new int[pixels.length];double exposure=0,detail=0,motion=0;
                        for(int n=0;n<pixels.length;n++){int p=pixels[n];int y=(((p>>16)&255)*77+((p>>8)&255)*150+(p&255)*29)>>8;luma[n]=y;
                            exposure+=Math.max(0,1-Math.abs(y-128)/110d);
                            if(n%128>0)detail+=Math.abs(y-luma[n-1]);
                            if(n>=128)detail+=Math.abs(y-luma[n-128]);
                            if(previous!=null)motion+=Math.abs(y-previous[n]);
                        }
                        samples.add(new Sample(ms,exposure/pixels.length*.45+Math.min(1,detail/pixels.length/45)*.4+Math.min(1,motion/pixels.length/35)*.15));previous=luma;
                    }finally{if(small!=null&&small!=frame)small.recycle();if(frame!=null)frame.recycle();}
                }
            }finally{try{reader.release();}catch(Exception ignored){}}
            samples.sort((a,b)->Double.compare(b.score,a.score));scores.put(clip.uri,samples);
        }
        for(int v=0;v<drafts.length;v++)for(EditProject.Clip selected:drafts[v].clips){
            ArrayList<Sample> samples=scores.get(selected.uri);if(samples==null||samples.isEmpty())continue;
            EditProject.Clip original=null;for(EditProject.Clip c:source.clips)if(c.uri.equals(selected.uri)&&c.start<=selected.start&&c.end>=selected.end){original=c;break;}
            if(original==null)continue;
            long length=selected.end-selected.start;Sample sample=samples.get(Math.min(v,samples.size()-1));
            selected.start=Math.max(original.start,Math.min(original.end-length,sample.ms-length/2));selected.end=selected.start+length;selected.validate();
        }
        return drafts;
    }
}
