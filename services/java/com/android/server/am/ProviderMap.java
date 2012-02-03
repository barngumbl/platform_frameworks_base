/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.am;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.UserId;
import android.util.Slog;
import android.util.SparseArray;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps track of content providers by authority (name) and class. It separates the mapping by
 * user and ones that are not user-specific (system providers).
 */
public class ProviderMap {

    private static final String TAG = "ProviderMap";

    private static final boolean DBG = false;

    private final HashMap<String, ContentProviderRecord> mGlobalByName
            = new HashMap<String, ContentProviderRecord>();
    private final HashMap<ComponentName, ContentProviderRecord> mGlobalByClass
            = new HashMap<ComponentName, ContentProviderRecord>();

    private final SparseArray<HashMap<String, ContentProviderRecord>> mProvidersByNamePerUser
            = new SparseArray<HashMap<String, ContentProviderRecord>>();
    private final SparseArray<HashMap<ComponentName, ContentProviderRecord>> mProvidersByClassPerUser
            = new SparseArray<HashMap<ComponentName, ContentProviderRecord>>();

    ContentProviderRecord getProviderByName(String name) {
        return getProviderByName(name, -1);
    }

    ContentProviderRecord getProviderByName(String name, int userId) {
        if (DBG) {
            Slog.i(TAG, "getProviderByName: " + name + " , callingUid = " + Binder.getCallingUid());
        }
        // Try to find it in the global list
        ContentProviderRecord record = mGlobalByName.get(name);
        if (record != null) {
            return record;
        }

        // Check the current user's list
        return getProvidersByName(userId).get(name);
    }

    ContentProviderRecord getProviderByClass(ComponentName name) {
        return getProviderByClass(name, -1);
    }

    ContentProviderRecord getProviderByClass(ComponentName name, int userId) {
        if (DBG) {
            Slog.i(TAG, "getProviderByClass: " + name + ", callingUid = " + Binder.getCallingUid());
        }
        // Try to find it in the global list
        ContentProviderRecord record = mGlobalByClass.get(name);
        if (record != null) {
            return record;
        }

        // Check the current user's list
        return getProvidersByClass(userId).get(name);
    }

    void putProviderByName(String name, ContentProviderRecord record) {
        if (DBG) {
            Slog.i(TAG, "putProviderByName: " + name + " , callingUid = " + Binder.getCallingUid()
                + ", record uid = " + record.appInfo.uid);
        }
        if (record.appInfo.uid < Process.FIRST_APPLICATION_UID) {
            mGlobalByName.put(name, record);
        } else {
            final int userId = UserId.getUserId(record.appInfo.uid);
            getProvidersByName(userId).put(name, record);
        }
    }

    void putProviderByClass(ComponentName name, ContentProviderRecord record) {
        if (DBG) {
            Slog.i(TAG, "putProviderByClass: " + name + " , callingUid = " + Binder.getCallingUid()
                + ", record uid = " + record.appInfo.uid);
        }
        if (record.appInfo.uid < Process.FIRST_APPLICATION_UID) {
            mGlobalByClass.put(name, record);
        } else {
            final int userId = UserId.getUserId(record.appInfo.uid);
            getProvidersByClass(userId).put(name, record);
        }
    }

    void removeProviderByName(String name, int optionalUserId) {
        if (mGlobalByName.containsKey(name)) {
            if (DBG)
                Slog.i(TAG, "Removing from globalByName name=" + name);
            mGlobalByName.remove(name);
        } else {
            // TODO: Verify this works, i.e., the caller happens to be from the correct user
            if (DBG)
                Slog.i(TAG,
                        "Removing from providersByName name=" + name + " user="
                        + (optionalUserId == -1 ? Binder.getOrigCallingUser() : optionalUserId));
            getProvidersByName(optionalUserId).remove(name);
        }
    }

    void removeProviderByClass(ComponentName name, int optionalUserId) {
        if (mGlobalByClass.containsKey(name)) {
            if (DBG)
                Slog.i(TAG, "Removing from globalByClass name=" + name);
            mGlobalByClass.remove(name);
        } else {
            if (DBG)
                Slog.i(TAG,
                        "Removing from providersByClass name=" + name + " user="
                        + (optionalUserId == -1 ? Binder.getOrigCallingUser() : optionalUserId));
            getProvidersByClass(optionalUserId).remove(name);
        }
    }

    private HashMap<String, ContentProviderRecord> getProvidersByName(int optionalUserId) {
        final int userId = optionalUserId >= 0
                ? optionalUserId : Binder.getOrigCallingUser();
        final HashMap<String, ContentProviderRecord> map = mProvidersByNamePerUser.get(userId);
        if (map == null) {
            HashMap<String, ContentProviderRecord> newMap = new HashMap<String, ContentProviderRecord>();
            mProvidersByNamePerUser.put(userId, newMap);
            return newMap;
        } else {
            return map;
        }
    }

    private HashMap<ComponentName, ContentProviderRecord> getProvidersByClass(int optionalUserId) {
        final int userId = optionalUserId >= 0
                ? optionalUserId : Binder.getOrigCallingUser();
        final HashMap<ComponentName, ContentProviderRecord> map = mProvidersByClassPerUser.get(userId);
        if (map == null) {
            HashMap<ComponentName, ContentProviderRecord> newMap = new HashMap<ComponentName, ContentProviderRecord>();
            mProvidersByClassPerUser.put(userId, newMap);
            return newMap;
        } else {
            return map;
        }
    }

    private void dumpProvidersByClassLocked(PrintWriter pw, boolean dumpAll,
            HashMap<ComponentName, ContentProviderRecord> map) {
        Iterator<Map.Entry<ComponentName, ContentProviderRecord>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ComponentName, ContentProviderRecord> e = it.next();
            ContentProviderRecord r = e.getValue();
            if (dumpAll) {
                pw.print("  * ");
                pw.println(r);
                r.dump(pw, "    ");
            } else {
                pw.print("  * ");
                pw.print(r.name.toShortString());
                /*
                if (r.app != null) {
                    pw.println(":");
                    pw.print("      ");
                    pw.println(r.app);
                } else {
                    pw.println();
                }
                */
            }
        }
    }

    private void dumpProvidersByNameLocked(PrintWriter pw,
            HashMap<String, ContentProviderRecord> map) {
        Iterator<Map.Entry<String, ContentProviderRecord>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ContentProviderRecord> e = it.next();
            ContentProviderRecord r = e.getValue();
            pw.print("  ");
            pw.print(e.getKey());
            pw.print(": ");
            pw.println(r);
        }
    }

    void dumpProvidersLocked(PrintWriter pw, boolean dumpAll) {
        boolean needSep = false;
        if (mGlobalByClass.size() > 0) {
            if (needSep)
                pw.println(" ");
            pw.println("  Published content providers (by class):");
            dumpProvidersByClassLocked(pw, dumpAll, mGlobalByClass);
            pw.println(" ");
        }

        if (mProvidersByClassPerUser.size() > 1) {
            for (int i = 0; i < mProvidersByClassPerUser.size(); i++) {
                HashMap<ComponentName, ContentProviderRecord> map = mProvidersByClassPerUser.valueAt(i);
                pw.println("  User " + mProvidersByClassPerUser.keyAt(i) + ":");
                dumpProvidersByClassLocked(pw, dumpAll, map);
                pw.println(" ");
            }
        } else if (mProvidersByClassPerUser.size() == 1) {
            HashMap<ComponentName, ContentProviderRecord> map = mProvidersByClassPerUser.valueAt(0);
            dumpProvidersByClassLocked(pw, dumpAll, map);
        }
        needSep = true;

        if (dumpAll) {
            pw.println(" ");
            pw.println("  Authority to provider mappings:");
            dumpProvidersByNameLocked(pw, mGlobalByName);

            for (int i = 0; i < mProvidersByNamePerUser.size(); i++) {
                if (i > 0) {
                    pw.println("  User " + mProvidersByNamePerUser.keyAt(i) + ":");
                }
                dumpProvidersByNameLocked(pw, mProvidersByNamePerUser.valueAt(i));
            }
        }
    }
}
