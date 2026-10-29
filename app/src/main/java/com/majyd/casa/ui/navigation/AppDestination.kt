package com.majyd.casa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dining
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.majyd.casa.data.model.DashboardCard

sealed class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val summary: String,
) {
    data object Home : AppDestination("home", "Inicio", Icons.Filled.Home, "Resumen doméstico")
    data object Shopping : AppDestination("shopping", "Compra", Icons.Filled.ShoppingCart, "Listas y productos")
    data object Tasks : AppDestination("tasks", "Tareas", Icons.Filled.TaskAlt, "Tareas domésticas")
    data object Calendar : AppDestination("calendar", "Agenda", Icons.Filled.CalendarMonth, "Citas y eventos")
    data object Dishes : AppDestination("dishes", "Mis platos", Icons.Filled.Dining, "Catalogo de platos")
    data object RecipeBook : AppDestination("recipe_book", "Recetario", Icons.Filled.MenuBook, "Catalogo de recetas")
    data object WeeklyMenu : AppDestination("menu", "Menú", Icons.Filled.RestaurantMenu, "Plan semanal")
    data object Plans : AppDestination("plans", "Planes", Icons.Filled.Dining, "Películas y experiencias")
    data object Trips : AppDestination("trips", "Viajes", Icons.Filled.Cabin, "Preparación de viajes")
    data object Appliances : AppDestination("appliances", "Hogar", Icons.Filled.Apartment, "Electrodomésticos y mantenimiento")
    data object Contacts : AppDestination("contacts", "Contactos", Icons.Filled.Inventory2, "Contactos importantes")
    data object Economy : AppDestination("economy", "Economia", Icons.Filled.AccountBalanceWallet, "Ingresos, gastos y ahorro")
    data object Trash : AppDestination("trash", "Papelera", Icons.Filled.LocalLaundryService, "Elementos eliminados")
    data object Sync : AppDestination("sync", "Sincronización", Icons.Filled.Notifications, "Estado local y pareado")
    data object Settings : AppDestination("settings", "Ajustes", Icons.Filled.Settings, "Preferencias y personas")
    data object Search : AppDestination("search", "Búsqueda", Icons.Filled.Search, "Búsqueda global")
}

val moduleDestinations = listOf(
    AppDestination.Shopping,
    AppDestination.Tasks,
    AppDestination.Calendar,
    AppDestination.WeeklyMenu,
    AppDestination.RecipeBook,
    AppDestination.Plans,
    AppDestination.Trips,
    AppDestination.Appliances,
    AppDestination.Contacts,
    AppDestination.Economy,
    AppDestination.Trash,
    AppDestination.Settings,
)

fun DashboardCard.toDestination(): AppDestination = when (this) {
    DashboardCard.SHOPPING -> AppDestination.Shopping
    DashboardCard.TASKS -> AppDestination.Tasks
    DashboardCard.CALENDAR -> AppDestination.Calendar
    DashboardCard.WEEKLY_MENU -> AppDestination.WeeklyMenu
    DashboardCard.RECIPE_BOOK -> AppDestination.RecipeBook
    DashboardCard.PLANS -> AppDestination.Plans
    DashboardCard.TRIPS -> AppDestination.Trips
    DashboardCard.HOME -> AppDestination.Home
    DashboardCard.CONTACTS -> AppDestination.Contacts
    DashboardCard.ECONOMY -> AppDestination.Economy
    DashboardCard.TRASH -> AppDestination.Trash
    DashboardCard.SYNC -> AppDestination.Sync
    DashboardCard.SETTINGS -> AppDestination.Settings
}
