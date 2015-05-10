package com.caballero.marco.dnssd_test;

import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;


import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends ActionBarActivity
{
    public static String SERVICE_NAME = "_marcotest";
    private int server_port = 0;
    private WifiP2pManager mManager;
    private Channel channel;
    private ServerSocket serverSocket;
    private TextView txtServiceStatus;
    private Button btnDiscover;
    final HashMap<String, String> buddies = new HashMap<String, String>();
    private ArrayAdapter<String> adapter;
    //private ActionListener serviceDiscoveryListener;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private DnsSdServiceResponseListener serviceResponseListener;
    private DnsSdTxtRecordListener txtRecordListener;
    private WifiP2pDnsSdServiceInfo serviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Load controls */
        txtServiceStatus = (TextView)findViewById(R.id.txtStatus);
        btnDiscover = (Button)findViewById(R.id.btnDiscover);
        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServiceDiscovery();
            }
        });

        mManager = (WifiP2pManager)getSystemService(WIFI_P2P_SERVICE);
        channel = mManager.initialize(this, getMainLooper(), null);
        adapter = new ArrayAdapter<String>( this,
                                            android.R.layout.simple_list_item_1,
                                            new ArrayList<String>());
        /* Initialize Socket Server */
        try {
            initializeSocketServer();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        startRegistration();
        setupDiscoverServices();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startRegistration() {
        //  Create a string map containing information about your service.
        Map record = new HashMap();
        record.put("listenport", String.valueOf(server_port));
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_NAME, "._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mManager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
                SpannableString text = new SpannableString("Online");
                text.setSpan(new ForegroundColorSpan(Color.GREEN), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                txtServiceStatus.setText(text);
                Log.v("DnsSDTest", "Service added.");
            }

            @Override
            public void onFailure(int arg0)
            {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                SpannableString text = new SpannableString("Offline");
                text.setSpan(new ForegroundColorSpan(Color.RED), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                txtServiceStatus.setText(text);
            }
        });
    }

    private void initializeSocketServer() throws IOException
    {
        serverSocket = new ServerSocket(0);
        server_port = serverSocket.getLocalPort();
    }

    private void setupDiscoverServices()
    {
        txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice)
            {
                Log.v("DnsSDTest", "DnsSdTxtRecord available -" + txtRecordMap.toString());
                buddies.put(srcDevice.deviceAddress, txtRecordMap.get("buddyname"));
            }
        };

        serviceResponseListener = new DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice)
            {
                // Update the device name with the human-friendly version from
                // the DnsTxtRecord, assuming one arrived.
                srcDevice.deviceName = buddies
                        .containsKey(srcDevice.deviceAddress) ? buddies
                        .get(srcDevice.deviceAddress) : srcDevice.deviceName;

                // Add to the custom adapter defined specifically for showing
                // wifi devices.
                adapter.add(srcDevice.toString());
                adapter.notifyDataSetChanged();
                Log.v("DnsSDTest", "onBonjourServiceAvailable " + instanceName);
            }
        };

        mManager.setDnsSdResponseListeners(channel, serviceResponseListener, txtRecordListener);
        Log.v("DnsSDTest", "Added DNS SD response listeners.");
    }

    private void startServiceDiscovery()
    {
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(channel, serviceRequest, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.v("DnsSDTest", "Service Request added successfully!");
            }

            @Override
            public void onFailure(int reason) {
                Log.v("DnsSDTest", "Failed to add service request!");
            }
        });

        mManager.discoverServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.v("DnsSDTest", "Service discovery successfull!");
            }

            @Override
            public void onFailure(int reason) {
                Log.v("DnsSDTest", "Service discovery failed :(");
            }
        });
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        mManager.removeLocalService(channel, serviceInfo, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.v("DnsSDTest", "Removed service");
                serviceInfo = null;
            }

            @Override
            public void onFailure(int reason) {
                Log.v("DnsSDTest", "Failed to remove service");
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if(serviceInfo == null)
        {
            startRegistration();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        try {
            if(!serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
