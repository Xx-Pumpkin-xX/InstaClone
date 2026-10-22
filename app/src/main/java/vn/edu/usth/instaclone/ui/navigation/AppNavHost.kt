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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.ui.auth.ForgotPasswordScreen
import vn.edu.usth.instaclone.ui.auth.LoginScreen
import vn.edu.usth.instaclone.ui.auth.RegisterScreen
import vn.edu.usth.instaclone.ui.chat.ChatScreen
import vn.edu.usth.instaclone.ui.chat.ConversationListScreen
import vn.edu.usth.instaclone.ui.profile.EditProfileScreen
import vn.edu.usth.instaclone.ui.profile.ProfileScreen
import vn.edu.usth.instaclone.ui.post.PostDetailScreen
import vn.edu.usth.instaclone.ui.auth.LogoutDialog

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    //Screen showing
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any {it.route == currentRoute}
    val currentUserId by AppContainer.authRepository.currentUserId.collectAsState()
    val logOut: () -> Unit = {
        AppContainer.authRepository.signOut()
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) {inclusive = true}
        }
    }
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
                        onAuthorClick = {uid -> navController.navigate(Routes.profile(uid))},
                    )
            }
            composable(Routes.SEARCH) {
                PlaceholderScreen("S-08 Search", owner = "Trang . T-26")
            }
            composable(Routes.NEW_POST) {
                vn.edu.usth.instaclone.ui.post.NewPostScreen(
                    onNavigateBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.CHAT_LIST) {
                ConversationListScreen(
                    onOpenChat = { userId -> navController.navigate(Routes.chat(userId)) },
                )
            }
            composable(Routes.MY_PROFILE) {
                var showLogout by rememberSaveable { mutableStateOf(false) }
                currentUserId?.let { myId ->
                    ProfileScreen(
                        userId = myId,
                        showBack = false,
                        onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                        onLogOut = {showLogout = true},
                        onPostClick = {postId -> navController.navigate(Routes.postDetail(postId))},
                    )
                }
                if (showLogout) {
                    LogoutDialog(
                        onConfirm = {showLogout = false; logOut()},
                        onDismiss = {showLogout = false},
                    )
                }
            }
            //----------Opened from other screens----------//
            composable(Routes.POST_DETAIL) { entry ->
                val postId = entry.arguments?.getString("postId") ?: ""
                PostDetailScreen(
                    postId = postId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PROFILE) { entry ->
                val userId = entry.arguments?.getString("userId") ?: ""
                var showLogout by rememberSaveable{mutableStateOf(false)}
                ProfileScreen(
                    userId = userId,
                    showBack = true,
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onLogOut = {showLogout = true},
                    onPostClick = { postId -> navController.navigate(Routes.postDetail(postId)) },
                    onMessage = { uid -> navController.navigate(Routes.chat(uid)) },
                )
                if (showLogout) {
                    LogoutDialog(
                        onConfirm = {showLogout = false; logOut()},
                        onDismiss = {showLogout = false},
                    )
                }
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
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
