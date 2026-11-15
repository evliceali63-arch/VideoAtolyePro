package com.videoatolye.pro;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.*;
import androidx.media3.common.*;
import androidx.media3.common.audio.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import com.google.common.collect.ImmutableList;
import java.nio.*;
import java.util.*;

/** One local rendering pipeline for both single clips and montage. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class VideoExporter {
    public static final class Gain extends BaseAudioProcessor {
        private final float gain;
        public Gain(float gain){this.gain=gain;}
        @Override protected AudioFormat onConfigure(AudioFormat in) throws UnhandledAudioFormatException {
            if(in.encoding!=C.ENCODING_PCM_16BIT)throw new UnhandledAudioFormatException(in);
            return in;
        }
        @Override public void queueInput(ByteBuffer input) {
            ByteBuffer output=replaceOutputBuffer(input.remaining());
            while(input.remaining()>=2)output.putShort((short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(input.getShort()*gain))));
            output.flip();
        }
    }
    public static Composition composition(EditProject p) {
        if(p.clips.isEmpty())throw new IllegalArgumentException("En az bir klip gerekli");
        ArrayList<EditedMediaItem> items=new ArrayList<>();
        for(EditProject.Clip c:p.clips) {
            c.validate();
            MediaItem media=new MediaItem.Builder().setUri(c.uri).setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build();
            ArrayList<Effect> effects=new ArrayList<>();
            effects.add(new ScaleAndRotateTransformation.Builder().setRotationDegrees(c.rotation).setScale(c.flip?-1f:1f,1f).build());
            if(c.brightness!=0)effects.add(new Brightness(c.brightness));
            if(c.saturation!=1)effects.add(new HslAdjustment.Builder().adjustSaturation((c.saturation-1)*100).build());
            final float factor=c.speed;
            SpeedProvider speedProvider=new SpeedProvider(){
                @Override public float getSpeed(long timeUs){return factor;}
                @Override public long getNextSpeedChangeTimeUs(long timeUs){return C.TIME_UNSET;}
            };
            if(c.speed!=1)effects.add(new SpeedChangeEffect(speedProvider));
            int width=p.ratio==1?Math.round(p.height*9f/16):p.ratio==2?p.height:p.ratio==3?Math.round(p.height*4f/5):Math.round(p.height*16f/9);
            width=(width/2)*2;
            effects.add(Presentation.createForWidthAndHeight(width,p.height,Presentation.LAYOUT_SCALE_TO_FIT));
            if(!c.caption.trim().isEmpty()) {
                SpannableString text=new SpannableString(fitCaption(c.caption,width));
                text.setSpan(new ForegroundColorSpan(Color.WHITE),0,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new BackgroundColorSpan(0xb0000000),0,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new AbsoluteSizeSpan(26),0,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                effects.add(new OverlayEffect(ImmutableList.of(TextOverlay.createStaticTextOverlay(text,
                    new OverlaySettings.Builder().setBackgroundFrameAnchor(0,-.72f).setOverlayFrameAnchor(0,0).build()))));
            }
            ArrayList<AudioProcessor> audio=new ArrayList<>();
            if(c.speed!=1)audio.add(new SpeedChangingAudioProcessor(speedProvider));
            if(c.volume!=1)audio.add(new Gain(c.volume));
            items.add(new EditedMediaItem.Builder(media).setRemoveAudio(c.volume==0)
                .setEffects(new Effects(audio,effects)).build());
        }
        ArrayList<EditedMediaItemSequence> sequences=new ArrayList<>();
        sequences.add(new EditedMediaItemSequence.Builder(items).build());
        if(!p.music.isEmpty()&&p.musicVolume>0){
            if(p.musicDuration<100)throw new IllegalArgumentException("Müziği yeniden seç: ses süresi okunamadı");
            ArrayList<EditedMediaItem> musicItems=new ArrayList<>();long remaining=p.totalMs();
            while(remaining>0){
                if(musicItems.size()>=1000)throw new IllegalArgumentException("Ses dosyası bu montaj için çok kısa; daha uzun bir müzik seç");
                long length=Math.min(remaining,p.musicDuration);
                MediaItem source=new MediaItem.Builder().setUri(p.music).setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(length).build()).build();
                musicItems.add(new EditedMediaItem.Builder(source).setRemoveVideo(true)
                    .setEffects(new Effects(ImmutableList.of(new Gain(p.musicVolume)),ImmutableList.of())).build());
                remaining-=length;
            }
            sequences.add(new EditedMediaItemSequence.Builder(musicItems).build());
        }
        return new Composition.Builder(sequences).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build();
    }
    public static Transformer create(Context context,Transformer.Listener listener) {
        return new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener).build();
    }
    private static String fitCaption(String raw,int width){
        android.graphics.Paint paint=new android.graphics.Paint();paint.setTextSize(26);
        StringBuilder text=new StringBuilder();
        for(String line:raw.split("\n",-1)){
            String remaining=line;
            while(!remaining.isEmpty()){
                int count=Math.max(1,paint.breakText(remaining,true,width*.86f,null));
                if(count<remaining.length()){
                    int space=remaining.lastIndexOf(' ',count);if(space>0)count=space;
                    if(count<remaining.length()&&Character.isLowSurrogate(remaining.charAt(count)))count++;
                }
                text.append(remaining,0,count).append('\n');remaining=remaining.substring(count).trim();
            }
        }
        return text.toString().trim();
    }
}
