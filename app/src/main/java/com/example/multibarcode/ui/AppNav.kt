package com.example.multibarcode.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.multibarcode.ui.admin.AdminScreen
import com.example.multibarcode.ui.backup.BackupScreen
import com.example.multibarcode.ui.auth.AuthPhase
import com.example.multibarcode.ui.auth.AuthViewModel
import com.example.multibarcode.ui.auth.LoginScreen
import com.example.multibarcode.ui.customers.CustomerDetailScreen
import com.example.multibarcode.ui.customers.CustomersScreen
import com.example.multibarcode.ui.home.HomeScreen
import com.example.multibarcode.ui.order.NewOrderScreen
import com.example.multibarcode.ui.order.OrdersScreen
import com.example.multibarcode.ui.products.BulkAddScreen
import com.example.multibarcode.ui.products.ProductsScreen
import com.example.multibarcode.ui.sync.UploadScreen
import com.example.multibarcode.ui.scan.LiveScanScreen

object Routes {
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val NEW_ORDER = "newOrder"
    const val CUSTOMERS = "customers"
    const val CUSTOMER_DETAIL = "customer"
    const val ORDERS = "orders"
    const val LIVE_SCAN = "liveScan"
    const val BULK_ADD = "bulkAdd"
    const val ADMIN = "admin"
    const val UPLOAD = "upload"
    const val BACKUP = "backup"

    fun customerDetail(id: String) = "$CUSTOMER_DETAIL/$id"
}

@Composable
fun AppNav() {
    val authVm: AuthViewModel = viewModel()
    val ui by authVm.ui.collectAsStateWithLifecycle()

    if (ui.phase == AuthPhase.AUTHORIZED) {
        MainNav(onSignOut = { authVm.signOut() }, isAdmin = ui.isAdmin)
    } else {
        LoginScreen(vm = authVm)
    }
}

@Composable
private fun MainNav(onSignOut: () -> Unit, isAdmin: Boolean) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onProducts = { nav.navigate(Routes.PRODUCTS) },
                onNewOrder = { nav.navigate(Routes.NEW_ORDER) },
                onCustomers = { nav.navigate(Routes.CUSTOMERS) },
                onOrders = { nav.navigate(Routes.ORDERS) },
                onLiveScan = { nav.navigate(Routes.LIVE_SCAN) },
                onSignOut = onSignOut,
                isAdmin = isAdmin,
                onAdmin = { nav.navigate(Routes.ADMIN) },
                onUpload = { nav.navigate(Routes.UPLOAD) },
                onBackup = { nav.navigate(Routes.BACKUP) },
            )
        }
        composable(Routes.ADMIN) {
            AdminScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.UPLOAD) {
            UploadScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PRODUCTS) {
            ProductsScreen(
                onBack = { nav.popBackStack() },
                onBulkAdd = { nav.navigate(Routes.BULK_ADD) },
            )
        }
        composable(Routes.BULK_ADD) {
            BulkAddScreen(
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
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
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("customerId").orEmpty()
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
