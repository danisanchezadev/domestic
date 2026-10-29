package com.majyd.casa.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val homeName: String = "Casa",
    val personOneName: String = "Persona 1",
    val personTwoName: String = "Persona 2",
    val domesticWifiSsid: String = "",
    val setupCompleted: Boolean = false,
    val darkMode: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.GREEN,
    val lastSyncAt: Long? = null,
    val lastManualSyncAt: Long? = null,
    val lastManualResult: String? = null,
    val lastManualMessage: String? = null,
    val lastManualRemoteDevice: String? = null,
    val lastManualSent: Int = 0,
    val lastManualReceived: Int = 0,
    val lastManualAcknowledged: Int = 0,
    val lastManualApplied: Int = 0,
    val lastManualDurationMs: Long? = null,
    val lastManualError: String? = null,
    val dashboardCards: List<DashboardCard> = defaultDashboardCards(),
) {
    companion object {
        fun normalizeDashboardCards(cards: List<DashboardCard>): List<DashboardCard> {
            val normalized = cards.filterNot { it == DashboardCard.SYNC || it == DashboardCard.SETTINGS }.distinct()
            return if (DashboardCard.ECONOMY in normalized) normalized else normalized + DashboardCard.ECONOMY
        }

        fun defaultDashboardCards(): List<DashboardCard> = listOf(
            DashboardCard.SHOPPING,
            DashboardCard.TASKS,
            DashboardCard.CALENDAR,
            DashboardCard.WEEKLY_MENU,
            DashboardCard.RECIPE_BOOK,
            DashboardCard.PLANS,
            DashboardCard.TRIPS,
            DashboardCard.HOME,
            DashboardCard.CONTACTS,
            DashboardCard.ECONOMY,
            DashboardCard.TRASH,
        )
    }
}
