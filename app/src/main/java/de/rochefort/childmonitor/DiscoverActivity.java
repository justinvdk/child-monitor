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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;


public class DiscoverActivity extends Activity {
    private static final String PREF_KEY_CHILD_DEVICE_ADDRESS = "childDeviceAddress";
    private static final String PREF_KEY_CHILD_DEVICE_PORT = "childDevicePort";
    final String TAG = "ChildMonitor";

    NsdManager _nsdManager;

    NsdManager.DiscoveryListener _discoveryListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "ChildMonitor start");

        _nsdManager = (NsdManager)this.getSystemService(Context.NSD_SERVICE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);

        final Button discoverChildButton = (Button) findViewById(R.id.discoverChildButton);
        discoverChildButton.setOnClickListener(v -> loadDiscoveryViaMdns());

        final Button enterChildAddressButton = (Button) findViewById(R.id.enterChildAddressButton);
        enterChildAddressButton.setOnClickListener(v -> loadDiscoveryViaAddress());
    }

    private void loadDiscoveryViaMdns() {
        setContentView(R.layout.activity_discover_mdns);
        startServiceDiscovery("_childmonitor._tcp.");
    }

    private void loadDiscoveryViaAddress() {
        setContentView(R.layout.activity_discover_address);

        final Button connectButton = (Button) findViewById(R.id.connectViaAddressButton);
        final EditText addressField = (EditText) findViewById(R.id.ipAddressField);
        final EditText portField = (EditText) findViewById(R.id.portField);
        String preferredAddress = getPreferences(MODE_PRIVATE).getString(PREF_KEY_CHILD_DEVICE_ADDRESS, null);
        if (preferredAddress != null && !preferredAddress.isEmpty()) {
            addressField.setText(preferredAddress);
        }
        int preferredPort = getPreferences(MODE_PRIVATE).getInt(PREF_KEY_CHILD_DEVICE_PORT, -1);
        if (preferredPort > 0) {
            portField.setText(String.valueOf(preferredPort));
        } else {
            portField.setText("10000");
        }

        connectButton.setOnClickListener(v -> {
            Log.i(TAG, "Connecting to child device via address");
            final String addressString = addressField.getText().toString();
            final String portString = portField.getText().toString();

            if(addressString.length() == 0)
            {
                Toast.makeText(DiscoverActivity.this, R.string.invalidAddress, Toast.LENGTH_LONG).show();
                return;
            }

            int port = 0;

            try
            {
                port = Integer.parseInt(portString);
            }
            catch(NumberFormatException e)
            {
                Toast.makeText(DiscoverActivity.this, R.string.invalidPort, Toast.LENGTH_LONG).show();
                return;
            }
            SharedPreferences.Editor preferencesEditor = getPreferences(MODE_PRIVATE).edit();
            preferencesEditor.putString(PREF_KEY_CHILD_DEVICE_ADDRESS, addressString);
            preferencesEditor.putInt(PREF_KEY_CHILD_DEVICE_PORT, port);
            preferencesEditor.apply();
            connectToChild(addressString, port, addressString);
        });
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "ChildMonitoring stop");

        if(_discoveryListener != null) {
            Log.i(TAG, "Unregistering monitoring service");

            _nsdManager.stopServiceDiscovery(_discoveryListener);
            _discoveryListener = null;
        }

        super.onDestroy();
    }

    public void startServiceDiscovery(final String serviceType) {
        final NsdManager nsdManager = (NsdManager)this.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            Log.e(TAG, "Could not obtain nsdManager");
            return;
        }

        WifiManager wifi = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final Runnable multicastReleaser;
        if (wifi != null) {
            final WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("multicastLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
            multicastReleaser = () -> {
                try {
                    multicastLock.release();
                } catch (Exception ignored) {
                    //dont really care
                }
            };
        } else {
            multicastReleaser = () -> {
            };
        }


        final ListView serviceTable = (ListView) findViewById(R.id.ServiceTable);

        final ArrayAdapter<ServiceInfoWrapper> availableServicesAdapter = new ArrayAdapter<ServiceInfoWrapper>(this,
                R.layout.available_children_list);
        serviceTable.setAdapter(availableServicesAdapter);

        serviceTable.setOnItemClickListener((parent, view, position, id) -> {
            final ServiceInfoWrapper info = (ServiceInfoWrapper) parent.getItemAtPosition(position);
            connectToChild(info.getAddress(), info.getPort(), info.getName());
        });

        // Instantiate a new DiscoveryListener
        _discoveryListener = new NsdManager.DiscoveryListener() {
            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType)
            {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success: " + service);

                if (!service.getServiceType().equals(serviceType)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().contains("ChildMonitor")) {
                    NsdManager.ResolveListener resolver = new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            // Called when the resolve fails.  Use the error code to debug.
                            Log.e(TAG, "Resolve failed: error " + errorCode + " for service: " + serviceInfo);
                        }

                        @Override
                        public void onServiceResolved(final NsdServiceInfo serviceInfo) {
                            Log.i(TAG, "Resolve Succeeded: " + serviceInfo);

                            DiscoverActivity.this.runOnUiThread(() -> availableServicesAdapter.add(new ServiceInfoWrapper(serviceInfo)));
                        }
                    };

                    _nsdManager.resolveService(service, resolver);
                } else {
                    Log.d(TAG, "Unknown Service name: " + service.getServiceName());
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "Service lost: " + service);
                multicastReleaser.run();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                multicastReleaser.run();
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
                multicastReleaser.run();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
                multicastReleaser.run();
            }
        };

        nsdManager.discoverServices(
                serviceType, NsdManager.PROTOCOL_DNS_SD, _discoveryListener
        );
    }

    /**
     * Launch the ListenActivity to connect to the given child device
     *
     * @param address
     * @param port
     * @param name
     */
    private void connectToChild(final String address, final int port, final String name) {
        final Intent i = new Intent(getApplicationContext(), ListenActivity.class);
        final Bundle b = new Bundle();
        b.putString("address", address);
        b.putInt("port", port);
        b.putString("name", name);
        i.putExtras(b);
        startActivity(i);
    }
}

class ServiceInfoWrapper {
    private NsdServiceInfo _info;
    public ServiceInfoWrapper(NsdServiceInfo info)
    {
        _info = info;
    }

    public String getAddress()
    {
        return _info.getHost().getHostAddress();
    }

    public int getPort()
    {
        return _info.getPort();
    }

    public String getName() {
        // If there is more than one service on the network, it will
        // have a number at the end, but will appear as the following:
        //   "ChildMonitor\\032(number)
        // or
        //   "ChildMonitor\032(number)
        // Replace \\032 and \032 with a " "
        String serviceName = _info.getServiceName();
        serviceName = serviceName.replace("\\\\032", " ");
        serviceName = serviceName.replace("\\032", " ");
        return serviceName;
    }

    @Override
    public String toString()
    {
        return getName();
    }
}
