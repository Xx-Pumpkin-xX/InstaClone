package vn.edu.usth.instaclone.data.repository

import vn.edu.usth.instaclone.data.model.Comment
import kotlin.random.Random

class FakeCommentRepository(
    private val postRepo: FakePostRepository,
) : CommentRepository {

    // Sổ ghi: postId -> danh sách bình luận của bài đó
    private val store = mutableMapOf<String, MutableList<Comment>>()

    // Đếm để mỗi bình luận mới có id khác nhau
    private var nextId = 1

    private val sampleNames = listOf(
        "An", "Nhat", "Ngan Anh", "Mai Anh", "Nhat Quang", "Quang",
        "Linh", "Huy", "Minh", "Khoa", "Vy", "Bao"
    )
    private val sampleTexts = listOf(
        "DAMNNN", "like!", "Tuat", "Nice", "SUGOIIIIIII", "dep qua",
        "Chỗ này ở đâu vậy?", "Đẹp quá!", "Chill ghê", "Wow", "Xịn đó",
        "Cho mình xin địa điểm với", "Ảnh đẹp thật sự", "Haha"
    )

    // Lấy danh sách của bài; lần đầu thì sinh bình luận mẫu đúng bằng commentCount
    private suspend fun listFor(postId: String): MutableList<Comment> {
        store[postId]?.let { return it }
        val count = (postRepo.getPost(postId)?.commentCount ?: 0L).toInt()
        val random = Random(postId.hashCode())   // cùng bài thì luôn ra cùng kết quả
        val list = MutableList(count) { i ->
            Comment(
                id = "seed-$postId-$i",
                authorId = "uid-seed-$i",
                authorUsername = sampleNames.random(random),
                text = sampleTexts.random(random),
            )
        }
        store[postId] = list
        return list
    }

    override suspend fun comments(postId: String): List<Comment> {
        return listFor(postId).toList()
    }

    override suspend fun addComment(postId: String, text: String) {
        val list = listFor(postId)
        list.add(
            Comment(
                id = "c${nextId++}",
                authorId = "demo-uid",
                authorUsername = "demo-user",
                text = text,
            )
        )
        postRepo.addToCommentCount(postId, 1)
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        val removed = listFor(postId).removeAll { it.id == commentId }
        if (removed) postRepo.addToCommentCount(postId, -1)
    }
}