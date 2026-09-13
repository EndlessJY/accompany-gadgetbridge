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

/** Bounded retry policy for Accompany's foreground-service health refresh loop. */
public final class AccompanyHealthSyncSchedule {
    private static final long RETRY_DELAY_MS = 60_000L;
    private static final long RATE_LIMIT_DELAY_MS = 2L * 60L * 1000L;
    private static final long FRESHNESS_DELAY_MS = 5L * 60L * 1000L;
    private static final long QUIET_DELAY_MS = 15L * 60L * 1000L;

    private AccompanyHealthSyncSchedule() {
    }

    public static long nextDelayMs(final String status) {
        if ("started".equals(status)) return FRESHNESS_DELAY_MS;
        if ("rate_limited".equals(status)) return RATE_LIMIT_DELAY_MS;
        if ("unsupported".equals(status)) return QUIET_DELAY_MS;
        return RETRY_DELAY_MS;
    }
}
