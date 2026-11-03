package com.videoatolye.pro;

import org.json.JSONObject;

/** Small edit recipe; image pixels are never stored in undo history. */
public final class PhotoState {
    public int rotation, exposure, brightness, contrast, highlights, shadows, saturation, warmth, tint, vignette;
    public boolean flipX, flipY, mono;
    public float left=0, top=0, right=1, bottom=1, textX=.5f, textY=.85f, textSize=.045f;
    public String text="";
    public int textColor=0xffffffff;
    public PhotoState copy() { return fromJson(toJson()); }
    public void resetCrop() { left=top=0; right=bottom=1; }
    public JSONObject toJson() {
        JSONObject j=new JSONObject();
        try {
            j.put("rotation",rotation);j.put("exposure",exposure);j.put("brightness",brightness);j.put("contrast",contrast);
            j.put("highlights",highlights);j.put("shadows",shadows);j.put("saturation",saturation);j.put("warmth",warmth);
            j.put("tint",tint);j.put("vignette",vignette);j.put("flipX",flipX);j.put("flipY",flipY);j.put("mono",mono);
            j.put("left",left);j.put("top",top);j.put("right",right);j.put("bottom",bottom);
            j.put("text",text);j.put("textX",textX);j.put("textY",textY);j.put("textSize",textSize);j.put("textColor",textColor);
        } catch (Exception e) { throw new IllegalStateException(e); }
        return j;
    }
    public static PhotoState fromJson(JSONObject j) {
        PhotoState s=new PhotoState();
        s.rotation=(j.optInt("rotation",0)%360+360)%360;
        s.exposure=bound(j.optInt("exposure"),-200,200);s.brightness=bound(j.optInt("brightness"),-100,100);
        s.contrast=bound(j.optInt("contrast"),-100,100);s.highlights=bound(j.optInt("highlights"),-100,100);
        s.shadows=bound(j.optInt("shadows"),-100,100);s.saturation=bound(j.optInt("saturation"),-100,100);
        s.warmth=bound(j.optInt("warmth"),-100,100);s.tint=bound(j.optInt("tint"),-100,100);s.vignette=bound(j.optInt("vignette"),0,100);
        s.flipX=j.optBoolean("flipX");s.flipY=j.optBoolean("flipY");s.mono=j.optBoolean("mono");
        s.left=clamp((float)j.optDouble("left",0),0,.98f);s.top=clamp((float)j.optDouble("top",0),0,.98f);
        s.right=clamp((float)j.optDouble("right",1),s.left+.01f,1);s.bottom=clamp((float)j.optDouble("bottom",1),s.top+.01f,1);
        s.text=j.optString("text","");s.textX=clamp((float)j.optDouble("textX",.5),.1f,.9f);
        s.textY=clamp((float)j.optDouble("textY",.85),.08f,.92f);s.textSize=clamp((float)j.optDouble("textSize",.045),.015f,.15f);
        s.textColor=j.optInt("textColor",0xffffffff);return s;
    }
    private static int bound(int n,int a,int b){return Math.max(a,Math.min(b,n));}
    private static float clamp(float n,float a,float b){return Float.isNaN(n)?a:Math.max(a,Math.min(b,n));}
    public boolean same(PhotoState s){return toJson().toString().equals(s.toJson().toString());}
}
