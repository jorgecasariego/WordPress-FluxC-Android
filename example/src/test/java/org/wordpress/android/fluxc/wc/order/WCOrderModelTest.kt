package org.wordpress.android.fluxc.wc.order

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.model.OrderStatus
import org.wordpress.android.fluxc.model.WCOrderModel

@RunWith(RobolectricTestRunner::class)
class WCOrderModelTest {
    @Test
    fun testStatus() {
        val order = WCOrderModel()
        assertEquals("", order.status)
        assertEquals(OrderStatus.UNKNOWN, order.getStatus())

        order.setStatus(OrderStatus.PROCESSING)
        assertEquals(OrderStatus.PROCESSING, order.getStatus())
        assertEquals(OrderStatus.PROCESSING.toString(), order.status)
        order.status = "false-status"
        assertEquals(OrderStatus.UNKNOWN, order.getStatus())
    }
}
