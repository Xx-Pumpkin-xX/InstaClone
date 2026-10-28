package vn.edu.usth.instaclone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.SEARCH, "Search", Icons.Filled.Search),
    BottomTab(Routes.NEW_POST, "Post", Icons.Filled.AddCircle),
    BottomTab(Routes.CHAT_LIST, "Chat", Icons.AutoMirrored.Filled.Send),
    BottomTab(Routes.MY_PROFILE, "Profile", Icons.Filled.Person),
)

@Composable
fun BottomBar(navController: NavController, currentRoute: String?) {
    NavigationBar {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(Routes.HOME) {saveState = true}
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {Icon(tab.icon, contentDescription = tab.label)},
                label = { Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = LocalDensity.current.fontScale < 1.3f,
            )
        }
    }
}
