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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.EthernetManager;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import android.app.settings.SettingsEnums;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settingslib.search.Indexable;
import com.android.settings.R;

public class EthernetSettings extends DashboardFragment implements Preference.OnPreferenceChangeListener, Indexable {
    private static final String TAG = "EthernetSettings";
    private String KEY_ETH_MAC_ADDRESS ;
    private String KEY_ETH_IP_ADDRESS;
    private String KEY_ETH_NET_MASK;
    private String KEY_ETH_GATEWAY;
    private String KEY_ETH_DNS1;
    private String KEY_ETH_DNS2;
    private String KEY_ETH_IPV6_ADDRESS;
    private String KEY_ETH_STATIC_CONFIG;

    private EthernetDialog mEthDialog = null;
    private Preference mEthConfigPref;
    private ConnectivityManager mCm;
    private EthernetManager mEthManager;
    private IntentFilter mIntentFilter;

    private static String mEthMACAddress = null;
    private static String mEthIpAddress = null;
    private static String mEthNetmask = null;
    private static String mEthGateway = null;
    private static String mEthdns1 = null;
    private static String mEthdns2 = null;
    private static String mEthIpV6Address = null;
    private final static String nullIpInfo = "0.0.0.0";

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.ethernet_settings;
    }

    public EthernetSettings() {
        super();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UpdateEthStatus();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        KEY_ETH_MAC_ADDRESS = getResources().getString(R.string.eth_mac_address);
        KEY_ETH_IP_ADDRESS = getResources().getString(R.string.eth_ipaddr);
        KEY_ETH_NET_MASK = getResources().getString(R.string.eth_mask);
        KEY_ETH_GATEWAY = getResources().getString(R.string.eth_gw);
        KEY_ETH_DNS1 = getResources().getString(R.string.eth_dns1);
        KEY_ETH_DNS2 = getResources().getString(R.string.eth_dns2);
        KEY_ETH_IPV6_ADDRESS = getResources().getString(R.string.eth_ipv6addr);
        KEY_ETH_STATIC_CONFIG = getResources().getString(R.string.eth_static_config);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        mEthConfigPref = preferenceScreen.findPreference(KEY_ETH_STATIC_CONFIG);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    }

    @Override
    public void onStart() {
        super.onStart();
        mCm = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        mEthManager = (EthernetManager) getActivity().getSystemService(Context.ETHERNET_SERVICE);
        mEthDialog = new EthernetDialog(getActivity(), mCm, mEthManager);
    }

    @Override
    public void onResume() {
        final Activity activity = getActivity();
        activity.registerReceiver(mReceiver, mIntentFilter);
        super.onResume();
        try {
            UpdateEthStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mReceiver);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mEthConfigPref) {
            final SettingsActivity activity = (SettingsActivity) getActivity();
                if (mEthDialog != null)
                    mEthDialog.show();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void getEthInfo() {
        EthernetInfo info = new EthernetInfo(mCm);
        if (info != null) {
            mEthMACAddress = info.mHwAddress;
            if ((mEthMACAddress == null) || (mEthMACAddress.equals(""))) {
                mEthMACAddress = nullIpInfo;
            }
            mEthIpAddress = info.mIpV4Address;
            if ((mEthIpAddress == null) || (mEthIpAddress.equals(""))) {
                mEthIpAddress = nullIpInfo;
            }
            mEthNetmask = mEthDialog.ipv4PrefixLengthToSubnetMask(info.prefixLen);
            if ((mEthNetmask == null) || (mEthNetmask.equals(""))) {
                mEthNetmask = nullIpInfo;
            }
            mEthGateway = info.gatewayAddress;
            if ((mEthGateway == null) || (mEthGateway.equals(""))) {
                mEthGateway = nullIpInfo;
            }
            mEthdns1 = info.dns1Address;
            if ((mEthdns1 == null) || (mEthdns1.equals(""))) {
                mEthdns1 = nullIpInfo;
            }
            mEthdns2 = info.dns2Address;
            if ((mEthdns2 == null) || (mEthdns2.equals(""))) {
                mEthdns2 = nullIpInfo;
            }
            mEthIpV6Address = info.mIpV6Address;
            if ((mEthIpV6Address == null) || (mEthIpV6Address.equals(""))) {
                mEthIpV6Address = nullIpInfo;
            }
        } else {
            getNullInfo();
        }
    }

    private void getNullInfo() {
        mEthMACAddress = nullIpInfo;
        mEthIpAddress = nullIpInfo;
        mEthNetmask = nullIpInfo;
        mEthGateway = nullIpInfo;
        mEthdns1 = nullIpInfo;
        mEthdns2 = nullIpInfo;
        mEthIpV6Address = nullIpInfo;
    }

    private void UpdateEthStatus() {
        boolean ethernetConnected = mEthDialog.isEthernetConnected();
        try {
            if (!ethernetConnected) {
                getNullInfo();
                setSummary();
            } else {
                getEthInfo();
                setSummary();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void setSummary() {
        setStringSummary(KEY_ETH_MAC_ADDRESS, mEthMACAddress);
        setStringSummary(KEY_ETH_IP_ADDRESS, mEthIpAddress);
        setStringSummary(KEY_ETH_NET_MASK, mEthNetmask);
        setStringSummary(KEY_ETH_GATEWAY, mEthGateway);
        setStringSummary(KEY_ETH_DNS1, mEthdns1);
        setStringSummary(KEY_ETH_DNS2, mEthdns2);
        setStringSummary(KEY_ETH_IPV6_ADDRESS, mEthIpV6Address);

    }

    private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            findPreference(preference).setSummary("");
        }
    }
}
