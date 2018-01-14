package org.wordpress.android.fluxc.model

import com.yarolegovich.wellsql.core.Identifiable
import com.yarolegovich.wellsql.core.annotation.Column
import com.yarolegovich.wellsql.core.annotation.PrimaryKey
import com.yarolegovich.wellsql.core.annotation.Table
import org.wordpress.android.fluxc.persistence.WellSqlConfig

@Table(addOn = WellSqlConfig.ADDON_WOOCOMMERCE)
data class WCOrderModel(@PrimaryKey @Column private var id: Int = 0) : Identifiable {
    @Column var localSiteId: Int = 0
    @Column var remoteOrderId: Long = 0
    @Column var status: String = OrderStatus.UNKNOWN.toString()
    @Column var currency: String = ""
    @Column var dateCreated: String = "" // ISO 8601-formatted date in UTC, e.g. 1955-11-05T14:15:00+0000
    @Column var total: Float = 0F
    @Column var billingFirstName: String = ""
    @Column var billingLastName: String = ""

    override fun getId(): Int = id

    override fun setId(id: Int) {
        this.id = id
    }

    fun getStatus(): OrderStatus {
        return OrderStatus.fromString(status)
    }

    fun setStatus(orderStatus: OrderStatus) {
        status = orderStatus.toString()
    }
}
