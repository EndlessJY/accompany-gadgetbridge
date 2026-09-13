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

final class AccompanyHealthSyncGate {
    enum Status {
        STARTED("started"),
        NOT_CONNECTED("not_connected"),
        UNSUPPORTED("unsupported"),
        BUSY("busy"),
        RATE_LIMITED("rate_limited");

        private final String wireValue;

        Status(final String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    private final long minimumIntervalMs;
    private long lastStartedAt = -1L;

    AccompanyHealthSyncGate(final long minimumIntervalMs) {
        if (minimumIntervalMs < 0L) throw new IllegalArgumentException("minimumIntervalMs must be non-negative");
        this.minimumIntervalMs = minimumIntervalMs;
    }

    synchronized Status request(
            final long elapsedRealtime,
            final boolean connected,
            final boolean supported,
            final boolean busy
    ) {
        if (!connected) return Status.NOT_CONNECTED;
        if (!supported) return Status.UNSUPPORTED;
        if (busy) return Status.BUSY;
        if (lastStartedAt >= 0L && elapsedRealtime >= lastStartedAt
                && elapsedRealtime - lastStartedAt < minimumIntervalMs) {
            return Status.RATE_LIMITED;
        }
        lastStartedAt = elapsedRealtime;
        return Status.STARTED;
    }

    synchronized void releaseFailedStart(final long elapsedRealtime) {
        if (lastStartedAt == elapsedRealtime) lastStartedAt = -1L;
    }
}
