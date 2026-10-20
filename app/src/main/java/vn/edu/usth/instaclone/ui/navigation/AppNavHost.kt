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
import androidx.compose.runtime.remember
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.ui.auth.LoginScreen
import vn.edu.usth.instaclone.ui.chat.ChatScreen
import vn.edu.usth.instaclone.ui.chat.ConversationListScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    //Screen showing
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any {it.route == currentRoute}
    val startRoute = remember {
        if (AppContainer.authRepository.currentUserId.value != null) Routes.HOME else Routes.LOGIN
        // Routes.HOME (TẠM ĐỂ TEST CHAT VÌ CHƯA CÓ TẢI KHOẢN DEMO)
    }
    Scaffold(
        bottomBar = {if (showBottomBar) BottomBar(navController, currentRoute)},
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) {inclusive = true}
                        }
                    },
                    onCreateAccount = {navController.navigate(Routes.REGISTER)},
                )
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
                vn.edu.usth.instaclone.ui.post.NewPostScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CHAT_LIST) {
                ConversationListScreen(
                    onOpenChat = { userId -> navController.navigate(Routes.chat(userId)) },
                )
            }
            composable(Routes.MY_PROFILE) {
                PlaceholderScreen("S-06 My profile", owner = "Trang . T-23") {
                    Button(onClick = {navController.navigate(Routes.EDIT_PROFILE)}) {Text("Edit profile")}
                    Button(onClick = {
                        AppContainer.authRepository.signOut()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) {inclusive = true}
                        }
                    }) {Text("Log out (temporary")}
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
            composable(Routes.CHAT) { entry ->
                val userId = entry.arguments?.getString("userId") ?: ""
                ChatScreen(
                    otherUserId = userId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
