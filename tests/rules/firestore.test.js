import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from '@firebase/rules-unit-testing';

import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  writeBatch,
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