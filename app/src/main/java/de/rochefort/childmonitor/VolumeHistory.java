package de.rochefort.childmonitor;

import android.support.v4.util.CircularArray;

public class VolumeHistory {
    private double mMaxVolume = 0.25;

    private double mVolumeNorm = 1.0 / mMaxVolume;
    private final CircularArray<Double> mHistory;
    private final int mMaxHistory;

    VolumeHistory(int maxHistory) {
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

    protected synchronized void addLast(double volume) {
        if (volume > mMaxVolume) {
            mMaxVolume = volume;
            mVolumeNorm = 1.0 / volume;
        }
        mHistory.addLast(volume);
        mHistory.removeFromStart(mHistory.size() - mMaxHistory);
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

    public synchronized VolumeHistory getSnapshot(int length) {
        length = Math.min(length, size());
        VolumeHistory copy = new VolumeHistory(length);
        copy.mMaxVolume = this.mMaxVolume;
        copy.mVolumeNorm = this.mVolumeNorm;
        for (int i = 0; i < length; ++i) {
            copy.mHistory.addLast(mHistory.get(i));
        }

        return copy;
    }
}
