package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XiaomiActivityAcknowledgementPolicyTest {
    @Test
    public void acknowledgesOnlyDurablyHandledFilesWhenRemovalIsEnabled() {
        assertTrue(XiaomiActivityFileFetcher.shouldAcknowledge(false, true, true, false));
        assertTrue(XiaomiActivityFileFetcher.shouldAcknowledge(false, true, false, true));
        assertFalse(XiaomiActivityFileFetcher.shouldAcknowledge(false, false, false, false));
        assertFalse(XiaomiActivityFileFetcher.shouldAcknowledge(false, true, false, false));
        assertFalse(XiaomiActivityFileFetcher.shouldAcknowledge(true, true, true, false));
    }
}
