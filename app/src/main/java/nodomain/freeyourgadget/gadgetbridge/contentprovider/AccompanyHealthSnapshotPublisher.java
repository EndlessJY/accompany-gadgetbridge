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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Pushes a bounded summary after Gadgetbridge has finished writing new data. */
public final class AccompanyHealthSnapshotPublisher {
    public static final String ACTION_SNAPSHOT = "com.example.sender.action.GADGETBRIDGE_HEALTH_SNAPSHOT";
    public static final String EXTRA_SNAPSHOT_JSON = "snapshot_json";
    private static final String SENDER_PACKAGE = "com.example.sender";
    private static final String SENDER_RECEIVER =
            "com.example.sender.health.GadgetbridgeHealthSnapshotReceiver";
    private static final long DEFAULT_DEBOUNCE_SECONDS = 10L;
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "AccompanyHealthPublisher");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> pending;
    private static volatile String latestSnapshotJson;

    private AccompanyHealthSnapshotPublisher() {
    }

    public static void schedule(final Context context) {
        schedule(context, TimeUnit.SECONDS.toMillis(DEFAULT_DEBOUNCE_SECONDS));
    }

    public static synchronized void schedule(final Context context, final long delayMs) {
        if (pending != null) {
            pending.cancel(false);
        }
        final Context appContext = context.getApplicationContext();
        pending = EXECUTOR.schedule(
                () -> publish(appContext),
                Math.max(0L, delayMs),
                TimeUnit.MILLISECONDS
        );
    }

    private static void publish(final Context context) {
        final JSONObject snapshot = AccompanyHealthProvider.readSnapshot();
        final String state = snapshot.optString("state");
        if (!"ready".equals(state) && !"no_data".equals(state)) {
            return;
        }
        latestSnapshotJson = snapshot.toString();
        final Intent intent = new Intent(ACTION_SNAPSHOT)
                .setComponent(new ComponentName(SENDER_PACKAGE, SENDER_RECEIVER))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(EXTRA_SNAPSHOT_JSON, latestSnapshotJson);
        context.sendBroadcast(intent, AccompanyHealthProvider.READ_PERMISSION);
    }

    static String latestOrRead() {
        final String cached = latestSnapshotJson;
        if (cached != null) {
            return cached;
        }
        return AccompanyHealthProvider.readSnapshot().toString();
    }
}
