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

import org.json.JSONObject;
import org.json.JSONException;

/** Builds the fixed, bounded JSON contract returned to Accompany. */
final class AccompanyHealthSnapshot {
    static final int CONTRACT_VERSION = 1;

    private final JSONObject values = new JSONObject();
    private final long readAt;
    private int metricCount;
    private long dataUpdatedAt;

    AccompanyHealthSnapshot(final long readAt) {
        this.readAt = readAt;
        put("contractVersion", CONTRACT_VERSION);
        put("readAt", readAt);
    }

    AccompanyHealthSnapshot putLong(final String key, final long value, final long min, final long max) {
        if (value >= min && value <= max) {
            put(key, value);
            metricCount++;
        }
        return this;
    }

    AccompanyHealthSnapshot putDouble(final String key, final double value, final double min, final double max) {
        if (Double.isFinite(value) && value >= min && value <= max) {
            put(key, Math.round(value * 10.0d) / 10.0d);
            metricCount++;
        }
        return this;
    }

    AccompanyHealthSnapshot putTimestamp(
            final String key,
            final long value,
            final long readAt,
            final String... measurementKeys
    ) {
        boolean hasMeasurement = false;
        for (final String measurementKey : measurementKeys) {
            if (values.has(measurementKey)) {
                hasMeasurement = true;
                break;
            }
        }
        if (hasMeasurement && value > 0L && value <= readAt + 5L * 60L * 1000L) {
            put(key, value);
            markDataUpdatedAt(value);
        }
        return this;
    }

    AccompanyHealthSnapshot markDataUpdatedAt(final long value) {
        if (value > 0L && value <= readAt + 5L * 60L * 1000L) {
            dataUpdatedAt = Math.max(dataUpdatedAt, value);
        }
        return this;
    }

    JSONObject build() {
        put("state", metricCount > 0 ? "ready" : "no_data");
        if (metricCount > 0 && dataUpdatedAt > 0L) {
            put("dataUpdatedAt", dataUpdatedAt);
        }
        return values;
    }

    static JSONObject error(final long readAt) {
        final AccompanyHealthSnapshot snapshot = new AccompanyHealthSnapshot(readAt);
        snapshot.put("state", "error");
        return snapshot.values;
    }

    private void put(final String key, final Object value) {
        try {
            values.put(key, value);
        } catch (final JSONException e) {
            throw new IllegalStateException("Unable to build health summary", e);
        }
    }
}
