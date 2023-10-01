/**
 * This file is part of Child Monitor.
 *
 * Child Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Child Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Child Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package de.rochefort.childmonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;

public class VolumeView extends View {
    private static final int MAX_HISTORY = 10_000;
    private double volume;
    private double maxVolume;
    private Paint paint;
    private LinkedList<Double> volumeHistory;

    public VolumeView(Context context) {
        super(context);
        init();
    }

    public VolumeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VolumeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        volume = 0;
        maxVolume = 0.25;
        paint = new Paint();
        volumeHistory = new LinkedList<>();
        paint.setColor(Color.rgb(255, 127, 0));
    }

    public void onAudioData(short[] data) {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            double rel = data[i] / ((double)128);
            sum += Math.pow(rel, 2);
        }
        volume = sum / data.length;
        if (volume > maxVolume) {
            maxVolume = volume;
        }
        volumeHistory.addLast(volume);
        while (volumeHistory.size() > MAX_HISTORY) {
            volumeHistory.removeFirst();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int height = canvas.getHeight();
        int width = canvas.getWidth();
        double relativeBrightness = 0;
        double normalizedVolume = volume / maxVolume;
        relativeBrightness = Math.max(0.3, normalizedVolume);
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
        double margins = height * 0.1;
        double graphHeight = height - 2*margins;
        int leftMost = Math.max(0, volumeHistory.size() - width);
        int yPrev = (int) (height - margins);
        for (int i = leftMost; i < volumeHistory.size() && i - leftMost < width; i++) {
            int xNext = i - leftMost;
            int yNext = (int) (margins + graphHeight - volumeHistory.get(i) / maxVolume * (graphHeight));
            int xPrev;
            if (i == leftMost) {
                xPrev = xNext;
            } else {
                xPrev = xNext - 1;
            }
            if (i == leftMost && i > 0){
                yPrev = (int) (margins + graphHeight - volumeHistory.get(i-1) / maxVolume * (graphHeight));
            }
            canvas.drawLine(xPrev, yPrev, xNext, yNext, paint);
            yPrev = yNext;

        }

    }
}
