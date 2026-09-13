package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

public class AccompanyHealthSnapshotTest {
    @Test
    public void readySnapshotKeepsOnlyBoundedRoundedValues() throws Exception {
        final long now = 1_800_000_000_000L;
        final JSONObject value = new AccompanyHealthSnapshot(now)
                .putLong("stepsToday", 8L, 0L, 500_000L)
                .putDouble("stressAverageToday", 32.04d, 1.0d, 100.0d)
                .putTimestamp("latestStressAt", now - 60_000L, now)
                .putLong("badHeartRate", 500L, 20L, 300L)
                .putDouble("badNumber", Double.NaN, 0.0d, 100.0d)
                .putTimestamp("futureTimestamp", now + 10L * 60L * 1000L, now)
                .build();

        assertEquals(1, value.getInt("contractVersion"));
        assertEquals("ready", value.getString("state"));
        assertEquals(8L, value.getLong("stepsToday"));
        assertEquals(32.0d, value.getDouble("stressAverageToday"), 0.0d);
        assertEquals(now - 60_000L, value.getLong("latestStressAt"));
        assertFalse(value.has("badHeartRate"));
        assertFalse(value.has("badNumber"));
        assertFalse(value.has("futureTimestamp"));
    }

    @Test
    public void emptySnapshotIsExplicitlyNoData() throws Exception {
        final JSONObject value = new AccompanyHealthSnapshot(1234L).build();

        assertEquals("no_data", value.getString("state"));
        assertEquals(1234L, value.getLong("readAt"));
    }

    @Test
    public void errorSnapshotIsValueFree() throws Exception {
        final JSONObject value = AccompanyHealthSnapshot.error(2345L);

        assertEquals("error", value.getString("state"));
        assertEquals(2345L, value.getLong("readAt"));
        assertEquals(3, value.length());
    }
}
