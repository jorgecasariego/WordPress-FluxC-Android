package org.wordpress.android.fluxc.store

import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.OrderStatus
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WCOrderStore @Inject constructor(dispatcher: Dispatcher) : Store(dispatcher) {
    override fun onRegister() {
        AppLog.d(T.API, "WCOrderStore onRegister")
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        // TODO
    }

    fun getNewOrders(): List<WCOrderModel> {
        val orderModel1 = WCOrderModel(1)
        orderModel1.localSiteId = 1
        orderModel1.remoteOrderId = 51
        orderModel1.currency = "USD"
        orderModel1.dateCreated = "2018-01-05T05:14:30+0000"
        orderModel1.total = 14.53F
        orderModel1.setStatus(OrderStatus.PROCESSING)
        orderModel1.billingFirstName = "John"
        orderModel1.billingLastName = "Peters"

        val orderModel2 = WCOrderModel(2)
        orderModel2.localSiteId = 1
        orderModel2.remoteOrderId = 63
        orderModel2.currency = "CAD"
        orderModel2.dateCreated = "2017-12-08T16:11:13+0000"
        orderModel2.total = 106.00F
        orderModel2.setStatus(OrderStatus.PENDING)
        orderModel2.billingFirstName = "Jane"
        orderModel2.billingLastName = "Masterson"

        return listOf(orderModel1, orderModel2)
    }
}
