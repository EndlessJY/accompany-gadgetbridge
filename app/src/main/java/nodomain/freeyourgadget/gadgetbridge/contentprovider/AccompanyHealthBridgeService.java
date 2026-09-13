/*
 * Copyright (C) 2026 Accompany contributors
 *
 * This file is part of Gadgetbridge.
 *
 * Gadgetbridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.example.sender.healthbridge.IAccompanyHealthBridge;

/**
 * Explicit, read-only Binder bridge used when an OEM blocks cross-app
 * ContentProvider access while the caller is in the background.
 */
public class AccompanyHealthBridgeService extends Service {
    private final IAccompanyHealthBridge.Stub binder = new IAccompanyHealthBridge.Stub() {
        @Override
        public String readSummary() {
            enforceBridgePermission("Caller cannot read wearable health data");
            return AccompanyHealthSnapshotPublisher.readCurrent();
        }

        @Override
        public String requestSync() {
            enforceBridgePermission("Caller cannot request wearable health synchronization");
            return AccompanyHealthProvider.requestSyncStatus();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return binder;
    }

    private void enforceBridgePermission(final String message) {
        getApplicationContext().enforceCallingOrSelfPermission(
                AccompanyHealthProvider.READ_PERMISSION,
                message
        );
    }
}
