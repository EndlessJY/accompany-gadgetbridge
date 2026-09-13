package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccompanyHealthSyncScheduleTest {
    @Test
    public void successfulSyncUsesFiveMinuteFreshnessCadence() {
        assertEquals(5L * 60L * 1000L, AccompanyHealthSyncSchedule.nextDelayMs("started"));
    }

    @Test
    public void temporaryStatesRetryWithoutWaitingForTheFullCadence() {
        assertEquals(2L * 60L * 1000L, AccompanyHealthSyncSchedule.nextDelayMs("rate_limited"));
        assertEquals(60_000L, AccompanyHealthSyncSchedule.nextDelayMs("busy"));
        assertEquals(60_000L, AccompanyHealthSyncSchedule.nextDelayMs("not_connected"));
        assertEquals(60_000L, AccompanyHealthSyncSchedule.nextDelayMs(null));
    }

    @Test
    public void unsupportedDevicesUseAQuietFallbackCadence() {
        assertEquals(15L * 60L * 1000L, AccompanyHealthSyncSchedule.nextDelayMs("unsupported"));
    }
}
