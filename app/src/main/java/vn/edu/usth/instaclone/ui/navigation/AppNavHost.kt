package vn.edu.usth.instaclone.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.edu.usth.instaclone.ui.common.PlaceholderScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    //Screen showing
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any {it.route == currentRoute}

    Scaffold(
        bottomBar = {if (showBottomBar) BottomBar(navController, currentRoute)},
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                PlaceholderScreen("S-01 Login", owner = "Phong . T-10") {
                    Button(onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) {inclusive = true}
                        }
                    }) { Text("Skip login (temporary)") }
                    Button(onClick = {navController.navigate(Routes.REGISTER)}) { Text("Go to Register") }
                }
            }
            composable(Routes.REGISTER) {
                PlaceholderScreen("S-02 Register", owner = "Dat . T-09")
            }
            composable(Routes.HOME) {
                PlaceholderScreen("S-03 Home feed", owner = "Tram . T-18") {
                    Button(onClick = {navController.navigate(Routes.postDetail("demo-post"))}) {Text("Open a post")}
                    Button(onClick = {navController.navigate(Routes.profile("demo-user"))}) {Text("Open a profile")}
                }
            }
            composable(Routes.SEARCH) {
                PlaceholderScreen("S-08 Search", owner = "Trang . T-26")
            }
            composable(Routes.NEW_POST) {
                PlaceholderScreen("S-04 New post", owner = "Nam . T-13")
            }
            composable(Routes.CHAT_LIST) {
                PlaceholderScreen("S-09 Message", owner = "Thine . T-29")
            }
            composable(Routes.MY_PROFILE) {
                PlaceholderScreen("S-06 My profile", owner = "Trang . T-23") {
                    Button(onClick = {navController.navigate(Routes.EDIT_PROFILE)}) {Text("Edit profile")}
                }
            }
            //----------Opened from other screens----------//
            composable(Routes.POST_DETAIL) { entry ->
                val postId = entry.arguments?.getString("postId") ?: ""
                PlaceholderScreen("S-05 Post: $postId", owner = "Tram . T-20")
            }
            composable(Routes.PROFILE) { entry ->
                val userId = entry.arguments?.getString("userId") ?: ""
                PlaceholderScreen("S-06 Profile: $userId", owner = "Trang . T-23") {
                    Button(onClick = {navController.navigate(Routes.chat(userId))}) {Text("Message")}
                }
            }
            composable(Routes.EDIT_PROFILE) {
                PlaceholderScreen("S-07 Edit profile", owner = "Nam . T-15")
            }
            composable(Routes.POST_DETAIL) { entry ->
                val userId = entry.arguments?.getString("userId") ?: ""
                PlaceholderScreen("S-10 Chat with $userId", owner = "Thine . T-30")
            }
        }
    }
}