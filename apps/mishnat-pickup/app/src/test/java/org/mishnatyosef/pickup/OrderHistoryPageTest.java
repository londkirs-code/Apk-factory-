package org.mishnatyosef.pickup;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrderHistoryPageTest {
    @Test public void saleNameComesFromSaleParentWeek() throws Exception {
        assertEquals("וישלח תשפ\"ז", OrderHistoryPage.saleName(TestData.firstHistoryOrder()));
    }

    @Test public void parentWeekWinsOverSaleWeek() throws Exception {
        JSONObject order = new JSONObject("{\"id\":1,\"sale\":{\"week\":\"שם משני\",\"parent\":{\"week\":\"נח\"}}}");
        assertEquals("נח", OrderHistoryPage.saleName(order));
    }

    @Test public void directParentWeekIsSupported() throws Exception {
        JSONObject order = new JSONObject("{\"id\":1,\"parent\":{\"week\":\"בראשית\"}}");
        assertEquals("בראשית", OrderHistoryPage.saleName(order));
    }

    @Test public void nestedOrderEnvelopeIsSupported() throws Exception {
        JSONObject wrapped = new JSONObject("{\"order\":{\"id\":5,\"sale\":{\"parent\":{\"week\":\"לך לך\"}}}}");
        assertEquals("לך לך", OrderHistoryPage.saleName(wrapped));
    }

    @Test public void objectValuesAreNeverUsedAsNames() throws Exception {
        JSONObject order = new JSONObject("{\"id\":77,\"name\":{\"he\":\"x\"}}");
        assertEquals("הזמנה 77", OrderHistoryPage.title(order));
    }

    @Test public void fallbackTitleUsesOrderId() throws Exception {
        assertEquals("הזמנה 42", OrderHistoryPage.title(new JSONObject("{\"id\":42}")));
    }

    @Test public void cancelledOrdersAreDetected() throws Exception {
        assertTrue(OrderHistoryPage.isCancelled(new JSONObject("{\"status\":\"cancelled\"}")));
        assertTrue(OrderHistoryPage.isCancelled(new JSONObject("{\"status\":\"בוטל\"}")));
        assertTrue(OrderHistoryPage.isCancelled(new JSONObject("{\"is_cancelled\":true}")));
        assertTrue(OrderHistoryPage.isCancelled(new JSONObject("{\"cancelled_at\":\"2026-01-01\"}")));
        assertFalse(OrderHistoryPage.isCancelled(new JSONObject("{\"status\":\"\"}")));
        assertFalse(OrderHistoryPage.isCancelled(TestData.firstHistoryOrder()));
    }

    @Test public void paginationIsParsed() throws Exception {
        OrderHistoryPage page = OrderHistoryPage.parse(TestData.text("orders_page.json"), 1);
        assertEquals(1, page.currentPage);
        assertEquals(3, page.lastPage);
        assertEquals(3, page.orders.length());
        assertTrue(page.hasMore());
    }
}
