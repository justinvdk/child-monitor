package de.rochefort.childmonitor;

import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.CircularArray;

public class VolumeHistory {
    private double mMaxVolume = 0.25;

    private double mVolumeNorm = 1.0 / mMaxVolume;
    private final CircularArray<Double> mHistory;
    private final int mMaxHistory;

    private final Handler uiHandler;

    VolumeHistory(int maxHistory) {
        uiHandler = new Handler(Looper.getMainLooper());
        mMaxHistory = maxHistory;
        mHistory = new CircularArray<>(maxHistory);
    }


    public double getVolumeNorm() {
        return mVolumeNorm;
    }

    public double get(int i) {
        return mHistory.get(i);
    }

    public int size() {
        return mHistory.size();
    }

    private void addLast(double volume) {
        // schedule editing of member vars on the ui event loop to avoid concurrency problems
        uiHandler.post(() -> {
            if (volume > mMaxVolume) {
                mMaxVolume = volume;
                mVolumeNorm = 1.0 / volume;
            }
            mHistory.addLast(volume);
            mHistory.removeFromStart(mHistory.size() - mMaxHistory);
        });
    }

    public void onAudioData(short[] data) {
        if (data.length < 1) {
            return;
        }

        final double scale = 1.0 / 128.0;
        double sum = 0;
        for (final short datum : data) {
            final double rel = datum * scale;
            sum += rel * rel;
        }
        final double volume = sum / data.length;
        addLast(volume);
    }
}
