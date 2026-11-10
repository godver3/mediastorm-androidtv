package com.strmr.mobile.ui.recordings

import com.strmr.tv.live.SeriesRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingRulesModelTest {
    @Test fun summaryNamesChannelsAiringsAndState() {
        assertEquals("All channels · Enabled", RecordingRulesModel.summary(SeriesRule(id = "a", allChannels = true)))
        assertEquals("BBC One · New only · Paused",
                     RecordingRulesModel.summary(SeriesRule(id = "b", channelName = "BBC One", newOnly = true, enabled = false)))
        assertEquals("Selected channel · Enabled", RecordingRulesModel.summary(SeriesRule(id = "c")))
    }
}
