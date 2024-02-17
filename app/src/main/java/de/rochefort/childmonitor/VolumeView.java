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

public class VolumeView extends View {
    private Paint paint;
    private VolumeHistory _volumeHistory;

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
        paint = new Paint();
        paint.setColor(Color.rgb(255, 127, 0));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final VolumeHistory volumeHistory = _volumeHistory;
        if (volumeHistory == null) {
            return;
        }

        final int height = canvas.getHeight();
        final int width = canvas.getWidth();

        final VolumeHistory history = volumeHistory.getSnapshot(width);
        final int size = history.size();    // Size is at most width
        final double volumeNorm = history.getVolumeNorm();
        final double normalizedVolume = history.get(size - 1);
        final double relativeBrightness = Math.max(0.3, normalizedVolume);
        int blue;
        int rest;
        if (relativeBrightness > 0.5) {
            blue = 255;
            rest = (int) (2 * 255 * (relativeBrightness - 0.5));
        } else {
            blue = (int) (255 * (relativeBrightness - 0.2) / 0.3);
            rest = 0;
        }
        final int rgb = Color.rgb(rest, rest, blue);
        canvas.drawColor(rgb);
        final double margins = height * 0.1;
        final double graphHeight = height - 2.0 * margins;
        final double graphScale = graphHeight * volumeNorm;
        int xPrev = 0;
        int yPrev = ((int) (margins + graphHeight - history.get(0) * graphScale));
        for (int xNext = 1; xNext < size; ++xNext) {
            int yNext = (int) (margins + graphHeight - history.get(xNext) * graphScale);
            canvas.drawLine(xPrev, yPrev, xNext, yNext, paint);
            xPrev = xNext;
            yPrev = yNext;
        }
    }

    public void setVolumeHistory(VolumeHistory volumeHistory) {
        this._volumeHistory = volumeHistory;
    }
}
