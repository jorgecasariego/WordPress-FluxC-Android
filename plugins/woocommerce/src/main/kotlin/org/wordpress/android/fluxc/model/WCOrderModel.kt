package org.wordpress.android.fluxc.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.yarolegovich.wellsql.core.Identifiable
import com.yarolegovich.wellsql.core.annotation.Column
import com.yarolegovich.wellsql.core.annotation.PrimaryKey
import com.yarolegovich.wellsql.core.annotation.Table
import org.wordpress.android.fluxc.model.order.OrderAddress
import org.wordpress.android.fluxc.model.order.OrderAddress.AddressType
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

    @Column var lineItems: String = ""

    companion object {
        private val gson by lazy { Gson() }
    }

    class LineItem {
        val id: Long? = null
        val name: String? = null
        @SerializedName("product_id")
        val productId: Long? = null
        @SerializedName("variation_id")
        val variationId: Long? = null
        val quantity: Int? = null
        val total: Float? = null // Price x quantity
        @SerializedName("total_tax")
        val totalTax: Float? = null
        val sku: String? = null
        val price: Float? = null // The per-item price
    }

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

    /**
     * Returns the billing details wrapped in a [OrderAddress].
     */
    fun getBillingAddress() = OrderAddress(this, AddressType.BILLING)

    /**
     * Returns the shipping details wrapped in a [OrderAddress].
     */
    fun getShippingAddress() = OrderAddress(this, AddressType.SHIPPING)

    /**
     * Deserializes the JSON contained in [lineItems] into a list of [LineItem] objects.
     */
    fun getLineItemList(): List<LineItem> {
        val responseType = object : TypeToken<List<LineItem>>() {}.type
        return gson.fromJson(lineItems, responseType) as? List<LineItem> ?: emptyList()
    }
}
