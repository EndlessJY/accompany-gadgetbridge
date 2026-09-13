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

import androidx.annotation.Nullable;

import java.util.Calendar;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * Process-local, short-lived totals observed directly from a Xiaomi realtime response.
 *
 * <p>The band can expose its current day total before the corresponding activity file is
 * finalized. Keeping this snapshot separate from the database avoids double-counting when the
 * detailed file arrives later.</p>
 */
public final class AccompanyRealtimeActivitySnapshot {
    static final long MAX_AGE_MS = 20L * 60L * 1000L;
    private static final int MAX_STEPS = 500_000;
    private static final int MIN_HEART_RATE = 20;
    private static final int MAX_HEART_RATE = 300;

    private static volatile Value latest;

    private AccompanyRealtimeActivitySnapshot() {
    }

    public static void update(
            final GBDevice device,
            final int stepsToday,
            final int heartRate,
            final long observedAtMs
    ) {
        if (device == null) return;
        final Value value = validated(device.getAddress(), stepsToday, heartRate, observedAtMs);
        if (value != null) {
            latest = value;
        }
    }

    @Nullable
    public static Value currentFor(final GBDevice device, final long nowMs) {
        if (device == null) return null;
        return currentForAddress(latest, device.getAddress(), nowMs);
    }

    @Nullable
    static Value validated(
            final String deviceAddress,
            final int stepsToday,
            final int heartRate,
            final long observedAtMs
    ) {
        if (deviceAddress == null || deviceAddress.isBlank()
                || stepsToday < 0 || stepsToday > MAX_STEPS
                || observedAtMs <= 0L) {
            return null;
        }
        final int boundedHeartRate = heartRate >= MIN_HEART_RATE && heartRate <= MAX_HEART_RATE
                ? heartRate
                : -1;
        return new Value(deviceAddress, stepsToday, boundedHeartRate, observedAtMs);
    }

    @Nullable
    static Value currentForAddress(
            @Nullable final Value value,
            final String deviceAddress,
            final long nowMs
    ) {
        if (value == null || deviceAddress == null || !value.deviceAddress.equals(deviceAddress)) {
            return null;
        }
        final long ageMs = nowMs - value.observedAtMs;
        if (ageMs < 0L || ageMs > MAX_AGE_MS || !isSameLocalDay(value.observedAtMs, nowMs)) {
            return null;
        }
        return value;
    }

    public static int missingSteps(final long storedSteps, final int observedSteps) {
        if (storedSteps < 0L || observedSteps < 0 || storedSteps >= observedSteps) return 0;
        return (int) Math.min(Integer.MAX_VALUE, observedSteps - storedSteps);
    }

    private static boolean isSameLocalDay(final long firstMs, final long secondMs) {
        final Calendar first = Calendar.getInstance();
        first.setTimeInMillis(firstMs);
        final Calendar second = Calendar.getInstance();
        second.setTimeInMillis(secondMs);
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    public static final class Value {
        private final String deviceAddress;
        private final int stepsToday;
        private final int heartRate;
        private final long observedAtMs;

        private Value(
                final String deviceAddress,
                final int stepsToday,
                final int heartRate,
                final long observedAtMs
        ) {
            this.deviceAddress = deviceAddress;
            this.stepsToday = stepsToday;
            this.heartRate = heartRate;
            this.observedAtMs = observedAtMs;
        }

        public int getStepsToday() {
            return stepsToday;
        }

        public int getHeartRate() {
            return heartRate;
        }

        public long getObservedAtMs() {
            return observedAtMs;
        }
    }
}
