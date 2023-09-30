/**
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import android.app.Activity;
import android.content.Context;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MonitorActivity extends Activity {
    final static String TAG = "ChildMonitor";

    private NsdManager nsdManager;

    private NsdManager.RegistrationListener registrationListener;

    private ServerSocket currentSocket;

    private Object connectionToken;

    private int currentPort;

    private void serviceConnection(Socket socket) {
            runOnUiThread(() -> {
                final TextView statusText = (TextView) findViewById(R.id.textStatus);
                statusText.setText(R.string.streaming);
            });

            final int frequency = AudioCodecDefines.FREQUENCY;
            final int channelConfiguration = AudioCodecDefines.CHANNEL_CONFIGURATION_IN;
            final int audioEncoding = AudioCodecDefines.ENCODING;

            final int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            final AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    frequency,
                    channelConfiguration,
                    audioEncoding,
                    bufferSize
            );

            final int pcmBufferSize = bufferSize*2;
            final short[] pcmBuffer = new short[pcmBufferSize];
            final byte[] ulawBuffer = new byte[pcmBufferSize];

            try {
                audioRecord.startRecording();
                final OutputStream out = socket.getOutputStream();

                socket.setSendBufferSize(pcmBufferSize);
                Log.d(TAG, "Socket send buffer size: " + socket.getSendBufferSize());

                while (socket.isConnected() && currentSocket != null && !Thread.currentThread().isInterrupted()) {
                    final int read = audioRecord.read(pcmBuffer, 0, bufferSize);
                    int encoded = CODEC.encode(pcmBuffer, read, ulawBuffer, 0);
                    out.write(ulawBuffer, 0, encoded);

                }
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
            } finally {
                audioRecord.stop();
            }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "ChildMonitor start");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        nsdManager = (NsdManager)this.getSystemService(Context.NSD_SERVICE);
        currentPort = 10000;
        currentSocket = null;
        final Object currentToken = new Object();
        connectionToken = currentToken;

        new Thread(() -> {
            while(Objects.equals(connectionToken, currentToken)) {
                try (ServerSocket serverSocket = new ServerSocket(currentPort)) {
                    currentSocket = serverSocket;
                    // Store the chosen port.
                    final int localPort = serverSocket.getLocalPort();

                    // Register the service so that parent devices can
                    // locate the child device
                    registerService(localPort);

                    // Wait for a parent to find us and connect
                    try (Socket socket = serverSocket.accept()) {
                        Log.i(TAG, "Connection from parent device received");

                        // We now have a client connection.
                        // Unregister so no other clients will
                        // attempt to connect
                        unregisterService();
                        serviceConnection(socket);
                    }
                } catch(Exception e) {
                    // Just in case
                    currentPort++;
                    Log.e(TAG, "Failed to open server socket. Port increased to " + currentPort, e);
                }
            }
        }).start();

        final TextView addressText = findViewById(R.id.address);

        List<String> listenAddresses = getListenAddresses();
        if(!listenAddresses.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < listenAddresses.size(); i++) {
                String listenAddress = listenAddresses.get(i);
                sb.append(listenAddress);
                if (i != listenAddresses.size() -1) {
                    sb.append("\n\n");
                }
            }
            addressText.setText(sb.toString());
        } else {
            addressText.setText(R.string.notConnected);
        }

    }

    private List<String> getListenAddresses() {
        String service = Context.CONNECTIVITY_SERVICE;
        ConnectivityManager cm = (ConnectivityManager)getSystemService(service);
        List<String> listenAddresses = new ArrayList<>();
        if (cm != null) {
            for (Network network : cm.getAllNetworks()) {
                NetworkInfo networkInfo = cm.getNetworkInfo(network);
                boolean connected = networkInfo.isConnected();
                if (connected) {
                    List<LinkAddress> linkAddresses = cm.getLinkProperties(network).getLinkAddresses();
                    for (LinkAddress linkAddress : linkAddresses) {
                        InetAddress address = linkAddress.getAddress();
                        if (!address.isLinkLocalAddress() && !address.isLoopbackAddress()) {
                            listenAddresses.add(address.getHostAddress() + " (" + networkInfo.getTypeName() + ")");
                        }
                    }
                }
            }
        }
        return listenAddresses;
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "ChildMonitor stop");

        unregisterService();

        connectionToken = null;
        if(currentSocket != null) {
            try {
                currentSocket.close();
                currentSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Failed to close active socket on port "+currentPort);
            }
        }
        super.onStop();
    }

    private void registerService(final int port) {
        final NsdServiceInfo serviceInfo  = new NsdServiceInfo();
        serviceInfo.setServiceName("ChildMonitor");
        serviceInfo.setServiceType("_childmonitor._tcp.");
        serviceInfo.setPort(port);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                final String serviceName = nsdServiceInfo.getServiceName();

                Log.i(TAG, "Service name: " + serviceName);

                MonitorActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        final TextView statusText = (TextView) findViewById(R.id.textStatus);
                        statusText.setText(R.string.waitingForParent);

                        final TextView serviceText = (TextView) findViewById(R.id.textService);
                        serviceText.setText(serviceName);

                        final TextView portText = (TextView) findViewById(R.id.port);
                        portText.setText(Integer.toString(port));
                    }
                });
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.

                Log.i(TAG, "Unregistering service");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.

                Log.e(TAG, "Unregistration failed: " + errorCode);
            }
        };

        nsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    /**
     * Uhregistered the service and assigns the listener
     * to null.
     */
    private void unregisterService() {
        if(registrationListener != null) {
            Log.i(TAG, "Unregistering monitoring service");

            nsdManager.unregisterService(registrationListener);
            registrationListener = null;
        }
    }
}
