import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, collection, getDoc, getDocs, query, orderBy, setDoc, writeBatch, increment, serverTimestamp, Timestamp } from 'firebase/firestore';
import { before, after, beforeEach, describe, it } from 'mocha';
let env;
const member = uid => env.authenticatedContext(uid).firestore();
const commentData = (uid, text = 'hello') => ({ authorId: uid, authorUsername: uid, text, createdAt: serverTimestamp() });
function add(db, id, { witness = id, uid = 'bob', text = 'hello' } = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, `posts/post/comments/${id}`), commentData(uid, text));
  batch.update(doc(db, 'posts/post'), {
    commentCount: increment(1),
    ...(witness === null ? {} : { commentMutation: { kind: 'comment', id: witness } })
  });
  return batch.commit();
}
function remove(db, id, witness = id) {
  const batch = writeBatch(db);
  batch.delete(doc(db, `posts/post/comments/${id}`));
  batch.update(doc(db, 'posts/post'), {
    commentCount: increment(-1),
    ...(witness === null ? {} : { commentMutation: { kind: 'comment', id: witness } })
  });
  return batch.commit();
}

describe('Firebase comment atomic write protocol (FR-11 to FR-13)', () => {
  before(async () => {
    env = await initializeTestEnvironment({ projectId: 'demo-instaclone', firestore: {
      host: '127.0.0.1', port: 8080,
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8')
    } });
  });
  after(async () => { if (env) await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      for (const uid of ['alice', 'bob']) await setDoc(doc(db, `users/${uid}`), {
        username: uid, displayName: uid, bio: '', avatarUrl: '', postCount: 0,
        followerCount: 0, followingCount: 0, createdAt: Timestamp.fromMillis(1)
      });
      await setDoc(doc(db, 'posts/post'), {
        authorId: 'alice', authorUsername: 'alice', authorAvatarUrl: '',
        imageUrl: 'https://example.com/photo.jpg', imagePath: 'images/posts/alice/photo.jpg',
        caption: '', likeCount: 0, commentCount: 0, createdAt: Timestamp.fromMillis(1)
      });
    });
  });
  it('creates a comment and increments its count atomically with its own marker', async () => {
    const db = member('bob');
    await assertSucceeds(add(db, 'first'));
    assert.equal((await getDoc(doc(db, 'posts/post'))).data().commentCount, 1);
    assert.equal((await getDoc(doc(db, 'posts/post/comments/first'))).data().authorId, 'bob');
  });
  it('deletes an older comment after a newer comment changed the stored marker', async () => {
    const db = member('bob');
    await add(db, 'first');
    await add(db, 'second');
    await assertSucceeds(remove(db, 'first'));
    const post = (await getDoc(doc(db, 'posts/post'))).data();
    assert.equal(post.commentCount, 1);
    assert.equal(post.commentMutation.id, 'first');
    assert.equal((await getDoc(doc(db, 'posts/post/comments/first'))).exists(), false);
    assert.equal((await getDoc(doc(db, 'posts/post/comments/second'))).exists(), true);
  });
  it('rejects missing add marker without leaving a partial comment or count', async () => {
    const db = member('bob');
    await assertFails(add(db, 'first', { witness: null }));
    assert.equal((await getDoc(doc(db, 'posts/post'))).data().commentCount, 0);
    assert.equal((await getDoc(doc(db, 'posts/post/comments/first'))).exists(), false);
  });
  it('rejects missing delete marker when it points at another comment', async () => {
    const db = member('bob');
    await add(db, 'first');
    await add(db, 'second');
    await assertFails(remove(db, 'first', null));
    assert.equal((await getDoc(doc(db, 'posts/post'))).data().commentCount, 2);
    assert.equal((await getDoc(doc(db, 'posts/post/comments/first'))).exists(), true);
  });
  it('rejects another authors deletion even with a matching marker', async () => {
    await add(member('bob'), 'first');
    await assertFails(remove(member('alice'), 'first'));
  });
  it('rejects a marker that names a different newly created comment', async () => {
    await assertFails(add(member('bob'), 'first', { witness: 'different' }));
  });
  it('rejects guest comment creation', async () => {
    await assertFails(add(env.unauthenticatedContext().firestore(), 'first'));
  });
  it('reads the complete list oldest first, with the newest comment last', async () => {
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      await setDoc(doc(db, 'posts/post/comments/new'), { ...commentData('bob'), createdAt: Timestamp.fromMillis(20) });
      await setDoc(doc(db, 'posts/post/comments/old'), { ...commentData('bob'), createdAt: Timestamp.fromMillis(10) });
    });
    const result = await getDocs(query(collection(member('bob'), 'posts/post/comments'), orderBy('createdAt', 'asc')));
    assert.deepEqual(result.docs.map(d => d.id), ['old', 'new']);
  });
});
