/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.pm;

import static com.google.common.truth.Truth.assertWithMessage;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

import android.content.IIntentReceiver;
import android.content.pm.PackageManagerInternal;
import android.os.Bundle;
import android.util.SparseArray;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.HexDump;
import com.android.server.pm.PerPackageReadTimeouts.Timeouts;
import com.android.server.pm.PerPackageReadTimeouts.VersionCodes;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

// atest PackageManagerServiceTest
// runtest -c com.android.server.pm.PackageManagerServiceTest frameworks-services
// bit FrameworksServicesTests:com.android.server.pm.PackageManagerServiceTest
@RunWith(AndroidJUnit4.class)
public class PackageManagerServiceTest {
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testPackageRemoval() throws Exception {
        class PackageSenderImpl implements PackageSender {
            public void sendPackageBroadcast(final String action, final String pkg,
                    final Bundle extras, final int flags, final String targetPkg,
                    final IIntentReceiver finishedReceiver, final int[] userIds,
                    int[] instantUserIds, SparseArray<int[]> broadcastAllowList) {
            }

            public void sendPackageAddedForNewUsers(String packageName,
                    boolean sendBootComplete, boolean includeStopped, int appId,
                    int[] userIds, int[] instantUserIds, int dataLoaderType) {
            }

            @Override
            public void notifyPackageAdded(String packageName, int uid) {
            }

            @Override
            public void notifyPackageChanged(String packageName, int uid) {

            }

            @Override
            public void notifyPackageRemoved(String packageName, int uid) {
            }
        }

        PackageSenderImpl sender = new PackageSenderImpl();
        PackageSetting setting = null;
        PackageManagerService.PackageRemovedInfo pri =
                new PackageManagerService.PackageRemovedInfo(sender);

        // Initial conditions: nothing there
        Assert.assertNull(pri.removedUsers);
        Assert.assertNull(pri.broadcastUsers);

        // populateUsers with nothing leaves nothing
        pri.populateUsers(null, setting);
        Assert.assertNull(pri.broadcastUsers);

        // Create a real (non-null) PackageSetting and confirm that the removed
        // users are copied properly
        setting = new PackageSetting("name", "realName", new File("codePath"),
                "legacyNativeLibraryPathString", "primaryCpuAbiString", "secondaryCpuAbiString",
                "cpuAbiOverrideString", 0, 0, 0, 0,
                null, null, null);
        pri.populateUsers(new int[] {
                1, 2, 3, 4, 5
        }, setting);
        Assert.assertNotNull(pri.broadcastUsers);
        Assert.assertEquals(5, pri.broadcastUsers.length);
        Assert.assertNotNull(pri.instantUserIds);
        Assert.assertEquals(0, pri.instantUserIds.length);

        // Exclude a user
        pri.broadcastUsers = null;
        final int EXCLUDED_USER_ID = 4;
        setting.setInstantApp(true, EXCLUDED_USER_ID);
        pri.populateUsers(new int[] {
                1, 2, 3, EXCLUDED_USER_ID, 5
        }, setting);
        Assert.assertNotNull(pri.broadcastUsers);
        Assert.assertEquals(4, pri.broadcastUsers.length);
        Assert.assertNotNull(pri.instantUserIds);
        Assert.assertEquals(1, pri.instantUserIds.length);

        // TODO: test that sendApplicationHiddenForUser() actually fills in
        // broadcastUsers
    }

    @Test
    public void testPartitions() throws Exception {
        String[] partitions = { "system", "vendor", "odm", "oem", "product", "system_ext" };
        String[] appdir = { "app", "priv-app" };
        for (int i = 0; i < partitions.length; i++) {
            final PackageManagerService.ScanPartition scanPartition =
                    PackageManagerService.SYSTEM_PARTITIONS.get(i);
            for (int j = 0; j < appdir.length; j++) {
                File path = new File(String.format("%s/%s/A.apk", partitions[i], appdir[j]));
                Assert.assertEquals(j == 1 && i != 3, scanPartition.containsPrivApp(path));

                final int scanFlag = scanPartition.scanFlag;
                Assert.assertEquals(i == 1, scanFlag == PackageManagerService.SCAN_AS_VENDOR);
                Assert.assertEquals(i == 2, scanFlag == PackageManagerService.SCAN_AS_ODM);
                Assert.assertEquals(i == 3, scanFlag == PackageManagerService.SCAN_AS_OEM);
                Assert.assertEquals(i == 4, scanFlag == PackageManagerService.SCAN_AS_PRODUCT);
                Assert.assertEquals(i == 5, scanFlag == PackageManagerService.SCAN_AS_SYSTEM_EXT);
            }
        }
    }

    @Test
    public void testKnownPackageToString_shouldNotGetUnknown() {
        final List<String> packageNames = new ArrayList<>();
        for (int i = 0; i <= PackageManagerInternal.LAST_KNOWN_PACKAGE; i++) {
            packageNames.add(PackageManagerInternal.knownPackageToString(i));
        }
        assertWithMessage(
                "The Ids of KnownPackage should be continuous and the string representation "
                        + "should not be unknown.").that(
                packageNames).containsNoneIn(Lists.newArrayList("Unknown"));
    }

    @Test
    public void testKnownPackage_lastKnownPackageIsTheLast() throws Exception {
        final List<Integer> knownPackageIds = getKnownPackageIdsList();
        assertWithMessage(
                "The last KnownPackage Id should be assigned to PackageManagerInternal"
                        + ".LAST_KNOWN_PACKAGE.").that(
                knownPackageIds.get(knownPackageIds.size() - 1)).isEqualTo(
                PackageManagerInternal.LAST_KNOWN_PACKAGE);
    }

    @Test
    public void testKnownPackage_IdsShouldBeUniqueAndContinuous() throws Exception {
        final List<Integer> knownPackageIds = getKnownPackageIdsList();
        for (int i = 0, size = knownPackageIds.size(); i < size - 1; i++) {
            assertWithMessage(
                    "The KnownPackage Ids should be unique and continuous. KnownPackageIds = "
                            + Arrays.toString(knownPackageIds.toArray())).that(
                    knownPackageIds.get(i) + 1).isEqualTo(knownPackageIds.get(i + 1));
        }
    }

    @Test
    public void testTimeouts() {
        Timeouts defaults = Timeouts.parse("3600000001:3600000002:3600000003");
        Assert.assertEquals(3600000001L, defaults.minTimeUs);
        Assert.assertEquals(3600000002L, defaults.minPendingTimeUs);
        Assert.assertEquals(3600000003L, defaults.maxPendingTimeUs);

        Timeouts empty = Timeouts.parse("");
        Assert.assertEquals(3600000000L, empty.minTimeUs);
        Assert.assertEquals(3600000000L, empty.minPendingTimeUs);
        Assert.assertEquals(3600000000L, empty.maxPendingTimeUs);

        Timeouts partial0 = Timeouts.parse("10000::");
        Assert.assertEquals(10000L, partial0.minTimeUs);
        Assert.assertEquals(3600000000L, partial0.minPendingTimeUs);
        Assert.assertEquals(3600000000L, partial0.maxPendingTimeUs);

        Timeouts partial1 = Timeouts.parse("10000:10001:");
        Assert.assertEquals(10000L, partial1.minTimeUs);
        Assert.assertEquals(10001L, partial1.minPendingTimeUs);
        Assert.assertEquals(3600000000L, partial1.maxPendingTimeUs);

        Timeouts fullDefault = Timeouts.parse("3600000000:3600000000:3600000000");
        Assert.assertEquals(3600000000L, fullDefault.minTimeUs);
        Assert.assertEquals(3600000000L, fullDefault.minPendingTimeUs);
        Assert.assertEquals(3600000000L, fullDefault.maxPendingTimeUs);

        Timeouts full = Timeouts.parse("10000:10001:10002");
        Assert.assertEquals(10000L, full.minTimeUs);
        Assert.assertEquals(10001L, full.minPendingTimeUs);
        Assert.assertEquals(10002L, full.maxPendingTimeUs);

        Timeouts invalid0 = Timeouts.parse(":10000");
        Assert.assertEquals(3600000000L, invalid0.minTimeUs);
        Assert.assertEquals(3600000000L, invalid0.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid0.maxPendingTimeUs);

        Timeouts invalid1 = Timeouts.parse(":10000::");
        Assert.assertEquals(3600000000L, invalid1.minTimeUs);
        Assert.assertEquals(3600000000L, invalid1.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid1.maxPendingTimeUs);

        Timeouts invalid2 = Timeouts.parse("10000:10001:abcd");
        Assert.assertEquals(10000L, invalid2.minTimeUs);
        Assert.assertEquals(10001L, invalid2.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid2.maxPendingTimeUs);

        Timeouts invalid3 = Timeouts.parse(":10000:");
        Assert.assertEquals(3600000000L, invalid3.minTimeUs);
        Assert.assertEquals(3600000000L, invalid3.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid3.maxPendingTimeUs);

        Timeouts invalid4 = Timeouts.parse("abcd:10001:10002");
        Assert.assertEquals(3600000000L, invalid4.minTimeUs);
        Assert.assertEquals(3600000000L, invalid4.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid4.maxPendingTimeUs);

        Timeouts invalid5 = Timeouts.parse("::1000000000000000000000000");
        Assert.assertEquals(3600000000L, invalid5.minTimeUs);
        Assert.assertEquals(3600000000L, invalid5.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid5.maxPendingTimeUs);

        Timeouts invalid6 = Timeouts.parse("-10000:10001:10002");
        Assert.assertEquals(3600000000L, invalid6.minTimeUs);
        Assert.assertEquals(3600000000L, invalid6.minPendingTimeUs);
        Assert.assertEquals(3600000000L, invalid6.maxPendingTimeUs);
    }

    @Test
    public void testVersionCodes() {
        final VersionCodes defaults = VersionCodes.parse("");
        Assert.assertEquals(Long.MIN_VALUE, defaults.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, defaults.maxVersionCode);

        VersionCodes single = VersionCodes.parse("191000070");
        Assert.assertEquals(191000070, single.minVersionCode);
        Assert.assertEquals(191000070, single.maxVersionCode);

        VersionCodes single2 = VersionCodes.parse("191000070-191000070");
        Assert.assertEquals(191000070, single2.minVersionCode);
        Assert.assertEquals(191000070, single2.maxVersionCode);

        VersionCodes upto = VersionCodes.parse("-191000070");
        Assert.assertEquals(Long.MIN_VALUE, upto.minVersionCode);
        Assert.assertEquals(191000070, upto.maxVersionCode);

        VersionCodes andabove = VersionCodes.parse("191000070-");
        Assert.assertEquals(191000070, andabove.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, andabove.maxVersionCode);

        VersionCodes range = VersionCodes.parse("191000070-201000070");
        Assert.assertEquals(191000070, range.minVersionCode);
        Assert.assertEquals(201000070, range.maxVersionCode);

        VersionCodes invalid0 = VersionCodes.parse("201000070-191000070");
        Assert.assertEquals(Long.MIN_VALUE, invalid0.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, invalid0.maxVersionCode);

        VersionCodes invalid1 = VersionCodes.parse("abcd-191000070");
        Assert.assertEquals(Long.MIN_VALUE, invalid1.minVersionCode);
        Assert.assertEquals(191000070, invalid1.maxVersionCode);

        VersionCodes invalid2 = VersionCodes.parse("abcd");
        Assert.assertEquals(Long.MIN_VALUE, invalid2.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, invalid2.maxVersionCode);

        VersionCodes invalid3 = VersionCodes.parse("191000070-abcd");
        Assert.assertEquals(191000070, invalid3.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, invalid3.maxVersionCode);
    }

    @Test
    public void testPerPackageReadTimeouts() {
        final String sha256 = "336faefc91bb2dddf9b21829106fbc607b862132fecd273e1b6b3ea55f09d4e1";
        final VersionCodes defVCs = VersionCodes.parse("");
        final Timeouts defTs = Timeouts.parse("3600000001:3600000002:3600000003");

        PerPackageReadTimeouts empty = PerPackageReadTimeouts.parse("", defVCs, defTs);
        Assert.assertNull(empty);

        PerPackageReadTimeouts packageOnly = PerPackageReadTimeouts.parse("package.com", defVCs,
                defTs);
        Assert.assertEquals("package.com", packageOnly.packageName);
        Assert.assertEquals(null, packageOnly.sha256certificate);
        Assert.assertEquals(Long.MIN_VALUE, packageOnly.versionCodes.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, packageOnly.versionCodes.maxVersionCode);
        Assert.assertEquals(3600000001L, packageOnly.timeouts.minTimeUs);
        Assert.assertEquals(3600000002L, packageOnly.timeouts.minPendingTimeUs);
        Assert.assertEquals(3600000003L, packageOnly.timeouts.maxPendingTimeUs);

        PerPackageReadTimeouts packageHash = PerPackageReadTimeouts.parse(
                "package.com:" + sha256, defVCs, defTs);
        Assert.assertEquals("package.com", packageHash.packageName);
        Assert.assertEquals(sha256, bytesToHexString(packageHash.sha256certificate));
        Assert.assertEquals(Long.MIN_VALUE, packageHash.versionCodes.minVersionCode);
        Assert.assertEquals(Long.MAX_VALUE, packageHash.versionCodes.maxVersionCode);
        Assert.assertEquals(3600000001L, packageHash.timeouts.minTimeUs);
        Assert.assertEquals(3600000002L, packageHash.timeouts.minPendingTimeUs);
        Assert.assertEquals(3600000003L, packageHash.timeouts.maxPendingTimeUs);

        PerPackageReadTimeouts packageVersionCode = PerPackageReadTimeouts.parse(
                "package.com::191000070", defVCs, defTs);
        Assert.assertEquals("package.com", packageVersionCode.packageName);
        Assert.assertEquals(null, packageVersionCode.sha256certificate);
        Assert.assertEquals(191000070, packageVersionCode.versionCodes.minVersionCode);
        Assert.assertEquals(191000070, packageVersionCode.versionCodes.maxVersionCode);
        Assert.assertEquals(3600000001L, packageVersionCode.timeouts.minTimeUs);
        Assert.assertEquals(3600000002L, packageVersionCode.timeouts.minPendingTimeUs);
        Assert.assertEquals(3600000003L, packageVersionCode.timeouts.maxPendingTimeUs);

        PerPackageReadTimeouts full = PerPackageReadTimeouts.parse(
                "package.com:" + sha256 + ":191000070-201000070:10001:10002:10003", defVCs, defTs);
        Assert.assertEquals("package.com", full.packageName);
        Assert.assertEquals(sha256, bytesToHexString(full.sha256certificate));
        Assert.assertEquals(191000070, full.versionCodes.minVersionCode);
        Assert.assertEquals(201000070, full.versionCodes.maxVersionCode);
        Assert.assertEquals(10001L, full.timeouts.minTimeUs);
        Assert.assertEquals(10002L, full.timeouts.minPendingTimeUs);
        Assert.assertEquals(10003L, full.timeouts.maxPendingTimeUs);
    }

    @Test
    public void testGetPerPackageReadTimeouts() {
        Assert.assertEquals(0, getPerPackageReadTimeouts(null).length);
        Assert.assertEquals(0, getPerPackageReadTimeouts("").length);
        Assert.assertEquals(0, getPerPackageReadTimeouts(",,,,").length);

        final String sha256 = "0fae93f1a7925b4c68bbea80ad3eaa41acfc9bc6f10bf1054f5d93a2bd556093";

        PerPackageReadTimeouts[] singlePackage = getPerPackageReadTimeouts(
                "package.com:" + sha256 + ":191000070-201000070:10001:10002:10003");
        Assert.assertEquals(1, singlePackage.length);
        Assert.assertEquals("package.com", singlePackage[0].packageName);
        Assert.assertEquals(sha256, bytesToHexString(singlePackage[0].sha256certificate));
        Assert.assertEquals(191000070, singlePackage[0].versionCodes.minVersionCode);
        Assert.assertEquals(201000070, singlePackage[0].versionCodes.maxVersionCode);
        Assert.assertEquals(10001L, singlePackage[0].timeouts.minTimeUs);
        Assert.assertEquals(10002L, singlePackage[0].timeouts.minPendingTimeUs);
        Assert.assertEquals(10003L, singlePackage[0].timeouts.maxPendingTimeUs);

        PerPackageReadTimeouts[] multiPackage = getPerPackageReadTimeouts("package.com:" + sha256
                + ":191000070-201000070:10001:10002:10003,package1.com::123456");
        Assert.assertEquals(2, multiPackage.length);
        Assert.assertEquals("package.com", multiPackage[0].packageName);
        Assert.assertEquals(sha256, bytesToHexString(multiPackage[0].sha256certificate));
        Assert.assertEquals(191000070, multiPackage[0].versionCodes.minVersionCode);
        Assert.assertEquals(201000070, multiPackage[0].versionCodes.maxVersionCode);
        Assert.assertEquals(10001L, multiPackage[0].timeouts.minTimeUs);
        Assert.assertEquals(10002L, multiPackage[0].timeouts.minPendingTimeUs);
        Assert.assertEquals(10003L, multiPackage[0].timeouts.maxPendingTimeUs);
        Assert.assertEquals("package1.com", multiPackage[1].packageName);
        Assert.assertEquals(null, multiPackage[1].sha256certificate);
        Assert.assertEquals(123456, multiPackage[1].versionCodes.minVersionCode);
        Assert.assertEquals(123456, multiPackage[1].versionCodes.maxVersionCode);
        Assert.assertEquals(3600000001L, multiPackage[1].timeouts.minTimeUs);
        Assert.assertEquals(3600000002L, multiPackage[1].timeouts.minPendingTimeUs);
        Assert.assertEquals(3600000003L, multiPackage[1].timeouts.maxPendingTimeUs);
    }

    private static PerPackageReadTimeouts[] getPerPackageReadTimeouts(String knownDigestersList) {
        final String defaultTimeouts = "3600000001:3600000002:3600000003";
        List<PerPackageReadTimeouts> result = PerPackageReadTimeouts.parseDigestersList(
                defaultTimeouts, knownDigestersList);
        if (result == null) {
            return null;
        }
        return result.toArray(new PerPackageReadTimeouts[result.size()]);
    }

    private static String bytesToHexString(byte[] bytes) {
        return HexDump.toHexString(bytes, 0, bytes.length, /*upperCase=*/ false);
    }

    private List<Integer> getKnownPackageIdsList() throws IllegalAccessException {
        final ArrayList<Integer> knownPackageIds = new ArrayList<>();
        final Field[] allFields = PackageManagerInternal.class.getDeclaredFields();
        for (Field field : allFields) {
            final int modifier = field.getModifiers();
            if (isPublic(modifier) && isStatic(modifier) && isFinal(modifier)
                    && Pattern.matches("PACKAGE(_[A-Z]+)+", field.getName())) {
                knownPackageIds.add(field.getInt(null));
            }
        }
        Collections.sort(knownPackageIds);
        return knownPackageIds;
    }
}
