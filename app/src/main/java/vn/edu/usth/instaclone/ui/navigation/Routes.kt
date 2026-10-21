package vn.edu.usth.instaclone.ui.navigation

object Routes {
    //before sign in
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"

    //5 bottom-bar tabs
    const val HOME = "home"
    const val SEARCH = "search"
    const val NEW_POST = "new_post"
    const val CHAT_LIST = "chat_list"
    const val MY_PROFILE = "my_profile"

    //open from other screen.
    const val POST_DETAIL = "post/{postId}"
    const val PROFILE = "profile/{userId}"
    const val EDIT_PROFILE = "edit_profile"
    const val CHAT = "chat/{userId}"

    //Helper fill in arg: Routes.postDetail("abc") --> "post/abc"
    fun postDetail(postId: String) = "post/$postId"
    fun profile(userId: String) = "profile/$userId"
    fun chat(userId: String) = "chat/$userId"
}