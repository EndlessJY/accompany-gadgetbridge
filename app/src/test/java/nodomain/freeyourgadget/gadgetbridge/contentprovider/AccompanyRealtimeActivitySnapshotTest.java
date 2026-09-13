package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;

public class AccompanyRealtimeActivitySnapshotTest {
    @Test
    public void acceptsFreshSameDeviceSnapshotAndBoundsHeartRate() {
        final long now = 1_800_000_000_000L;
        final AccompanyRealtimeActivitySnapshot.Value value =
                AccompanyRealtimeActivitySnapshot.validated("AA:BB", 123, 900, now - 1_000L);

        assertNotNull(value);
        assertNotNull(AccompanyRealtimeActivitySnapshot.currentForAddress(value, "AA:BB", now));
        assertEquals(-1, value.getHeartRate());
    }

    @Test
    public void rejectsExpiredWrongDeviceAndInvalidTotals() {
        final long now = 1_800_000_000_000L;
        final AccompanyRealtimeActivitySnapshot.Value value =
                AccompanyRealtimeActivitySnapshot.validated("AA:BB", 123, 80, now);

        assertNull(AccompanyRealtimeActivitySnapshot.currentForAddress(value, "CC:DD", now));
        assertNull(AccompanyRealtimeActivitySnapshot.currentForAddress(
                value,
                "AA:BB",
                now + AccompanyRealtimeActivitySnapshot.MAX_AGE_MS + 1L
        ));
        assertNull(AccompanyRealtimeActivitySnapshot.validated("AA:BB", -1, 80, now));
    }

    @Test
    public void contributesOnlyThePartMissingFromDetailedHistory() {
        assertEquals(75, AccompanyRealtimeActivitySnapshot.missingSteps(125L, 200));
        assertEquals(0, AccompanyRealtimeActivitySnapshot.missingSteps(200L, 200));
        assertEquals(0, AccompanyRealtimeActivitySnapshot.missingSteps(240L, 200));
    }

    @Test
    public void snapshotNeverCrossesTheLocalDayBoundary() {
        final Calendar observed = Calendar.getInstance();
        observed.clear();
        observed.set(2026, Calendar.SEPTEMBER, 13, 23, 59, 0);
        final AccompanyRealtimeActivitySnapshot.Value value =
                AccompanyRealtimeActivitySnapshot.validated(
                        "AA:BB",
                        123,
                        80,
                        observed.getTimeInMillis()
                );

        assertNull(AccompanyRealtimeActivitySnapshot.currentForAddress(
                value,
                "AA:BB",
                observed.getTimeInMillis() + 2L * 60L * 1000L
        ));
    }

    @Test
    public void freshRealtimeObservationOverridesStaleDatabaseActivityAndTimestamp() throws Exception {
        final long now = 1_800_000_000_000L;
        final long observedAt = now - 2_000L;
        final AccompanyRealtimeActivitySnapshot.Value value =
                AccompanyRealtimeActivitySnapshot.validated("AA:BB", 456, 82, observedAt);
        final AccompanyHealthSnapshot output = new AccompanyHealthSnapshot(now)
                .putLong("stepsToday", 89L, 0L, 500_000L)
                .putLong("latestHeartRateBpm", 61L, 20L, 300L)
                .putTimestamp("latestHeartRateAt", now - 90L * 60L * 1000L, now, "latestHeartRateBpm")
                .markDataUpdatedAt(now - 90L * 60L * 1000L);

        AccompanyRealtimeActivitySnapshot.applyToSnapshot(output, value, "AA:BB", now);
        final org.json.JSONObject snapshot = output.build();

        assertEquals(456L, snapshot.getLong("stepsToday"));
        assertEquals(82L, snapshot.getLong("latestHeartRateBpm"));
        assertEquals(observedAt, snapshot.getLong("latestHeartRateAt"));
        assertEquals(observedAt, snapshot.getLong("dataUpdatedAt"));
    }

    @Test
    public void fresherStepTotalDoesNotKeepDistanceFromOlderDetailedSamples() throws Exception {
        final long now = 1_800_000_000_000L;
        final AccompanyRealtimeActivitySnapshot.Value value =
                AccompanyRealtimeActivitySnapshot.validated("AA:BB", 456, 82, now - 2_000L);
        final AccompanyHealthSnapshot output = new AccompanyHealthSnapshot(now)
                .putLong("stepsToday", 89L, 0L, 500_000L)
                .putDouble("distanceMetersToday", 57.0d, 0.0d, 1_000_000.0d);

        AccompanyRealtimeActivitySnapshot.applyToSnapshot(output, value, "AA:BB", now);
        final org.json.JSONObject snapshot = output.build();

        assertEquals(456L, snapshot.getLong("stepsToday"));
        assertFalse(snapshot.has("distanceMetersToday"));
    }

    @Test
    public void expiredRealtimeObservationDoesNotMakeOldDatabaseDataLookFresh() throws Exception {
        final long now = 1_800_000_000_000L;
        final long storedAt = now - 90L * 60L * 1000L;
        final AccompanyRealtimeActivitySnapshot.Value value =
                AccompanyRealtimeActivitySnapshot.validated(
                        "AA:BB",
                        456,
                        82,
                        now - AccompanyRealtimeActivitySnapshot.MAX_AGE_MS - 1L
                );
        final AccompanyHealthSnapshot output = new AccompanyHealthSnapshot(now)
                .putLong("stepsToday", 89L, 0L, 500_000L)
                .markDataUpdatedAt(storedAt);

        AccompanyRealtimeActivitySnapshot.applyToSnapshot(output, value, "AA:BB", now);
        final org.json.JSONObject snapshot = output.build();

        assertEquals(89L, snapshot.getLong("stepsToday"));
        assertEquals(storedAt, snapshot.getLong("dataUpdatedAt"));
        assertFalse(snapshot.has("latestHeartRateAt"));
    }
}
