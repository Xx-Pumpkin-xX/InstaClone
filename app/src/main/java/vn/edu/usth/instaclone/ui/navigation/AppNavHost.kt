package vn.edu.usth.instaclone.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.edu.usth.instaclone.ui.common.PlaceholderScreen
import vn.edu.usth.instaclone.ui.home.HomeFeedScreen
import androidx.compose.runtime.remember
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.ui.auth.ForgotPasswordScreen
import vn.edu.usth.instaclone.ui.auth.LoginScreen
import vn.edu.usth.instaclone.ui.auth.RegisterScreen
import vn.edu.usth.instaclone.ui.chat.ChatScreen
import vn.edu.usth.instaclone.ui.chat.ConversationListScreen
import vn.edu.usth.instaclone.ui.profile.ProfileScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    //Screen showing
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any {it.route == currentRoute}
    val currentUserId by AppContainer.authRepository.currentUserId.collectAsState()
    val startRoute = remember {
        if (AppContainer.authRepository.currentUserId.value != null) Routes.HOME else Routes.LOGIN
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
                    onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                )
            }
            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(onBackToLogin = { navController.popBackStack() })
            }
            composable(Routes.REGISTER) {
                //S-02 Register, Dat . T-09
                RegisterScreen(
                    onRegistered = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBackToLogin = { navController.popBackStack() }
                )
            }
            composable(Routes.HOME) {
                    HomeFeedScreen(
                        onPostClick = { postId -> navController.navigate(Routes.postDetail(postId)) },
                        onAuthorClick = { uid -> navController.navigate(Routes.profile(uid)) },
                    )
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
                currentUserId?.let { myId ->
                    ProfileScreen(
                        userId = myId,
                        showBack = false,
                        onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                        onLogOut = {
                            AppContainer.authRepository.signOut()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(navController.graph.id) {inclusive = true}
                            }
                        },
                        onPostClick = { postId -> navController.navigate(Routes.postDetail(postId)) },
                    )
                }
            }
            //----------Opened from other screens----------//
            composable(Routes.POST_DETAIL) { entry ->
                val postId = entry.arguments?.getString("postId") ?: ""
                PlaceholderScreen("S-05 Post: $postId", owner = "Tram . T-20")
            }
            composable(Routes.PROFILE) { entry ->
                val userId = entry.arguments?.getString("userId") ?: ""
                ProfileScreen(
                    userId = userId,
                    showBack = true,
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onLogOut = {
                        AppContainer.authRepository.signOut()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) {inclusive = true}
                        }
                    },
                    onPostClick = { postId -> navController.navigate(Routes.postDetail(postId)) },
                    onMessage = { uid -> navController.navigate(Routes.chat(uid)) },
                )
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
