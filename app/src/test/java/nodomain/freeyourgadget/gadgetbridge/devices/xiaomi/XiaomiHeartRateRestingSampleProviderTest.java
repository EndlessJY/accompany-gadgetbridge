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
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiDailySummarySample;

public class XiaomiHeartRateRestingSampleProviderTest {
    @Test
    public void missingRestingHeartRateDoesNotCrashSnapshotReads() {
        final XiaomiDailySummarySample summary = new XiaomiDailySummarySample();
        summary.setTimestamp(1_700_000_000_000L);
        summary.setHrResting(null);

        final XiaomiHeartRateRestingSampleProvider.XiaomiHeartRateRestingSample sample =
                new XiaomiHeartRateRestingSampleProvider.XiaomiHeartRateRestingSample(summary);

        assertEquals(-1, sample.getHeartRate());
        assertEquals(summary.getTimestamp(), sample.getTimestamp());
    }
}
