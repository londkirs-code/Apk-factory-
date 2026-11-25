package org.mishnatyosef.pickup;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PickupInfoTest {
    private JSONObject info() throws Exception {
        return PickupInfo.extract(TestData.firstHistoryOrder(), null);
    }

    @Test public void paymentFieldsAreNeverStored() throws Exception {
        String stored = info().toString();
        assertFalse(stored.contains("SECRET"));
        assertFalse(stored.contains("tranzila"));
        assertFalse(stored.contains("tokef"));
        assertFalse(stored.contains("\"card\""));
    }

    @Test public void saleNameIsStored() throws Exception {
        assertEquals("וישלח תשפ\"ז", info().optString("sale_name"));
    }

    private String value(List<PickupInfo.Line> lines, String label) {
        for (PickupInfo.Line line : lines) if (!line.isSection() && label.equals(line.label)) return line.value;
        return null;
    }

    @Test public void pickupDateAndHoursAreDescribed() throws Exception {
        List<PickupInfo.Line> lines = PickupInfo.describe(info());
        assertEquals("יום שלישי, 24.11.2026", value(lines, "תאריך"));
        assertEquals("17:00–17:30", value(lines, "שעות"));
        assertEquals("מעלה חבר", value(lines, "נקודת איסוף"));
        assertNull("שעות 00:00 לא מוצגות", value(lines, "שעות לגברים"));
    }

    @Test public void hiddenPhoneIsNotOffered() throws Exception {
        assertEquals("", PickupInfo.phone(info()));
    }

    @Test public void noSaleMeansNoInfo() throws Exception {
        assertNull(PickupInfo.extract(new JSONObject("{\"id\":1}"), null));
    }
}
