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
  await env.cleanup();
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