package org.wordpress.android.fluxc.model

enum class OrderStatus(val value: String) {
    UNKNOWN(""),
    PENDING("pending"),
    ON_HOLD("on-hold"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    REFUNDED("refunded"),
    FAILED("failed");

    override fun toString(): String = value

    companion object {
        fun fromString(string: String?): OrderStatus {
            OrderStatus.values()
                    .filter { string.equals(it.toString(), ignoreCase = true) }
                    .firstOrNull { return it }
            return UNKNOWN
        }
    }
}
