package vn.edu.usth.instaclone.ui.common

import com.google.firebase.Timestamp

fun timeAgo(ts: Timestamp?): String {
    if (ts == null) return "just now"
    val s = (System.currentTimeMillis() - ts.toDate().time) / 1000
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        s < 60 -> "just now"
        s < 3600 -> plural(s / 60, "minute")
        s < 86400 -> plural(s / 3600, "hour")
        s < 604800 -> plural(s / 86400, "day")
        else -> plural(s / 604800, "week")
    }
}