package org.mishnatyosef.pickup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class QuantitiesAndFingerprintTest {
    @Test public void quantitiesAreParsed() {
        assertEquals(2.5, Quantities.parse("2,5"), 0.0001);
        assertEquals(3, Quantities.parse("3"), 0.0001);
        assertEquals(0, Quantities.parse("abc"), 0.0001);
        assertEquals(0, Quantities.parse(null), 0.0001);
        assertEquals(0, Quantities.parse("-4"), 0.0001);
    }

    @Test public void quantitiesAreFormatted() {
        assertEquals("3", Quantities.format(3.0));
        assertEquals("2.5", Quantities.format(2.5));
        assertEquals("0.125", Quantities.format(0.125));
    }

    @Test public void fingerprintIgnoresOrderAndCategory() {
        List<ProductRow> a = new ArrayList<>(), b = new ArrayList<>();
        a.add(new ProductRow("1", "חלב", "u1", "2", "", "", "מוצרי חלב", 3));
        a.add(new ProductRow("2", "לחם", "u2", "1", ""));
        b.add(new ProductRow("2", "לחם", "u2", "1", ""));
        b.add(new ProductRow("1", "חלב", "u1", "2", ""));
        assertEquals(DataGuard.fingerprint(a), DataGuard.fingerprint(b));
    }

    @Test public void fingerprintChangesWithQuantity() {
        List<ProductRow> a = new ArrayList<>(), b = new ArrayList<>();
        a.add(new ProductRow("1", "חלב", "", "2", ""));
        b.add(new ProductRow("1", "חלב", "", "3", ""));
        assertNotEquals(DataGuard.fingerprint(a), DataGuard.fingerprint(b));
    }

    @Test public void tickCombinesKeyAndAmount() {
        assertEquals("1|2", DataGuard.tick(new ProductRow("1", "חלב", "", "2", "")));
    }
}
