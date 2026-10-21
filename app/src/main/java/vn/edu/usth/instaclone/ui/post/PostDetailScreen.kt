package vn.edu.usth.instaclone.ui.post

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post
import androidx.compose.foundation.layout.WindowInsets
// SCREEN 1: connected to the ViewModel, loads data and handles errors
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit = {},
    viewModel: PostDetailViewModel = viewModel(),
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    // Load the post when the screen opens
    LaunchedEffect(postId) { viewModel.load(postId) }

    // Show a Toast on error, then clear it
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    PostDetailContent(
        post = post,
        comments = comments,
        onSend = { text -> viewModel.sendComment(text) },
        onBack = onBack,
    )
}

// SCREEN 2: UI only (the Preview uses this one)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailContent(
    post: Post?,
    comments: List<Comment>,
    onSend: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),   // <- thêm dòng này
        topBar = {
            TopAppBar(
                title = { Text("Post") },
                windowInsets = WindowInsets(0, 0, 0, 0),   // <- thêm dòng này
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {

                // Part 1: the post (it can be null)
                if (post != null) {
                    item {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(post.authorUsername, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(Color.LightGray)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(post.caption)
                        }
                    }
                }

                // Part 2: each comment
                items(comments, key = { it.id }) { comment ->
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(comment.authorUsername, fontWeight = FontWeight.Bold)
                        Text(comment.text)
                    }
                }
            }

            // Part 3: comment input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment...") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailPreview() {
    PostDetailContent(
        post = Post(id = "1", authorUsername = "demo", caption = "Hello"),
        comments = listOf(
            Comment(id = "c1", authorUsername = "an", text = "Yo"),
            Comment(id = "c2", authorUsername = "binh", text = "SUGOIII"),
        ),
        onSend = {},
    )
}