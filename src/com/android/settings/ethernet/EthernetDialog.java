/*
 * Copyright 2022 NXP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.ethernet;

import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.EthernetManager;
import android.net.InetAddresses;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.NetworkInfo;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.net.module.util.ProxyUtils;
import com.android.net.module.util.Inet4AddressUtils;

import com.android.settings.ProxySelector;
import com.android.settings.R;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;

import android.net.EthernetManager.InterfaceStateListener;
import android.net.IpConfiguration;
import android.os.Handler;
import android.util.ArrayMap;
import com.android.settingslib.utils.ThreadUtils;
import java.util.Arrays;

import android.net.IpConfiguration;
import android.net.EthernetNetworkUpdateRequest;

class EthernetDialog extends AlertDialog implements DialogInterface.OnClickListener, DialogInterface.OnShowListener, DialogInterface.OnDismissListener, AdapterView.OnItemSelectedListener {
    private final String TAG = "EthConfDialog";
    private static final String DEFAULT_ADDRESS = "0.0.0.0";
    private static final String DEFAULT_PREFIX_LEN = "24";
    private static final String DEFAULT_IFACE = "eth0";
    private IpAssignment mIpAssignment = IpAssignment.UNASSIGNED;

    private static final int PROXY_NONE = 0;
    private static final int PROXY_STATIC = 1;
    private static final int PROXY_PAC = 2;

    private View mView;
    private Context mContext;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mPrefixLen;
    private EditText mGw;
    private EditText mDns1;
    private EditText mDns2;
    private LinearLayout addrLayout;
    private Spinner mProxySettingsSpinner;
    private TextView mProxyHostView;
    private TextView mProxyPortView;
    private TextView mProxyExclusionListView;
    private TextView mProxyPacView;
    private ProxySettings mProxySettings = ProxySettings.UNASSIGNED;
    private ProxyInfo mHttpProxy = null;

    private ConnectivityManager mCm;
    private EthernetManager mEthManager;

    InterfaceStateListener mEthernetListener;
    ArrayMap<String, IpConfiguration> mAvailableInterfaces = new ArrayMap<>();
    Handler mUiHandler = ThreadUtils.getUiThreadHandler();
    IpConfiguration mIpConfiguration = new IpConfiguration();

    public EthernetDialog(Context context, ConnectivityManager cm, EthernetManager ethManager) {
        super(context);
        mContext = context;
        mCm = cm;
        mEthManager = ethManager;
        buildDialogContent(context);
        setOnShowListener(this);
        setOnDismissListener(this);

        mEthernetListener = (iface, state, role, configuration) -> {
            if (state == mEthManager.STATE_LINK_UP) {
                mAvailableInterfaces.put(iface, configuration);
            } else {
                mAvailableInterfaces.remove(iface);
            }
        };

        if (mEthManager != null) {
            mEthManager.addInterfaceStateListener(r -> mUiHandler.post(r),
                    mEthernetListener);
        }
    }

    public void onShow(DialogInterface dialog) {
        updateInfo();
    }

    public void onDismiss(DialogInterface dialog) {
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mProxySettingsSpinner) {
            showProxyFields();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
        case BUTTON_POSITIVE:
            handle_saveconf();
            break;
        case BUTTON_NEGATIVE:
            break;
        default:
        }
    }

    public String getifaceName(){
        String mInterfaceName = "";
        String[] ifaces = new String[2];
        ifaces[0] = getEthernetInterfaceName();
        if (ifaces.length > 0) {
            mInterfaceName = ifaces[0];
            return mInterfaceName;
        }
        return DEFAULT_IFACE;
    }

    /**
     * Get the current Ethernet interface name.
     */
    public String getEthernetInterfaceName() {
        ensureRunningOnUiThread();
        if (mAvailableInterfaces.size() == 0) return null;
        return mAvailableInterfaces.keyAt(0);
    }

    /**
     * Get the current IP configuration of Ethernet interface.
     */
    public IpConfiguration getEthernetIpConfiguration() {
        ensureRunningOnUiThread();
        if (mAvailableInterfaces.size() == 0) return null;
        return mIpConfiguration;
    }

    public void ensureRunningOnUiThread() {
        if (mUiHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("Not running on the UI thread: "
                    + Thread.currentThread().getName());
        }
    }

    public boolean isEthernetEnabled() {
        return mEthManager != null;
    }

    public boolean isEthernetAvailable() {
        ensureRunningOnUiThread();
        return isEthernetEnabled() && (mAvailableInterfaces.size() > 0);
    }

    public boolean isEthernetConnected() {
        NetworkInfo networkInfo = mCm.getActiveNetworkInfo();
        int mNetworkType = ConnectivityManager.TYPE_NONE;
        if (networkInfo == null) {
            mNetworkType = ConnectivityManager.TYPE_NONE;
        } else {
            if (networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                mNetworkType = ConnectivityManager.TYPE_ETHERNET;
            } else {
                //TODO: Here we only care about ethernet, so hardcoded as wifi type
                mNetworkType = ConnectivityManager.TYPE_WIFI;
            }
        }

        return mNetworkType == ConnectivityManager.TYPE_ETHERNET;
    }

    private void updateInfo() {
        try {
            boolean ethernetConnected = false;
            if(isEthernetAvailable()) {
                ethernetConnected = isEthernetConnected();
            }

            if (ethernetConnected) {
                IpConfiguration config = getEthernetIpConfiguration();
                if (config != null) {
                    mIpAssignment = config.getIpAssignment();
                    if (mIpAssignment == IpAssignment.STATIC) {
                        mConTypeDhcp.setChecked(false);
                        mConTypeManual.setChecked(true);
                        addrLayout.setVisibility(View.VISIBLE);
                        getEthernetInfo();
                    } else {
                        mConTypeDhcp.setChecked(true);
                        mConTypeManual.setChecked(false);
                        addrLayout.setVisibility(View.GONE);
                    }
                    mProxySettings = config.getProxySettings();
                    if (mProxySettings == ProxySettings.STATIC) {
                        mProxySettingsSpinner.setSelection(PROXY_STATIC);
                    } else if (mProxySettings == ProxySettings.PAC) {
                        mProxySettingsSpinner.setSelection(PROXY_PAC);
                    } else {
                        mProxySettingsSpinner.setSelection(PROXY_NONE);
                    }
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void buildDialogContent(Context context) {
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText) mView.findViewById(R.id.ipaddr_edit);
        mPrefixLen = (EditText) mView.findViewById(R.id.prefix_length_edit);
        mGw = (EditText) mView.findViewById(R.id.eth_gw_edit);
        mDns1 = (EditText) mView.findViewById(R.id.eth_dns1_edit);
        mDns2 = (EditText) mView.findViewById(R.id.eth_dns2_edit);
        addrLayout = (LinearLayout) mView.findViewById(R.id.addr_layout);
        mProxySettingsSpinner = (Spinner) mView.findViewById(R.id.proxy_settings);
        mProxySettingsSpinner.setOnItemSelectedListener(this);
        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                addrLayout.setVisibility(View.VISIBLE);
                mIpAssignment = IpAssignment.STATIC;
                getEthernetInfo();
            }
        });
        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                addrLayout.setVisibility(View.GONE);
                mIpAssignment = IpAssignment.DHCP;
            }
        });
        //this.setInverseBackgroundForced(true);
        this.setButton(BUTTON_POSITIVE, context.getText(R.string.menu_save), this);
        this.setButton(BUTTON_NEGATIVE, context.getText(R.string.menu_cancel), this);
    }

    private void showProxyFields() {
        IpConfiguration config = getEthernetIpConfiguration();
        if (mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC) {
            setVisibility(R.id.proxy_warning_limited_support, View.VISIBLE);
            setVisibility(R.id.proxy_fields, View.VISIBLE);
            setVisibility(R.id.proxy_pac_field, View.GONE);
            if (mProxyHostView == null) {
                mProxyHostView = (TextView) mView.findViewById(R.id.proxy_hostname);
                mProxyPortView = (TextView) mView.findViewById(R.id.proxy_port);
                mProxyExclusionListView = (TextView) mView.findViewById(R.id.proxy_exclusionlist);
            }
            if (config != null) {
                ProxyInfo proxyProperties = config.getHttpProxy();
                if (proxyProperties != null) {
                    mProxyHostView.setText(proxyProperties.getHost());
                    mProxyPortView.setText(Integer.toString(proxyProperties.getPort()));
                    mProxyExclusionListView.setText(ProxyUtils.exclusionListAsString(proxyProperties.getExclusionList()));
                }
            }
        } else if (mProxySettingsSpinner.getSelectedItemPosition() == PROXY_PAC) {
            setVisibility(R.id.proxy_warning_limited_support, View.GONE);
            setVisibility(R.id.proxy_fields, View.GONE);
            setVisibility(R.id.proxy_pac_field, View.VISIBLE);

            if (mProxyPacView == null) {
                mProxyPacView = (TextView) mView.findViewById(R.id.proxy_pac);
            }
            if (config != null) {
                ProxyInfo proxyInfo = config.getHttpProxy();
                if (proxyInfo != null) {
                    mProxyPacView.setText(proxyInfo.getPacFileUrl().toString());
                }
            }
        } else {
            setVisibility(R.id.proxy_warning_limited_support, View.GONE);
            setVisibility(R.id.proxy_fields, View.GONE);
            setVisibility(R.id.proxy_pac_field, View.GONE);
        }
    }

    void handle_saveconf() {
        try {
            if (mConTypeDhcp.isChecked()) {
                Log.i(TAG, "mode dhcp");
                if (setDhcp()) {
                    Log.i(TAG, "setDhcp success !");
                } else {
                    printErrorMsg(1);// todo
                }
            } else {
                Log.i(TAG, "mode static ip");
                int result = isValidAddress();
                if (result == 0) {
                    if (setStatic()) {
                        Log.i(TAG, "setStatic success !");
                    } else {
                        printErrorMsg(1);// todo
                    }
                } else {
                    printErrorMsg(result);
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private boolean NotIpAddress(String value) {
        int start = 0;
        int end = value.indexOf('.');
        int numBlocks = 0;
        while (start < value.length()) {
            if (end == -1) {
                end = value.length();
            }
            try {
                int block = Integer.parseInt(value.substring(start, end));
                if ((block > 255) || (block < 0)) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
            numBlocks++;
            start = end + 1;
            end = value.indexOf('.', start);
        }
        return (numBlocks != 4);
    }

    private void printErrorMsg(int type) {
        switch (type) {
        case 1:
            Toast.makeText(mContext, R.string.wifi_failed_save_message, Toast.LENGTH_LONG).show();
            break;
        case 2:
            Toast.makeText(mContext, R.string.wifi_ip_settings_invalid_ip_address, Toast.LENGTH_LONG).show();
            break;
        case 3:
            Toast.makeText(mContext, R.string.wifi_ip_settings_invalid_network_prefix_length, Toast.LENGTH_LONG).show();
            break;
        case 4:
            Toast.makeText(mContext, R.string.wifi_ip_settings_invalid_gateway, Toast.LENGTH_LONG).show();
            break;
        case 5:
            Toast.makeText(mContext, R.string.wifi_ip_settings_invalid_dns, Toast.LENGTH_LONG).show();
            break;
        default:
            break;
        }
    }

    private int isValidAddress() {
        int errorType = 0;
        if (NotIpAddress(mIpaddr.getText().toString())) {
            errorType = 2;
        }
        int prefix = Integer.parseInt(mPrefixLen.getText().toString());
        if (prefix < 0 || prefix > 32) {
            errorType = 3;
        }
        if (NotIpAddress(mGw.getText().toString())) {
            errorType = 4;
        }
        if (NotIpAddress(mDns1.getText().toString()) || NotIpAddress(mDns2.getText().toString())) {
            errorType = 5;
        }
        return errorType;
    }

    public String ipv4PrefixLengthToSubnetMask(int prefixLength) {
        try {
            return Inet4AddressUtils.getPrefixMaskAsInet4Address(prefixLength).getHostAddress();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void getEthernetInfo() {
        try {
            EthernetInfo info = new EthernetInfo(mCm);
            if (info != null) {
                if (info.mIpV4Address != null) {
                    mIpaddr.setText(info.mIpV4Address);
                } else {
                    mIpaddr.setText(DEFAULT_ADDRESS);
                }

                if (ipv4PrefixLengthToSubnetMask(info.prefixLen) != null) {
                    mPrefixLen.setText(String.valueOf(info.prefixLen));
                } else {
                    mPrefixLen.setText(DEFAULT_PREFIX_LEN);
                }

                if (info.gatewayAddress != null) {
                    mGw.setText(info.gatewayAddress);
                } else {
                    mGw.setText(DEFAULT_ADDRESS);
                }

                if (info.dns1Address != null) {
                    mDns1.setText(info.dns1Address);
                } else {
                    mDns1.setText(DEFAULT_ADDRESS);
                }

                if (info.dns2Address != null) {
                    mDns2.setText(info.dns2Address);
                } else {
                    mDns2.setText(DEFAULT_ADDRESS);
                }

            } else {
                mIpaddr.setText(DEFAULT_ADDRESS);
                mPrefixLen.setText(DEFAULT_ADDRESS);
                mGw.setText(DEFAULT_ADDRESS);
                mDns1.setText(DEFAULT_ADDRESS);
                mDns2.setText(DEFAULT_ADDRESS);
            }
        } catch (Exception e) {
            mIpaddr.setText(DEFAULT_ADDRESS);
            mPrefixLen.setText(DEFAULT_ADDRESS);
            mGw.setText(DEFAULT_ADDRESS);
            mDns1.setText(DEFAULT_ADDRESS);
            mDns2.setText(DEFAULT_ADDRESS);
        }
    }

    private boolean setStatic() {
        try {
            setProxy();
            mIpConfiguration.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
            final StaticIpConfiguration.Builder staticIpBuilder =
                    new StaticIpConfiguration.Builder();

            String ipAddr = mIpaddr.getText().toString();
            Inet4Address inetAddr = null;
            if (!TextUtils.isEmpty(ipAddr)) {
                Log.i(TAG, "Static IP address =" + ipAddr);

                try {
                    inetAddr = (Inet4Address) InetAddresses.parseNumericAddress(ipAddr);
                } catch (IllegalArgumentException | ClassCastException e) {
                    Log.e(TAG, "Static IP configuration failed with address parse error");
                }

                if (inetAddr == null || inetAddr.equals(Inet4Address.ANY)) {
                    Log.e(TAG, "Static IP configuration failed with inetAddr error");
                }
            }

            int networkPrefixLength = Integer.parseInt(mPrefixLen.getText().toString());
            try {
                staticIpBuilder.setIpAddress(new LinkAddress(inetAddr, networkPrefixLength));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Static IP configuration failed with ipaddress set error");
            }

            String gateway = mGw.getText().toString();
            if (!TextUtils.isEmpty(gateway)) {
                try {
                    staticIpBuilder.setGateway(InetAddresses.parseNumericAddress(gateway));
                } catch (IllegalArgumentException | ClassCastException e) {
                    Log.e(TAG, "Static IP configuration failed with gateway set error");
                }
            }

            final ArrayList<InetAddress> dnsServers = new ArrayList<>();
            String dns1 = mDns1.getText().toString();
            String dns2 = mDns2.getText().toString();
            if (!TextUtils.isEmpty(dns1)) {
                try {
                    dnsServers.add(InetAddresses.parseNumericAddress(dns1));
                    if (!TextUtils.isEmpty(dns2)) {
                        try {
                            dnsServers.add(InetAddresses.parseNumericAddress(dns2));
                        } catch (IllegalArgumentException | ClassCastException e) {
                            Log.e(TAG,"Static IP configuration failed with dns error");
                        }
                    }
                } catch (IllegalArgumentException | ClassCastException e) {
                    Log.e(TAG,"Static IP configuration failed with dns error");
                }
            }
            staticIpBuilder.setDnsServers(dnsServers);

            mIpConfiguration.setStaticIpConfiguration(staticIpBuilder.build());
            mIpConfiguration.setProxySettings(mProxySettings);
            mIpConfiguration.setHttpProxy(mHttpProxy);
            final EthernetNetworkUpdateRequest request =
                    new EthernetNetworkUpdateRequest.Builder()
                            .setIpConfiguration(mIpConfiguration)
                            .build();
            mEthManager.updateConfiguration(getEthernetInterfaceName(), request, r -> r.run(),
                    null /* network listener */);
            return true;
        } catch (Exception e) {
            // TODO: handle exception
        }
        return false;
    }

    private boolean setDhcp() {
        try {
            setProxy();
            mIpConfiguration.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
            mIpConfiguration.setProxySettings(mProxySettings);
            mIpConfiguration.setHttpProxy(mHttpProxy);
            mAvailableInterfaces.put(getifaceName(), mIpConfiguration);

            return true;
        } catch (Exception e) {
            // TODO: handle exception
        }
        return false;
    }

    private void setProxy() {
        try {
            final int selectedPosition = mProxySettingsSpinner.getSelectedItemPosition();
            mProxySettings = ProxySettings.NONE;
            mHttpProxy = null;
            if (selectedPosition == PROXY_STATIC && mProxyHostView != null) {
                mProxySettings = ProxySettings.STATIC;
                String host = mProxyHostView.getText().toString();
                String portStr = mProxyPortView.getText().toString();
                String exclusionList = mProxyExclusionListView.getText().toString();
                int port = 0;
                int result = 0;
                try {
                    port = Integer.parseInt(portStr);
                    result = ProxySelector.validate(host, portStr, exclusionList);
                } catch (NumberFormatException e) {
                    result = R.string.proxy_error_invalid_port;
                }
                if (result == 0) {
                    mHttpProxy = ProxyInfo.buildDirectProxy(host, port,ProxyUtils.exclusionStringAsList(exclusionList));
                }
            } else if ((selectedPosition == PROXY_PAC) && (mProxyPacView != null)) {
                mProxySettings = ProxySettings.PAC;
                CharSequence uriSequence = mProxyPacView.getText();
                //TODO: validate the PAC is null or not
                Uri uri = Uri.parse(uriSequence.toString());
                mHttpProxy =  ProxyInfo.buildPacProxy(uri);
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    private Inet4Address getIPv4Address(String text) {
        try {
            return (Inet4Address) InetAddresses.parseNumericAddress(text);
        } catch (Exception e) {
            return null;
        }
    }

    private void setVisibility(int id, int visibility) {
        final View v = mView.findViewById(id);
        if (v != null) {
            v.setVisibility(visibility);
        }
    }
}
