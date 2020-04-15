package protect.babymonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class VolumeView extends View {
    private double volume = 0;
    private double maxVolume = 0;

    public VolumeView(Context context) {
        super(context);
    }

    public VolumeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VolumeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void onAudioData(byte[] data) {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            double rel = Math.abs(data[i]) / (double)32767f;
            sum+=Math.pow(rel, 4);
        }
        volume = sum/data.length;
        if (volume > maxVolume) {
            maxVolume = volume;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        double relativeBrightness = 0;
        if (maxVolume > 0) {
            relativeBrightness = volume / (float) maxVolume;
            relativeBrightness = Math.max(0.3, relativeBrightness);
        }
        int blue;
        int rest;
        if (relativeBrightness > 0.5) {
            blue = 255;
            rest = (int) (2 * 255 * (relativeBrightness - 0.5));
        } else {
            blue = (int) (255 * (relativeBrightness - 0.2) / 0.3);
            rest = 0;
        }
        int rgb = Color.rgb(rest, rest, blue);
        canvas.drawColor(rgb);
    }
}
