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

import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.LinkAddress;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import android.util.SparseArray;
public class EthernetInfo {
    final String TAG = "EthernetInfo";
    private ConnectivityManager mCm;

    public String mType = null;
    public String mState = null;
    public String mInterfaceName = null;
    public String mHwAddress = null;
    public String mIpAddresses = null;
    public String mIpV4Address = null;
    public String mIpV6Address = null;
    public int prefixLen = 0;
    public String gatewayAddress = null;
    public String dns1Address = null;
    public String dns2Address = null;
    public String mDnsAddresses = null;
    public String mDomains = null;
    public String mRoutes = null;
    public String mBandwidth = null;

    public EthernetInfo(ConnectivityManager connectivityManager) {
        mCm = connectivityManager;
        getEthInfo();
    }

    private void getEthInfo() {
        Network network = mCm.getActiveNetwork();
        LinkProperties mLinkProp = mCm.getLinkProperties(network);

        if (mLinkProp != null) {
            NetworkInfo nInfo = mCm.getNetworkInfo(network);

            NetworkCapabilities nCaps = mCm.getNetworkCapabilities(network);

            NetworkInterface nIface = null;
            try {
                nIface = NetworkInterface.getByName(mLinkProp.getInterfaceName());
            } catch (SocketException exception) {
                Log.e(TAG, "SocketException -- Failed to get interface info");
            }

            mInterfaceName = mLinkProp.getInterfaceName();
            mHwAddress = getMacAddress(nIface);
            mIpAddresses = mLinkProp.getLinkAddresses().toString();
            mDnsAddresses = mLinkProp.getDnsServers().toString();
            mDomains = mLinkProp.getDomains();
            mRoutes = mLinkProp.getRoutes().toString();
            mType = nInfo.getTypeName();
            mState = nInfo.getState().name() + "/" + nInfo.getDetailedState().name();
            mBandwidth = "Bandwidth (Down/Up): " + nCaps.getLinkDownstreamBandwidthKbps()
                    + " Kbps/" + nCaps.getLinkUpstreamBandwidthKbps() + " Kbps";

            Log.i(TAG, "mInterfaceName " + mInterfaceName);
            Log.i(TAG, "mHwAddress " + mHwAddress);
            Log.i(TAG, "mIpAddresses " + mIpAddresses);
            Log.i(TAG, "mDnsAddresses " + mDnsAddresses);
            Log.i(TAG, "mDomains " + mDomains);
            Log.i(TAG, "mRoutes " + mRoutes);
            Log.i(TAG, "mType " + mType);
            Log.i(TAG, "mState " + mState);
            Log.i(TAG, "mBandwidth " + mBandwidth);

            for (LinkAddress addr : mLinkProp.getLinkAddresses()) {
                if (addr.getAddress() instanceof Inet4Address) {
                    mIpV4Address = addr.getAddress().getHostAddress();
                    prefixLen = addr.getPrefixLength();
                } else if (addr.getAddress() instanceof Inet6Address) {
                    mIpV6Address = (addr.getAddress().getHostAddress());
                }
            }

            List<RouteInfo> routeInfoList = mLinkProp.getRoutes();
            if ((routeInfoList != null) && (!routeInfoList.isEmpty())) {
                boolean foundDefaultRoute = false;
                for (RouteInfo r : routeInfoList) {
                    if (r.isDefaultRoute()) {
                        gatewayAddress = r.getGateway().getHostAddress();
                        foundDefaultRoute = true;
                        break;
                    }
                }
                if(!foundDefaultRoute) {
                    RouteInfo r = routeInfoList.get(routeInfoList.size() - 1);
                    InetAddress inetGateway = r.getGateway();
                    gatewayAddress = inetGateway.getHostAddress();
                }
            }
            List<InetAddress> dnsServerList = mLinkProp.getDnsServers();
            if ((dnsServerList != null) && (!dnsServerList.isEmpty())) {
                dns1Address = dnsServerList.get(0).getHostAddress();
                if (dnsServerList.size() > 1) {
                    dns2Address = dnsServerList.get(1).getHostAddress();
                }
            }
        } else {
            Log.d(TAG, "getEthInfo null");
        }
    }

    private String getMacAddress(NetworkInterface ni) {
        if (ni == null) {
            return "??:??:??:??:??:??";
        }

        byte[] mac = null;
        try {
            mac = ni.getHardwareAddress();
        } catch (SocketException exception) {
            Log.e(TAG, "Failed to get interface MAC address");
            return "??:??:??:??:??:??";
        }

        if (mac == null) {
            return "??:??:??:??:??:??";
        }

        StringBuilder sb = new StringBuilder(18);
        for (byte b : mac) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
