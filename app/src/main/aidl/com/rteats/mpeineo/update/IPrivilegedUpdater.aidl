package com.rteats.mpeineo.update;

import android.os.ParcelFileDescriptor;
import com.rteats.mpeineo.update.IUpdateResult;

interface IPrivilegedUpdater {
    void install(in ParcelFileDescriptor apk, long size, String packageName,
                 boolean relaunch, IUpdateResult callback);
}
