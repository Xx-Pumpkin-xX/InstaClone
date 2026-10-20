import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { ref, uploadBytes, deleteObject, getMetadata } from 'firebase/storage';
import { before, beforeEach, after, describe, it } from 'mocha';

const MAX_BYTES = 1024 * 1024;
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-instaclone',
    storage: {
      host: '127.0.0.1',
      port: 9199,
      rules: readFileSync(new URL('../../storage.rules', import.meta.url), 'utf8')
    }
  });
});
beforeEach(async () => { await env.clearStorage(); });
after(async () => { if (env) await env.cleanup(); });

function storageFor(uid = 'alice') {
  return uid === null
    ? env.unauthenticatedContext().storage()
    : env.authenticatedContext(uid).storage();
}
function upload(storage, path, contentType = 'image/jpeg', size = 100) {
  return uploadBytes(ref(storage, path), new Uint8Array(size), { contentType });
}

// Fake bytes deliberately test metadata authorization, not image decoding.
for (const kind of ['post', 'avatar']) {
  const pathFor = (uid, ext = 'jpg') => kind === 'post'
    ? `images/posts/${uid}/post_01.${ext}`
    : `images/avatars/${uid}.${ext}`;

  describe(`Storage security: ${kind}`, () => {
    for (const [ext, mime] of [['jpg', 'image/jpeg'], ['png', 'image/png']]) {
      it(`allows owner to upload ${ext}`, async () => {
        await assertSucceeds(upload(storageFor(), pathFor('alice', ext), mime));
      });
      it(`allows owner to replace ${ext}`, async () => {
        const storage = storageFor();
        await upload(storage, pathFor('alice', ext), mime);
        await assertSucceeds(upload(storage, pathFor('alice', ext), mime, 200));
      });
      it(`allows owner to delete ${ext}`, async () => {
        const storage = storageFor();
        await upload(storage, pathFor('alice', ext), mime);
        await assertSucceeds(deleteObject(ref(storage, pathFor('alice', ext))));
      });
      it(`rejects MIME mismatch for ${ext}`, async () => {
        const wrongMime = ext === 'jpg' ? 'image/png' : 'image/jpeg';
        await assertFails(upload(storageFor(), pathFor('alice', ext), wrongMime));
      });
    }

    it('allows exactly 1 MiB', async () => {
      await assertSucceeds(upload(storageFor(), pathFor('alice'), 'image/jpeg', MAX_BYTES));
    });
    it('rejects 1 MiB plus one byte', async () => {
      await assertFails(upload(storageFor(), pathFor('alice'), 'image/jpeg', MAX_BYTES + 1));
    });
    it('rejects an empty file', async () => {
      await assertFails(upload(storageFor(), pathFor('alice'), 'image/jpeg', 0));
    });
    it('rejects guest upload', async () => {
      await assertFails(upload(storageFor(null), pathFor('alice')));
    });
    it('rejects creating a file for another user', async () => {
      await assertFails(upload(storageFor('bob'), pathFor('alice')));
    });
    it('rejects overwriting another user file', async () => {
      await upload(storageFor(), pathFor('alice'));
      await assertFails(upload(storageFor('bob'), pathFor('alice')));
    });
    it('rejects deleting another user file', async () => {
      await upload(storageFor(), pathFor('alice'));
      await assertFails(deleteObject(ref(storageFor('bob'), pathFor('alice'))));
    });
    it('rejects guest delete', async () => {
      await upload(storageFor(), pathFor('alice'));
      await assertFails(deleteObject(ref(storageFor(null), pathFor('alice'))));
    });
    it('allows signed-in member to read metadata', async () => {
      await upload(storageFor(), pathFor('alice'));
      await assertSucceeds(getMetadata(ref(storageFor('bob'), pathFor('alice'))));
    });
    it('rejects guest read', async () => {
      await upload(storageFor(), pathFor('alice'));
      await assertFails(getMetadata(ref(storageFor(null), pathFor('alice'))));
    });
    for (const mime of ['text/plain', 'application/octet-stream', 'image/svg+xml', 'image/webp']) {
      it(`rejects disallowed MIME ${mime}`, async () => {
        await assertFails(upload(storageFor(), pathFor('alice'), mime));
      });
    }
    it('rejects invalid replacement, even by owner', async () => {
      const storage = storageFor();
      await upload(storage, pathFor('alice'));
      await assertFails(upload(storage, pathFor('alice'), 'text/plain'));
    });
  });
}

describe('Filename and path restrictions', () => {
  for (const filename of ['payload.cmd', 'payload.cmd.jpg', 'post.jpeg', 'post.JPG', 'post.svg', 'post name.jpg']) {
    it(`rejects post filename ${filename}`, async () => {
      await assertFails(upload(storageFor(), `images/posts/alice/${filename}`));
    });
  }
  for (const path of ['images/posts/alice/nested/post.jpg', 'other/alice/post.jpg', 'images/avatars/alice.jpg.png']) {
    it(`rejects unexpected path ${path}`, async () => {
      await assertFails(upload(storageFor(), path));
    });
  }
});

describe('Documented limitation: Storage Rules do not inspect bytes', () => {
  it('accepts non-image text when filename and declared MIME are valid', async () => {
    // This SUCCESS is intentional. Rules cannot prove that bytes form a JPEG.
    // Trusted decoding/re-encoding is needed for server-enforced content validation.
    const bytes = new TextEncoder().encode('This is plain text, not a JPEG image.');
    await assertSucceeds(uploadBytes(
      ref(storageFor(), 'images/posts/alice/metadata_only.jpg'),
      bytes,
      { contentType: 'image/jpeg' }
    ));
  });
});