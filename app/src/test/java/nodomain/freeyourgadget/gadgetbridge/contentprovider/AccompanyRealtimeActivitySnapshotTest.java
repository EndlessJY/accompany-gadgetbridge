package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import static org.junit.Assert.assertEquals;
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
}
