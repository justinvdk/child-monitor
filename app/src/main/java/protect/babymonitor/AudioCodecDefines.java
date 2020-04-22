package protect.babymonitor;

import android.media.AudioFormat;

public class AudioCodecDefines {
    public static final int FREQUENCY = 11025;
    public static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int CHANNEL_CONFIGURATION_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_CONFIGURATION_OUT = AudioFormat.CHANNEL_OUT_MONO;


    private AudioCodecDefines() {
        throw new IllegalStateException("Do not instantiate!");
    }
}
