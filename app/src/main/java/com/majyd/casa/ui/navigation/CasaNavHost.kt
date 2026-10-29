package com.majyd.casa.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.majyd.casa.data.model.AppPreferences
import com.majyd.casa.data.model.DishCollection
import com.majyd.casa.ui.home.HomeScreen
import com.majyd.casa.ui.module.ModulePlaceholderScreen
import com.majyd.casa.ui.search.SearchScreen
import com.majyd.casa.ui.shopping.ShoppingScreen
import com.majyd.casa.ui.settings.SettingsScreen
import com.majyd.casa.ui.sync.SyncScreen
import com.majyd.casa.ui.tasks.TasksScreen
import com.majyd.casa.ui.calendar.CalendarScreen
import com.majyd.casa.ui.weeklymenu.WeeklyMenuScreen
import com.majyd.casa.ui.dishes.DishesScreen
import com.majyd.casa.ui.contacts.ContactsScreen

@Composable
fun CasaNavHost(
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(
                preferences = preferences,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(AppDestination.Shopping.route) {
            ShoppingScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestination.Tasks.route) {
            TasksScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestination.Calendar.route) {
            CalendarScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestination.WeeklyMenu.route) {
            WeeklyMenuScreen(
                onBack = { navController.popBackStack() },
                onOpenDishes = { navController.navigate(AppDestination.Dishes.route) },
            )
        }
        composable(AppDestination.Dishes.route) {
            DishesScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.RecipeBook.route) {
            DishesScreen(onBack = { navController.popBackStack() }, collection = DishCollection.RECIPE_BOOK)
        }
        composable(AppDestination.Contacts.route) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Sync.route) {
            SyncScreen(
                preferences = preferences,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                preferences = preferences,
                onBack = { navController.popBackStack() },
                onOpenSync = { navController.navigate(AppDestination.Sync.route) },
            )
        }
        composable(AppDestination.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
            )
        }
        val implementedDestinations = setOf(
            AppDestination.Shopping,
            AppDestination.Tasks,
            AppDestination.Calendar,
            AppDestination.WeeklyMenu,
            AppDestination.Dishes,
            AppDestination.RecipeBook,
            AppDestination.Contacts,
            AppDestination.Sync,
            AppDestination.Settings,
            AppDestination.Search,
        )
        moduleDestinations.filterNot { it in implementedDestinations }.forEach { destination ->
            composable(destination.route) {
                ModulePlaceholderScreen(
                    destination = destination,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
