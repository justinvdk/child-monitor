package de.rochefort.childmonitor;

import static de.rochefort.childmonitor.AudioCodecDefines.CODEC;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class ListenService extends Service {
    private static final String TAG = "ListenService";
    public static final String CHANNEL_ID = TAG;
    public static final int ID = 1337;

    private final int frequency = AudioCodecDefines.FREQUENCY;
    private final int channelConfiguration = AudioCodecDefines.CHANNEL_CONFIGURATION_OUT;
    private final int audioEncoding = AudioCodecDefines.ENCODING;
    private final int bufferSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
    private final int byteBufferSize = bufferSize*2;

    private final IBinder binder = new ListenBinder();
    private NotificationManager notificationManager;
    private Thread listenThread;

    private final VolumeHistory volumeHistory = new VolumeHistory(16_384);
    private String childDeviceName;

    public ListenService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // Display a notification about us starting.  We put an icon in the status bar.
        createNotificationChannel();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String name = extras.getString("name");
            childDeviceName = name;
            Notification n = buildNotification(name);
            startForeground(ID, n);
            String address = extras.getString("address");
            int port = extras.getInt("port");
            doListen(address, port);
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Thread lt = listenThread;
        if (lt != null) {
            lt.interrupt();
            listenThread = null;
        }

        // Cancel the persistent notification.
        int NOTIFICATION = R.string.listening;
        notificationManager.cancel(NOTIFICATION);

        stopForeground(true);
        // Tell the user we stopped.
        Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public VolumeHistory getVolumeHistory() {
        return volumeHistory;
    }

    private Notification buildNotification(String name) {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.listening);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, ListenActivity.class), PendingIntent.FLAG_IMMUTABLE);

        // Set the info for the views that show in the notification panel.
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID);
        b.setSmallIcon(R.drawable.listening_notification)  // the status icon
                .setOngoing(true)
                .setTicker(text)  // the status text
                .setContentTitle(text)  // the label of the entry
                .setContentText(name)  // the contents of the entry
                .setContentIntent(contentIntent);
        return b.build();
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    public void setErrorCallback(Runnable errorCallback) {
        this.mErrorCallback = errorCallback;
    }

    public void setUpdateCallback(Runnable updateCallback) {
        this.mUpdateCallback = updateCallback;
    }

    public class ListenBinder extends Binder {
        ListenService getService() {
            return ListenService.this;
        }
    }

    private Runnable mErrorCallback;
    private Runnable mUpdateCallback;

    private void doListen(String address, int port) {
        listenThread = new Thread(() -> {
            try {
                final Socket socket = new Socket(address, port);
                streamAudio(socket);
            } catch (IOException e) {
                Log.e(TAG, "Failed to stream audio", e);
            }

            if (!Thread.currentThread().isInterrupted()) {
                // If this thread has not been interrupted, likely something
                // bad happened with the connection to the child device. Play
                // an alert to notify the user that the connection has been
                // interrupted.
                playAlert();

                final Runnable errorCallback = mErrorCallback;
                if (errorCallback != null) {
                    errorCallback.run();
                }
            }
        });

        listenThread.start();
    }


    private void streamAudio(final Socket socket) throws IllegalArgumentException, IllegalStateException, IOException {
        Log.i(TAG, "Setting up stream");

        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                frequency,
                channelConfiguration,
                audioEncoding,
                bufferSize,
                AudioTrack.MODE_STREAM);

        final InputStream is = socket.getInputStream();
        int read = 0;

        audioTrack.play();

        try {
            final byte [] readBuffer = new byte[byteBufferSize];
            final short [] decodedBuffer = new short[byteBufferSize*2];

            while (socket.isConnected() && read != -1 && !Thread.currentThread().isInterrupted()) {
                read = is.read(readBuffer);
                int decoded = CODEC.decode(decodedBuffer, readBuffer, read, 0);

                if (decoded > 0) {
                    audioTrack.write(decodedBuffer, 0, decoded);
                    short[] decodedBytes = new short[decoded];
                    System.arraycopy(decodedBuffer, 0, decodedBytes, 0, decoded);
                    volumeHistory.onAudioData(decodedBytes);
                    final Runnable updateCallback = mUpdateCallback;
                    if (updateCallback != null) {
                        updateCallback.run();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
        } finally {
            audioTrack.stop();
            socket.close();
        }
    }


    private void playAlert() {
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.upward_beep_chromatic_fifths);
        if(mp != null) {
            Log.i(TAG, "Playing alert");
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        } else {
            Log.e(TAG, "Failed to play alert");
        }
    }

    public String getChildDeviceName() {
        return childDeviceName;
    }
}
