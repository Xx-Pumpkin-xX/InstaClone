import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from '@firebase/rules-unit-testing';

import {
  doc,
  collection,
  getDoc,
  getDocs,
  query,
  where,
  orderBy,
  startAt,
  endAt,
  limit,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  runTransaction,
  serverTimestamp
} from 'firebase/firestore';

import {
  before,
  beforeEach,
  after,
  describe,
  it
} from 'mocha';

let env;

function newPost(uid, postId) {
  return {
    authorId: uid,
    authorUsername: uid,
    authorAvatarUrl: '',
    imageUrl: 'https://example.com/post.jpg',
    imagePath: `images/posts/${uid}/${postId}.jpg`,
    caption: 'Test post',
    likeCount: 0,
    commentCount: 0,
    createdAt: serverTimestamp()
  };
}

function newUser(username) {
  return {
    username,
    displayName: username,
    bio: '',
    avatarUrl: '',
    postCount: 0,
    followerCount: 0,
    followingCount: 0,
    createdAt: serverTimestamp()
  };
}

async function register(db, uid, username) {
  const batch = writeBatch(db);

  batch.set(doc(db, 'users', uid), newUser(username));
  batch.set(doc(db, 'usernames', username), { uid });

  return batch.commit();
}

async function createPost(db, uid, postId) {
  const batch = writeBatch(db);
  batch.set(doc(db, 'posts', postId), newPost(uid, postId));
  batch.update(doc(db, 'users', uid), {
    postCount: 1,
    counterMutation: { kind: 'post', id: postId }
  });
  return batch.commit();
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-instaclone',
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: readFileSync(
        new URL('../../firestore.rules', import.meta.url),
        'utf8'
      )
    }
  });
});

beforeEach(async () => {
  await env.clearFirestore();
});

after(async () => {
  if (env) await env.cleanup();
});

describe('Users and usernames', () => {
  it('allow creating profile and claim together', async () => {
    const db = env.authenticatedContext('alice').firestore();

    await assertSucceeds(register(db, 'alice', 'alice'));
  });

  it('deny creating profile without claim', async () => {
    const db = env.authenticatedContext('alice').firestore();

    await assertFails(
      setDoc(doc(db, 'users', 'alice'), newUser('alice'))
    );
  });

  it('deny creating profile for other', async () => {
    const db = env.authenticatedContext('alice').firestore();

    await assertFails(register(db, 'bob', 'bob'));
  });

  it('guest can not read profile', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');

    const guest = env.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(guest, 'users', 'alice')));
  });

  it('member can read other profile', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');

    const bob = env.authenticatedContext('bob').firestore();

    await assertSucceeds(getDoc(doc(bob, 'users', 'alice')));
  });

  it('member can search users by username prefix, guest cannot', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');

    const bob = env.authenticatedContext('bob').firestore();
    await register(bob, 'bob', 'bob');

    const search = db => query(
      collection(db, 'users'),
      orderBy('username'),
      startAt('bob'),
      endAt('bob\uf8ff'),
      limit(20)
    );

    const member = env.authenticatedContext('charlie').firestore();
    await assertSucceeds(getDocs(search(member)));

    const guest = env.unauthenticatedContext().firestore();
    await assertFails(getDocs(search(guest)));
  });

  it('owner can update bio', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');

    await assertSucceeds(
      updateDoc(doc(db, 'users', 'alice'), { bio: 'Hello' })
    );
  });

  it('rejects display names over 30 characters and bios over 150 characters', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');

    await assertFails(
      updateDoc(doc(db, 'users', 'alice'), { displayName: 'x'.repeat(31) })
    );
    await assertFails(
      updateDoc(doc(db, 'users', 'alice'), { bio: 'x'.repeat(151) })
    );
  });

  it('can not update other profile', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');

    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      updateDoc(doc(bob, 'users', 'alice'), { bio: 'Changed' })
    );
  });

  it('can not change counter', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');

    await assertFails(
      updateDoc(doc(db, 'users', 'alice'), {
        followerCount: 999
      })
    );
  });

  it('cant not claim already username', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');

    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(register(bob, 'bob', 'alice'));
  });
});

describe('Post creation and deletion', () => {
  it('allows creating a post with its matching atomic post-count increment', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');

    await assertSucceeds(createPost(db, 'alice', 'post-1'));
  });

  it('denies creating a post without the matching post-count increment', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');

    await assertFails(
      setDoc(doc(db, 'posts', 'post-1'), newPost('alice', 'post-1'))
    );
  });

  it('denies creating a post for another member', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');

    const batch = writeBatch(db);
    batch.set(doc(db, 'posts', 'post-1'), newPost('bob', 'post-1'));
    batch.update(doc(db, 'users', 'alice'), {
      postCount: 1,
      counterMutation: { kind: 'post', id: 'post-1' }
    });
    await assertFails(batch.commit());
  });

  it('allows deleting an own post with an atomic count decrement and cleanup marker', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');
    await createPost(db, 'alice', 'post-1');
    await assertSucceeds(getDoc(doc(db, 'postDeletionCleanup', 'not-created-yet')));

    const batch = writeBatch(db);
    batch.delete(doc(db, 'posts', 'post-1'));
    batch.update(doc(db, 'users', 'alice'), {
      postCount: 0,
      counterMutation: { kind: 'post', id: 'post-1' }
    });
    batch.set(doc(db, 'postDeletionCleanup', 'post-1'), {
      authorId: 'alice',
      imagePath: 'images/posts/alice/post-1.jpg'
    });
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDocs(query(
      collection(db, 'postDeletionCleanup'),
      where('authorId', '==', 'alice')
    )));
  });

  it('rejects post deletion without the cleanup marker and preserves the post', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await register(db, 'alice', 'alice');
    await createPost(db, 'alice', 'post-1');
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'posts', 'post-1', 'likes', 'bob'), {
        createdAt: serverTimestamp()
      });
      await setDoc(doc(context.firestore(), 'posts', 'post-1', 'comments', 'comment-1'), {
        authorId: 'bob',
        authorUsername: 'bob',
        text: 'Nice',
        createdAt: serverTimestamp()
      });
    });

    const batch = writeBatch(db);
    batch.delete(doc(db, 'posts', 'post-1'));
    batch.update(doc(db, 'users', 'alice'), {
      postCount: 0,
      counterMutation: { kind: 'post', id: 'post-1' }
    });
    await assertFails(batch.commit());
    assert.equal((await getDoc(doc(db, 'posts', 'post-1'))).exists(), true);
    assert.equal((await getDoc(doc(db, 'users', 'alice'))).data().postCount, 1);
    assert.equal((await getDoc(doc(db, 'posts', 'post-1', 'likes', 'bob'))).exists(), true);
    assert.equal((await getDoc(doc(db, 'posts', 'post-1', 'comments', 'comment-1'))).exists(), true);
  });

  it('denies deleting another member post', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');
    await createPost(alice, 'alice', 'post-1');

    const bob = env.authenticatedContext('bob').firestore();
    await assertFails(deleteDoc(doc(bob, 'posts', 'post-1')));
  });

  it('does not allow a post owner to remove another member interactions while the post exists', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');
    await createPost(alice, 'alice', 'post-1');
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'posts', 'post-1', 'likes', 'bob'), { createdAt: serverTimestamp() });
      await setDoc(doc(db, 'posts', 'post-1', 'comments', 'comment-1'), {
        authorId: 'bob',
        authorUsername: 'bob',
        text: 'Nice',
        createdAt: serverTimestamp()
      });
    });

    const likes = await getDocs(collection(alice, 'posts', 'post-1', 'likes'));
    const comments = await getDocs(collection(alice, 'posts', 'post-1', 'comments'));
    assert.equal(likes.size, 1);
    assert.equal(comments.size, 1);
    await assertFails(deleteDoc(doc(alice, 'posts', 'post-1', 'likes', 'bob')));
    await assertFails(deleteDoc(doc(alice, 'posts', 'post-1', 'comments', 'comment-1')));
    assert.equal((await getDoc(doc(alice, 'posts', 'post-1'))).data().likeCount, 0);
    assert.equal((await getDoc(doc(alice, 'posts', 'post-1'))).data().commentCount, 0);
    assert.equal((await getDoc(doc(alice, 'posts', 'post-1', 'likes', 'bob'))).exists(), true);
    assert.equal((await getDoc(doc(alice, 'posts', 'post-1', 'comments', 'comment-1'))).exists(), true);

    const deleteBatch = writeBatch(alice);
    deleteBatch.delete(doc(alice, 'posts', 'post-1'));
    deleteBatch.update(doc(alice, 'users', 'alice'), {
      postCount: 0,
      counterMutation: { kind: 'post', id: 'post-1' }
    });
    deleteBatch.set(doc(alice, 'postDeletionCleanup', 'post-1'), {
      authorId: 'alice',
      imagePath: 'images/posts/alice/post-1.jpg'
    });
    await assertSucceeds(deleteBatch.commit());
    await assertSucceeds(deleteDoc(doc(alice, 'posts', 'post-1', 'likes', 'bob')));
    await assertSucceeds(deleteDoc(doc(alice, 'posts', 'post-1', 'comments', 'comment-1')));
    await assertSucceeds(deleteDoc(doc(alice, 'postDeletionCleanup', 'post-1')));
  });

  it('does not allow a stranger to remove interactions through a cleanup marker', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');
    await createPost(alice, 'alice', 'post-1');
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'posts', 'post-1', 'likes', 'bob'), { createdAt: serverTimestamp() });
      await setDoc(doc(db, 'posts', 'post-1', 'comments', 'comment-1'), {
        authorId: 'bob',
        authorUsername: 'bob',
        text: 'Nice',
        createdAt: serverTimestamp()
      });
    });

    const deleteBatch = writeBatch(alice);
    deleteBatch.delete(doc(alice, 'posts', 'post-1'));
    deleteBatch.update(doc(alice, 'users', 'alice'), {
      postCount: 0,
      counterMutation: { kind: 'post', id: 'post-1' }
    });
    deleteBatch.set(doc(alice, 'postDeletionCleanup', 'post-1'), {
      authorId: 'alice',
      imagePath: 'images/posts/alice/post-1.jpg'
    });
    await assertSucceeds(deleteBatch.commit());

    const charlie = env.authenticatedContext('charlie').firestore();
    await assertFails(getDoc(doc(charlie, 'postDeletionCleanup', 'post-1')));
    await assertFails(deleteDoc(doc(charlie, 'posts', 'post-1', 'likes', 'bob')));
    await assertFails(deleteDoc(doc(charlie, 'posts', 'post-1', 'comments', 'comment-1')));
  });

  it('allows signed-in members to list likes but not delete another member like', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');
    await createPost(alice, 'alice', 'post-1');
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'posts', 'post-1', 'likes', 'bob'), {
        createdAt: serverTimestamp()
      });
    });

    const charlie = env.authenticatedContext('charlie').firestore();
    await assertSucceeds(getDocs(collection(charlie, 'posts', 'post-1', 'likes')));
    await assertFails(deleteDoc(doc(charlie, 'posts', 'post-1', 'likes', 'bob')));
  });
});

describe('Post author avatar updates', () => {
  async function seedPost() {
    const alice = env.authenticatedContext('alice').firestore();
    await register(alice, 'alice', 'alice');
    await updateDoc(doc(alice, 'users', 'alice'), { avatarUrl: 'https://example.com/new.jpg' });
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'posts', 'post-1'), {
        authorId: 'alice',
        authorUsername: 'alice',
        authorAvatarUrl: 'https://example.com/old.jpg',
        imageUrl: 'https://example.com/post.jpg',
        imagePath: 'images/posts/alice/post-1.jpg',
        caption: 'Original caption',
        likeCount: 0,
        commentCount: 0,
        createdAt: serverTimestamp()
      });
    });
  }

  it('allows the author to update only the copied avatar URL', async () => {
    await seedPost();
    const alice = env.authenticatedContext('alice').firestore();

    await assertSucceeds(
      updateDoc(doc(alice, 'posts', 'post-1'), {
        authorAvatarUrl: 'https://example.com/new.jpg'
      })
    );
  });

  it('denies another member from updating the copied avatar URL', async () => {
    await seedPost();
    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      updateDoc(doc(bob, 'posts', 'post-1'), {
        authorAvatarUrl: 'https://example.com/new.jpg'
      })
    );
  });

  it('denies changing a post field while updating the avatar URL', async () => {
    await seedPost();
    const alice = env.authenticatedContext('alice').firestore();

    await assertFails(
      updateDoc(doc(alice, 'posts', 'post-1'), {
        authorAvatarUrl: 'https://example.com/new.jpg',
        caption: 'Changed caption'
      })
    );
  });
});
describe('FRS registration username policy', () => {
  for (const username of ['phong.demo', 'phong_demo', 'a'.repeat(30)]) {
    it(`allows ${username} in an atomic profile and claim write`, async () => {
      const db = env.authenticatedContext('alice').firestore();
      await assertSucceeds(register(db, 'alice', username));
    });
  }
  for (const username of ['ab', 'a'.repeat(31), 'PhongDemo', 'a b']) {
    it(`rejects invalid username ${username}`, async () => {
      const db = env.authenticatedContext('alice').firestore();
      await assertFails(register(db, 'alice', username));
    });
  }
});

async function seedProfiles(...uids) {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const batch = writeBatch(db);
    for (const uid of uids) {
      batch.set(doc(db, 'users', uid), newUser(uid));
    }
    await batch.commit();
  });
}

async function readUser(uid) {
  let data;
  await env.withSecurityRulesDisabled(async (context) => {
    const snapshot = await getDoc(doc(context.firestore(), 'users', uid));
    data = snapshot.data();
  });
  return data;
}

async function follow(db, me, target) {
  await runTransaction(db, async (transaction) => {
    const meRef = doc(db, 'users', me);
    const targetRef = doc(db, 'users', target);
    const followingRef = doc(db, 'users', me, 'following', target);
    const followerRef = doc(db, 'users', target, 'followers', me);

    const meSnapshot = await transaction.get(meRef);
    const targetSnapshot = await transaction.get(targetRef);
    const followingSnapshot = await transaction.get(followingRef);

    if (!meSnapshot.exists() || !targetSnapshot.exists() || followingSnapshot.exists()) {
      return;
    }

    transaction.set(followingRef, { createdAt: serverTimestamp() });
    transaction.set(followerRef, { createdAt: serverTimestamp() });
    transaction.update(meRef, {
      followingCount: meSnapshot.data().followingCount + 1,
      counterMutation: { kind: 'follow', id: target }
    });
    transaction.update(targetRef, {
      followerCount: targetSnapshot.data().followerCount + 1,
      counterMutation: { kind: 'follow', id: me }
    });
  });
}

async function unfollow(db, me, target) {
  await runTransaction(db, async (transaction) => {
    const meRef = doc(db, 'users', me);
    const targetRef = doc(db, 'users', target);
    const followingRef = doc(db, 'users', me, 'following', target);
    const followerRef = doc(db, 'users', target, 'followers', me);

    const meSnapshot = await transaction.get(meRef);
    const targetSnapshot = await transaction.get(targetRef);
    const followingSnapshot = await transaction.get(followingRef);

    if (!meSnapshot.exists() || !followingSnapshot.exists()) return;

    transaction.delete(followingRef);
    transaction.delete(followerRef);
    transaction.update(meRef, {
      followingCount: meSnapshot.data().followingCount - 1,
      counterMutation: { kind: 'follow', id: target }
    });
    if (targetSnapshot.exists()) {
      transaction.update(targetRef, {
        followerCount: targetSnapshot.data().followerCount - 1,
        counterMutation: { kind: 'follow', id: me }
      });
    }
  });
}

describe('Follow and counters', () => {
  it('allows a member to follow another member atomically', async () => {
    await seedProfiles('alice', 'bob');
    const alice = env.authenticatedContext('alice').firestore();

    await assertSucceeds(follow(alice, 'alice', 'bob'));

    assert.equal((await readUser('alice')).followingCount, 1);
    assert.equal((await readUser('alice')).followerCount, 0);
    assert.equal((await readUser('bob')).followerCount, 1);
    assert.equal((await readUser('bob')).followingCount, 0);

    await assertSucceeds(getDoc(doc(alice, 'users', 'alice', 'following', 'bob')));
    await assertSucceeds(getDoc(doc(alice, 'users', 'bob', 'followers', 'alice')));
  });

  it('allows a member to unfollow another member atomically', async () => {
    await seedProfiles('alice', 'bob');
    const alice = env.authenticatedContext('alice').firestore();
    await follow(alice, 'alice', 'bob');

    await assertSucceeds(unfollow(alice, 'alice', 'bob'));

    assert.equal((await readUser('alice')).followingCount, 0);
    assert.equal((await readUser('bob')).followerCount, 0);

    const following = await getDoc(doc(alice, 'users', 'alice', 'following', 'bob'));
    const follower = await getDoc(doc(alice, 'users', 'bob', 'followers', 'alice'));
    assert.equal(following.exists(), false);
    assert.equal(follower.exists(), false);
  });

  it('rejects following yourself', async () => {
    await seedProfiles('alice');
    const alice = env.authenticatedContext('alice').firestore();

    await assertFails(follow(alice, 'alice', 'alice'));
  });

  it('rejects writing under another member following collection', async () => {
    await seedProfiles('alice', 'bob');
    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      setDoc(doc(bob, 'users', 'alice', 'following', 'bob'), {
        createdAt: serverTimestamp()
      })
    );
  });

  it('rejects a follower document created for someone else', async () => {
    await seedProfiles('alice', 'bob');
    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      setDoc(doc(bob, 'users', 'alice', 'followers', 'charlie'), {
        createdAt: serverTimestamp()
      })
    );
  });

  it('rejects following a member that does not exist', async () => {
    await seedProfiles('alice');
    const alice = env.authenticatedContext('alice').firestore();

    await assertFails(
      setDoc(doc(alice, 'users', 'alice', 'following', 'ghost'), {
        createdAt: serverTimestamp()
      })
    );
  });

  it('rejects a follower count change of more than one', async () => {
    await seedProfiles('alice', 'bob');
    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      updateDoc(doc(bob, 'users', 'alice'), { followerCount: 2 })
    );
  });

  it('rejects a follower count change without the follower document', async () => {
    await seedProfiles('alice', 'bob');
    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      updateDoc(doc(bob, 'users', 'alice'), { followerCount: 1 })
    );
  });

  it('rejects another member changing a following count', async () => {
    await seedProfiles('alice', 'bob');
    const bob = env.authenticatedContext('bob').firestore();

    await assertFails(
      updateDoc(doc(bob, 'users', 'alice'), { followingCount: 1 })
    );
  });

  it('rejects a following count change of more than one', async () => {
    await seedProfiles('alice');
    const alice = env.authenticatedContext('alice').firestore();

    await assertFails(
      updateDoc(doc(alice, 'users', 'alice'), { followingCount: 2 })
    );
  });

  it('rejects a duplicate follow write', async () => {
    await seedProfiles('alice', 'bob');
    const alice = env.authenticatedContext('alice').firestore();
    await follow(alice, 'alice', 'bob');

    await assertFails(
      setDoc(doc(alice, 'users', 'alice', 'following', 'bob'), {
        createdAt: serverTimestamp()
      })
    );
  });

  it('rejects deleting only the follower document', async () => {
    await seedProfiles('alice', 'bob');
    const alice = env.authenticatedContext('alice').firestore();
    await follow(alice, 'alice', 'bob');

    await assertFails(
      deleteDoc(doc(alice, 'users', 'bob', 'followers', 'alice'))
    );
  });

  it('denies guests reading or writing follow documents', async () => {
    await seedProfiles('alice', 'bob');
    await follow(env.authenticatedContext('alice').firestore(), 'alice', 'bob');
    const guest = env.unauthenticatedContext().firestore();

    await assertFails(
      getDoc(doc(guest, 'users', 'alice', 'following', 'bob'))
    );
    await assertFails(
      setDoc(doc(guest, 'users', 'alice', 'following', 'bob'), {
        createdAt: serverTimestamp()
      })
    );
  });

  it('allows signed-in members to read another member following collection', async () => {
    await seedProfiles('alice', 'bob');
    await follow(env.authenticatedContext('alice').firestore(), 'alice', 'bob');
    const bob = env.authenticatedContext('bob').firestore();

    await assertSucceeds(
      getDoc(doc(bob, 'users', 'alice', 'following', 'bob'))
    );
  });

  it('lets the target and the follower read a follower document', async () => {
    await seedProfiles('alice', 'bob');
    await follow(env.authenticatedContext('alice').firestore(), 'alice', 'bob');

    const alice = env.authenticatedContext('alice').firestore();
    const bob = env.authenticatedContext('bob').firestore();

    await assertSucceeds(
      getDoc(doc(alice, 'users', 'bob', 'followers', 'alice'))
    );
    await assertSucceeds(
      getDoc(doc(bob, 'users', 'bob', 'followers', 'alice'))
    );
  });
});
describe('Cleanup marker reuse regression', () => {
  const id = 'reuse-test';
  async function deletedPostWithMarker() {
    const alice = env.authenticatedContext('alice').firestore();
    const bob = env.authenticatedContext('bob').firestore();
    await register(alice, 'alice', 'alice');
    await register(bob, 'bob', 'bob');
    await createPost(alice, 'alice', id);
    const batch = writeBatch(alice);
    batch.delete(doc(alice, 'posts', id));
    batch.update(doc(alice, 'users', 'alice'), {
      postCount: 0, counterMutation: { kind: 'post', id }
    });
    batch.set(doc(alice, 'postDeletionCleanup', id), {
      authorId: 'alice', imagePath: `images/posts/alice/${id}.jpg`
    });
    await batch.commit();
    return { alice, bob };
  }

  it('blocks reusing a post ID while its cleanup marker remains, for any author', async () => {
    const { alice, bob } = await deletedPostWithMarker();
    await assertFails(createPost(alice, 'alice', id));
    await assertFails(createPost(bob, 'bob', id));
    assert.equal((await getDoc(doc(bob, 'users', 'bob'))).data().postCount, 0);
    assert.equal((await getDoc(doc(bob, 'posts', id))).exists(), false);
  });

  it('blocks deleting the marker and recreating the post in the same batch', async () => {
    const { alice } = await deletedPostWithMarker();
    const batch = writeBatch(alice);
    batch.delete(doc(alice, 'postDeletionCleanup', id));
    batch.set(doc(alice, 'posts', id), newPost('alice', id));
    batch.update(doc(alice, 'users', 'alice'), {
      postCount: 1, counterMutation: { kind: 'post', id }
    });
    await assertFails(batch.commit());
    assert.equal((await getDoc(doc(alice, 'postDeletionCleanup', id))).exists(), true);
  });

  it('stale marker cannot authorize deleting interactions on an existing post', async () => {
    const { alice, bob } = await deletedPostWithMarker();
    // Simulate legacy data created before the post-ID reuse restriction.
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      await setDoc(doc(db, 'posts', id), {
        ...newPost('bob', id), likeCount: 1, commentCount: 1
      });
      await setDoc(doc(db, 'posts', id, 'likes', 'bob'), { createdAt: serverTimestamp() });
      await setDoc(doc(db, 'posts', id, 'comments', 'c'), {
        authorId: 'bob', authorUsername: 'bob', text: 'hi', createdAt: serverTimestamp()
      });
    });
    await assertFails(deleteDoc(doc(alice, 'posts', id, 'likes', 'bob')));
    await assertFails(deleteDoc(doc(alice, 'posts', id, 'comments', 'c')));
    const post = (await getDoc(doc(bob, 'posts', id))).data();
    assert.equal(post.likeCount, 1);
    assert.equal(post.commentCount, 1);
    assert.equal((await getDoc(doc(bob, 'posts', id, 'likes', 'bob'))).exists(), true);
    assert.equal((await getDoc(doc(bob, 'posts', id, 'comments', 'c'))).exists(), true);
  });

  it('allows post creation after cleanup marker removal has committed', async () => {
    const { alice, bob } = await deletedPostWithMarker();
    await assertSucceeds(deleteDoc(doc(alice, 'postDeletionCleanup', id)));
    await assertSucceeds(createPost(bob, 'bob', id));
  });
});
