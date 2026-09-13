package nodomain.freeyourgadget.gadgetbridge.contentprovider;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccompanyHealthSyncGateTest {
    @Test
    public void startsOnlyForConnectedSupportedIdleDevice() {
        final AccompanyHealthSyncGate gate = new AccompanyHealthSyncGate(120_000L);

        assertEquals(AccompanyHealthSyncGate.Status.NOT_CONNECTED, gate.request(1_000L, false, true, false));
        assertEquals(AccompanyHealthSyncGate.Status.UNSUPPORTED, gate.request(1_000L, true, false, false));
        assertEquals(AccompanyHealthSyncGate.Status.BUSY, gate.request(1_000L, true, true, true));
        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(1_000L, true, true, false));
    }

    @Test
    public void rateLimitsRepeatedAcceptedRequestsButRecoversAfterInterval() {
        final AccompanyHealthSyncGate gate = new AccompanyHealthSyncGate(120_000L);

        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(1_000L, true, true, false));
        assertEquals(AccompanyHealthSyncGate.Status.RATE_LIMITED, gate.request(120_999L, true, true, false));
        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(121_000L, true, true, false));
    }

    @Test
    public void elapsedClockResetDoesNotLeaveSyncPermanentlyRateLimited() {
        final AccompanyHealthSyncGate gate = new AccompanyHealthSyncGate(120_000L);

        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(500_000L, true, true, false));
        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(10L, true, true, false));
    }

    @Test
    public void failedDispatchCanRetryImmediately() {
        final AccompanyHealthSyncGate gate = new AccompanyHealthSyncGate(120_000L);

        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(1_000L, true, true, false));
        gate.releaseFailedStart(1_000L);
        assertEquals(AccompanyHealthSyncGate.Status.STARTED, gate.request(1_001L, true, true, false));
    }
}
