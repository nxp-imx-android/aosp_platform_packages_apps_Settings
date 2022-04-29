/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.vpn2;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.UserHandle;
import android.util.ArraySet;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.testutils.FakeFeatureFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class VpnSettingsTest {
    private static final String ADVANCED_VPN_GROUP_KEY = "advanced_vpn_group";
    private static final String VPN_GROUP_KEY = "vpn_group";
    private static final String ADVANCED_VPN_GROUP_TITLE = "advanced_vpn_group_title";
    private static final String VPN_GROUP_TITLE = "vpn_group_title";
    private static final String FAKE_PACKAGE_NAME = "com.fake.package.name";
    private static final String ADVANCED_VPN_GROUP_PACKAGE_NAME = "com.advanced.package.name";
    private static final int USER_ID_1 = UserHandle.USER_NULL;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private VpnSettings mVpnSettings;
    private Context mContext;
    private PreferenceManager mPreferenceManager;
    private PreferenceScreen mPreferenceScreen;
    private PreferenceGroup mAdvancedVpnGroup;
    private PreferenceGroup mVpnGroup;
    private FakeFeatureFactory mFakeFeatureFactory;

    @Before
    @UiThreadTest
    public void setUp() throws PackageManager.NameNotFoundException {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mVpnSettings = spy(new VpnSettings());
        mContext = spy(ApplicationProvider.getApplicationContext());
        mAdvancedVpnGroup = spy(new PreferenceCategory(mContext));
        mVpnGroup = spy(new PreferenceCategory(mContext));
        mAdvancedVpnGroup.setKey(ADVANCED_VPN_GROUP_KEY);
        mVpnGroup.setKey(VPN_GROUP_KEY);
        mPreferenceManager = new PreferenceManager(mContext);
        mPreferenceScreen = mPreferenceManager.createPreferenceScreen(mContext);
        mPreferenceScreen.addPreference(mAdvancedVpnGroup);
        mPreferenceScreen.addPreference(mVpnGroup);
        mFakeFeatureFactory = FakeFeatureFactory.setupForTest();
        mVpnSettings.init(mPreferenceScreen, mFakeFeatureFactory.getAdvancedVpnFeatureProvider());

        when(mVpnSettings.getContext()).thenReturn(mContext);
        when(mFakeFeatureFactory.mAdvancedVpnFeatureProvider
                .getAdvancedVpnPreferenceGroupTitle(mContext)).thenReturn(ADVANCED_VPN_GROUP_TITLE);
        when(mFakeFeatureFactory.mAdvancedVpnFeatureProvider.getVpnPreferenceGroupTitle(mContext))
                .thenReturn(VPN_GROUP_TITLE);
        when(mFakeFeatureFactory.mAdvancedVpnFeatureProvider.getAdvancedVpnPackageName())
                .thenReturn(ADVANCED_VPN_GROUP_PACKAGE_NAME);
        doReturn(mContext).when(mContext).createContextAsUser(any(), anyInt());
        doReturn(mContext).when(mContext).createPackageContextAsUser(any(), anyInt(), any());
        doReturn(mPreferenceManager).when(mVpnGroup).getPreferenceManager();
        doReturn(mPreferenceManager).when(mAdvancedVpnGroup).getPreferenceManager();
    }

    @Test
    public void setShownAdvancedPreferences_hasGeneralVpn_returnsVpnCountAs1() {
        Set<Preference> updates = new ArraySet<>();
        AppPreference pref =
                spy(new AppPreference(mContext, USER_ID_1, FAKE_PACKAGE_NAME));
        updates.add(pref);

        mVpnSettings.setShownAdvancedPreferences(updates);

        assertThat(mVpnGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mVpnGroup.isVisible()).isTrue();
        assertThat(mAdvancedVpnGroup.isVisible()).isFalse();
    }

    @Test
    public void setShownAdvancedPreferences_hasAdvancedVpn_returnsAdvancedVpnCountAs1() {
        Set<Preference> updates = new ArraySet<>();
        AppPreference pref =
                spy(new AppPreference(mContext, USER_ID_1, ADVANCED_VPN_GROUP_PACKAGE_NAME));
        updates.add(pref);

        mVpnSettings.setShownAdvancedPreferences(updates);

        assertThat(mAdvancedVpnGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mAdvancedVpnGroup.isVisible()).isTrue();
        assertThat(mVpnGroup.isVisible()).isFalse();
    }

    @Test
    public void setShownAdvancedPreferences_noVpn_returnsEmpty() {
        Set<Preference> updates = new ArraySet<>();

        mVpnSettings.setShownAdvancedPreferences(updates);

        assertThat(mAdvancedVpnGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mVpnGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mAdvancedVpnGroup.isVisible()).isFalse();
        assertThat(mVpnGroup.isVisible()).isFalse();
    }
}