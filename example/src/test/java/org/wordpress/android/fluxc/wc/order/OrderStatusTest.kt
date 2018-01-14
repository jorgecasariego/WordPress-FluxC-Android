package org.wordpress.android.fluxc.wc.order

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.model.OrderStatus

@RunWith(RobolectricTestRunner::class)
class OrderStatusTest {
    @Test
    fun testStringConversion() {
        val status = OrderStatus.ON_HOLD
        assertEquals("on-hold", status.toString())

        assertEquals(OrderStatus.ON_HOLD, OrderStatus.fromString("on-hold"))
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.fromString(null))
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.fromString("not-a-real-status"))
    }
}
