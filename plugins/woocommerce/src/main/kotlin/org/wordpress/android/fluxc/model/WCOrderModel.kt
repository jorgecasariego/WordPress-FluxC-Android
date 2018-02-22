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
    @Column var status: String = ""
    @Column var currency: String = ""
    @Column var dateCreated: String = "" // ISO 8601-formatted date in UTC, e.g. 1955-11-05T14:15:00Z
    @Column var total: Float = 0F
    @Column var billingFirstName: String = ""
    @Column var billingLastName: String = ""
    @Column var billingCompany: String = ""
    @Column var billingAddress1: String = ""
    @Column var billingAddress2: String = ""
    @Column var billingCity: String = ""
    @Column var billingState: String = ""
    @Column var billingPostcode: String = ""
    @Column var billingCountry: String = ""
    @Column var billingEmail: String = ""
    @Column var billingPhone: String = ""

    @Column var shippingFirstName: String = ""
    @Column var shippingLastName: String = ""
    @Column var shippingCompany: String = ""
    @Column var shippingAddress1: String = ""
    @Column var shippingAddress2: String = ""
    @Column var shippingCity: String = ""
    @Column var shippingState: String = ""
    @Column var shippingPostcode: String = ""
    @Column var shippingCountry: String = ""

    override fun getId(): Int = id

    override fun setId(id: Int) {
        this.id = id
    }

    /**
     * Returns true if there are shipping details defined for this order,
     * which are different from the billing details.
     *
     * If no separate shipping details are defined, the billing details should be used instead,
     * as the shippingX properties will be empty.
     */
    fun hasSeparateShippingDetails() = shippingCountry.isNotEmpty()
}
