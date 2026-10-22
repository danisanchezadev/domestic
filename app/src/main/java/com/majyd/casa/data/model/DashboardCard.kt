package com.majyd.casa.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class DashboardCard {
    SHOPPING,
    TASKS,
    CALENDAR,
    WEEKLY_MENU,
    PLANS,
    TRIPS,
    HOME,
    CONTACTS,
    ECONOMY,
    TRASH,
    SYNC,
    SETTINGS,
}
