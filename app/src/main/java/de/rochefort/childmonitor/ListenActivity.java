/*
 * This file is part of the Child Monitor.
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

import static de.rochefort.childmonitor.AudioCodecDefines.CODEC;

import android.app.Activity;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class ListenActivity extends Activity
{
    final String TAG = "ChildMonitor";
    // Sets an ID for the notification
    final static int mNotificationId = 1;

    String _address;
    int _port;
    String _name;
    NotificationManagerCompat _mNotifyMgr;

    Thread _listenThread;
    private final int frequency = AudioCodecDefines.FREQUENCY;
    private final int channelConfiguration = AudioCodecDefines.CHANNEL_CONFIGURATION_OUT;
    private final int audioEncoding = AudioCodecDefines.ENCODING;
    private final int bufferSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
    private final int byteBufferSize = bufferSize*2;

    private void streamAudio(final Socket socket, AudioListener listener) throws IllegalArgumentException, IllegalStateException, IOException
    {
        Log.i(TAG, "Setting up stream");

        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                frequency,
                channelConfiguration,
                audioEncoding,
                bufferSize,
                AudioTrack.MODE_STREAM);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final InputStream is = socket.getInputStream();
        int read = 0;

        audioTrack.play();

        try
        {
            final byte [] readBuffer = new byte[byteBufferSize];
            final short [] decodedBuffer = new short[byteBufferSize*2];

            while(socket.isConnected() && read != -1 && Thread.currentThread().isInterrupted() == false)
            {
                read = is.read(readBuffer);
                int decoded = CODEC.decode(decodedBuffer, readBuffer, read, 0);

                if(decoded > 0)
                {
                    audioTrack.write(decodedBuffer, 0, decoded);
                    short[] decodedBytes = new short[decoded];
                    System.arraycopy(decodedBuffer, 0, decodedBytes, 0, decoded);
                    listener.onAudio(decodedBytes);
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
        }
        finally
        {
            audioTrack.stop();
            socket.close();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        final Bundle b = getIntent().getExtras();
        _address = b.getString("address");
        _port = b.getInt("port");
        _name = b.getString("name");
        // Gets an instance of the NotificationManager service
        _mNotifyMgr =
                NotificationManagerCompat.from(this);

        setContentView(R.layout.activity_listen);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ListenActivity.this)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.listening_notification)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.listening));

        _mNotifyMgr.notify(mNotificationId, mBuilder.build());

        final TextView connectedText = (TextView) findViewById(R.id.connectedTo);
        connectedText.setText(_name);

        final TextView statusText = (TextView) findViewById(R.id.textStatus);
        statusText.setText(R.string.listening);

        final VolumeView volumeView = (VolumeView) findViewById(R.id.volume);

        final AudioListener listener = new AudioListener() {
            @Override
            public void onAudio(final short[] audioData) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        volumeView.onAudioData(audioData);
                    }
                });
            }
        };


        _listenThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    final Socket socket = new Socket(_address, _port);
                    streamAudio(socket, listener);
                }
                catch (UnknownHostException e)
                {
                    Log.e(TAG, "Failed to stream audio", e);
                }
                catch (IOException e)
                {
                    Log.e(TAG, "Failed to stream audio", e);
                }

                if(Thread.currentThread().isInterrupted() == false)
                {
                    // If this thread has not been interrupted, likely something
                    // bad happened with the connection to the child device. Play
                    // an alert to notify the user that the connection has been
                    // interrupted.
                    playAlert();

                    ListenActivity.this.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            final TextView connectedText = (TextView) findViewById(R.id.connectedTo);
                            connectedText.setText("");

                            final TextView statusText = (TextView) findViewById(R.id.textStatus);
                            statusText.setText(R.string.disconnected);
                            NotificationCompat.Builder mBuilder =
                                    new NotificationCompat.Builder(ListenActivity.this)
                                            .setOngoing(false)
                                            .setSmallIcon(R.drawable.listening_notification)
                                            .setContentTitle(getString(R.string.app_name))
                                            .setContentText(getString(R.string.disconnected));
                            _mNotifyMgr.notify(mNotificationId, mBuilder.build());
                        }
                    });
                }
            }
        });

        _listenThread.start();
    }

    @Override
    public void onDestroy()
    {
        _listenThread.interrupt();
        _listenThread = null;

        super.onDestroy();
    }

    private void playAlert()
    {
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.upward_beep_chromatic_fifths);
        if(mp != null)
        {
            Log.i(TAG, "Playing alert");
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mp)
                {
                    mp.release();
                }
            });
            mp.start();
        }
        else
        {
            Log.e(TAG, "Failed to play alert");
        }
    }
}
