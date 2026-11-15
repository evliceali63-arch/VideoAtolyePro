package com.videoatolye.pro;

import org.json.*;
import java.util.*;

/** Portable edit decisions. Source media is never overwritten. */
public final class EditProject {
    public static final class Clip {
        public String uri, name, caption="";
        public long duration, start, end;
        public float speed=1, volume=1, brightness=0, saturation=1;
        public int rotation=0;
        public boolean flip=false;
        public Clip(String uri,String name,long duration) {
            if(duration<100) throw new IllegalArgumentException("Video çok kısa veya süresi okunamadı");
            this.uri=uri;this.name=name;this.duration=duration;end=duration;
        }
        public long outputMs(){return Math.round((end-start)/speed);}
        public Clip copy() throws JSONException {return from(toJson());}
        public JSONObject toJson() throws JSONException {
            return new JSONObject().put("uri",uri).put("name",name).put("duration",duration)
                .put("start",start).put("end",end).put("speed",speed).put("volume",volume)
                .put("brightness",brightness).put("saturation",saturation).put("rotation",rotation)
                .put("flip",flip).put("caption",caption);
        }
        public static Clip from(JSONObject o) throws JSONException {
            Clip c=new Clip(o.getString("uri"),o.optString("name","Klip"),o.getLong("duration"));
            c.start=Math.max(0,o.optLong("start",0));c.end=Math.min(c.duration,o.optLong("end",c.duration));
            c.speed=(float)o.optDouble("speed",1);c.volume=(float)o.optDouble("volume",1);
            c.brightness=(float)o.optDouble("brightness",0);c.saturation=(float)o.optDouble("saturation",1);
            c.rotation=o.optInt("rotation",0);c.flip=o.optBoolean("flip",false);c.caption=o.optString("caption","");
            c.validate();return c;
        }
        public void validate() {
            if(start<0||end>duration||end-start<100||!Float.isFinite(speed)||speed<.25f||speed>4||
                !Float.isFinite(volume)||volume<0||volume>1||!Float.isFinite(brightness)||brightness<-.5f||brightness>.5f||
                !Float.isFinite(saturation)||saturation<0||saturation>2||rotation%90!=0)
                throw new IllegalArgumentException("Klip aralığı veya ayarları geçersiz");
        }
    }
    public final ArrayList<Clip> clips=new ArrayList<>();
    public String name="Yeni proje", music="", musicName="";
    public long musicDuration=0;
    public float musicVolume=.35f;
    public int ratio=0,height=720;
    public long totalMs(){long ms=0;for(Clip c:clips)ms+=c.outputMs();return ms;}
    public String encode() throws JSONException {
        JSONArray a=new JSONArray();for(Clip c:clips)a.put(c.toJson());
        return new JSONObject().put("format","VideoAtolyePro").put("version",1).put("name",name)
            .put("clips",a).put("music",music).put("musicName",musicName).put("musicDuration",musicDuration).put("musicVolume",musicVolume)
            .put("ratio",ratio).put("height",height).toString(2);
    }
    public static EditProject decode(String raw) throws JSONException {
        JSONObject o=new JSONObject(raw);
        if(!"VideoAtolyePro".equals(o.optString("format"))||o.optInt("version")!=1)
            throw new IllegalArgumentException("Desteklenmeyen proje dosyası");
        EditProject p=new EditProject();p.name=o.optString("name","Proje");
        JSONArray a=o.getJSONArray("clips");if(a.length()>100)throw new IllegalArgumentException("En fazla 100 klip");
        for(int i=0;i<a.length();i++)p.clips.add(Clip.from(a.getJSONObject(i)));
        p.music=o.optString("music","");p.musicName=o.optString("musicName","");
        p.musicDuration=Math.max(0,o.optLong("musicDuration",0));
        p.musicVolume=(float)o.optDouble("musicVolume",.35);p.ratio=o.optInt("ratio",0);p.height=o.optInt("height",720);
        if(p.ratio<0||p.ratio>3||!(p.height==480||p.height==720||p.height==1080)||!Float.isFinite(p.musicVolume)||p.musicVolume<0||p.musicVolume>1)
            throw new IllegalArgumentException("Proje ayarları geçersiz");
        return p;
    }
    /** Evenly spaced draft selection, deliberately not presented as semantic AI. */
    public EditProject draft(long targetMs,int variant) throws JSONException {
        if(clips.isEmpty())throw new IllegalArgumentException("Önce video ekle");
        if(targetMs<1000||targetMs>600000)throw new IllegalArgumentException("Süre 1–600 saniye olmalı");
        EditProject p=decode(encode());p.clips.clear();p.name=name+" • Taslak "+(variant+1);
        ArrayList<Clip> pool=new ArrayList<>(clips);
        if(variant%3==1)Collections.reverse(pool);
        if(variant%3==2&&pool.size()>1)Collections.rotate(pool,1);
        long remaining=Math.min(targetMs,totalMs());
        for(int i=0;i<pool.size()&&remaining>=100;i++) {
            Clip src=pool.get(i),c=src.copy();
            long length=Math.min(c.outputMs(),Math.max(100,remaining/(pool.size()-i)));
            long sourceLength=Math.min(c.end-c.start,Math.round(length*c.speed));
            double position=new double[]{.20,.50,.78}[variant%3];
            c.start=src.start+Math.round((src.end-src.start-sourceLength)*position);
            c.end=c.start+sourceLength;p.clips.add(c);remaining-=c.outputMs();
        }
        // Fill unused requested duration from source regions when short clips exhausted early.
        for(int i=0;i<p.clips.size()&&remaining>=100;i++) {
            Clip c=p.clips.get(i),src=pool.get(i);
            long add=Math.min(src.end-c.end,Math.round(remaining*c.speed));
            c.end+=add;remaining-=Math.round(add/c.speed);
            add=Math.min(c.start-src.start,Math.round(remaining*c.speed));
            c.start-=add;remaining-=Math.round(add/c.speed);
        }
        return p;
    }
}
