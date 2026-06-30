package com.example.multibarcode.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.multibarcode.ui.customers.CustomerDetailScreen
import com.example.multibarcode.ui.customers.CustomersScreen
import com.example.multibarcode.ui.home.HomeScreen
import com.example.multibarcode.ui.order.NewOrderScreen
import com.example.multibarcode.ui.order.OrdersScreen
import com.example.multibarcode.ui.products.ProductsScreen
import com.example.multibarcode.ui.scan.LiveScanScreen

object Routes {
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val NEW_ORDER = "newOrder"
    const val CUSTOMERS = "customers"
    const val CUSTOMER_DETAIL = "customer"
    const val ORDERS = "orders"
    const val LIVE_SCAN = "liveScan"

    fun customerDetail(id: Long) = "$CUSTOMER_DETAIL/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onProducts = { nav.navigate(Routes.PRODUCTS) },
                onNewOrder = { nav.navigate(Routes.NEW_ORDER) },
                onCustomers = { nav.navigate(Routes.CUSTOMERS) },
                onOrders = { nav.navigate(Routes.ORDERS) },
                onLiveScan = { nav.navigate(Routes.LIVE_SCAN) },
            )
        }
        composable(Routes.PRODUCTS) {
            ProductsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.NEW_ORDER) {
            NewOrderScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Routes.CUSTOMERS) {
            CustomersScreen(
                onBack = { nav.popBackStack() },
                onOpenCustomer = { id -> nav.navigate(Routes.customerDetail(id)) },
            )
        }
        composable(
            route = "${Routes.CUSTOMER_DETAIL}/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("customerId") ?: 0L
            CustomerDetailScreen(customerId = id, onBack = { nav.popBackStack() })
        }
        composable(Routes.ORDERS) {
            OrdersScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.LIVE_SCAN) {
            LiveScanScreen(onBack = { nav.popBackStack() })
        }
    }
}
