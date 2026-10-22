import { readFileSync } from 'node:fs';
import { strict as assert } from 'node:assert';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp } from 'firebase/firestore';
import { before, beforeEach, after, describe, it } from 'mocha';
let env;
const stamp = Timestamp.fromMillis(1000);
const profile = name => ({ username:name, displayName:name, bio:'', avatarUrl:'', postCount:0, followerCount:0, followingCount:0, createdAt:stamp });
const post = (author='alice') => ({ authorId:author, authorUsername:author, authorAvatarUrl:'', imageUrl:'https://example.com/p.jpg', imagePath:`images/posts/${author}/p.jpg`, caption:'hello', likeCount:0, commentCount:0, createdAt:serverTimestamp() });
const dbFor = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
before(async () => { env = await initializeTestEnvironment({projectId:'demo-instaclone',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(new URL('../../firestore.rules',import.meta.url),'utf8')}}); });
after(async () => { if(env) await env.cleanup(); });
async function seed(entries) { await env.withSecurityRulesDisabled(async ctx => { for(const [path,data] of Object.entries(entries)) await setDoc(doc(ctx.firestore(),path),data); }); }
function createPost(db,id='new',data=post()) { const b=writeBatch(db); b.set(doc(db,`posts/${id}`),data); b.update(doc(db,'users/alice'),{postCount:1,counterMutation:{kind:'post',id}}); return b.commit(); }
function like(db,uid='bob',adding=true,delta=adding?1:0) { const b=writeBatch(db); const r=doc(db,`posts/p/likes/${uid}`); adding?b.set(r,{createdAt:serverTimestamp()}):b.delete(r); b.update(doc(db,'posts/p'),{likeCount:delta}); return b.commit(); }
function comment(db,id='c',adding=true,data={authorId:'bob',authorUsername:'bob',text:'hi',createdAt:serverTimestamp()},count=adding?1:0) { const b=writeBatch(db); const r=doc(db,`posts/p/comments/${id}`); adding?b.set(r,data):b.delete(r); b.update(doc(db,'posts/p'),{commentCount:count,commentMutation:{kind:'comment',id}}); return b.commit(); }
function follow(db,adding=true,omit='',delta=adding?1:0,target='bob') { const b=writeBatch(db); for(const path of [`users/alice/following/${target}`,`users/${target}/followers/alice`]) { if(path===omit)continue; adding?b.set(doc(db,path),{createdAt:serverTimestamp()}):b.delete(doc(db,path)); } if(omit!=='actor')b.update(doc(db,'users/alice'),{followingCount:delta,counterMutation:{kind:'follow',id:target}}); if(omit!=='target')b.update(doc(db,`users/${target}`),{followerCount:delta,counterMutation:{kind:'follow',id:'alice'}}); return b.commit(); }
function send(db,{mid='m',sender='alice',participants=['alice','bob'],text='hello',omit='',summaryText=text}={}) { const b=writeBatch(db); if(omit!=='message')b.set(doc(db,`conversations/alice_bob/messages/${mid}`),{senderId:sender,text,createdAt:serverTimestamp()}); if(omit!=='summary')b.set(doc(db,'conversations/alice_bob'),{participants,participantUsernames:{alice:'alice',bob:'bob'},lastMessageText:summaryText,lastMessageAt:serverTimestamp(),lastSenderId:sender,lastMessageId:mid}); return b.commit(); }
describe('T08 complete collections and atomic invariants',()=>{
 beforeEach(async()=>{await env.clearFirestore(); await seed({'users/alice':profile('alice'),'users/bob':profile('bob'),'users/eve':profile('eve'),'posts/p':{...post(),createdAt:stamp}});});
 describe('posts and user counters',()=>{
  it('allows atomic post creation',async()=>{await assertSucceeds(createPost(dbFor('alice')));});
  it('allows atomic owner delete',async()=>{const db=dbFor('alice');await createPost(db);const b=writeBatch(db);b.delete(doc(db,'posts/new'));b.update(doc(db,'users/alice'),{postCount:0,counterMutation:{kind:'post',id:'new'}});await assertSucceeds(b.commit());});
  it('rejects creation without user counter',async()=>{await assertFails(setDoc(doc(dbFor('alice'),'posts/new'),post()));});
  it('rejects standalone +1 and -1 counters',async()=>{for(const n of [1,-1])await assertFails(updateDoc(doc(dbFor('alice'),'users/alice'),{postCount:n,counterMutation:{kind:'post',id:'p'}}));});
  it('rejects forged author',async()=>{await assertFails(createPost(dbFor('bob')));});
  it('rejects another users image path',async()=>{await assertFails(createPost(dbFor('alice'),'new',{...post(),imagePath:'images/posts/bob/p.jpg'}));});
  it('rejects forged username',async()=>{await assertFails(createPost(dbFor('alice'),'new',{...post(),authorUsername:'bob'}));});
  it('rejects nonzero initial counters',async()=>{await assertFails(createPost(dbFor('alice'),'new',{...post(),likeCount:1}));});
  it('rejects caption over 2200',async()=>{await assertFails(createPost(dbFor('alice'),'new',{...post(),caption:'x'.repeat(2201)}));});
  it('rejects two posts for one +1 counter',async()=>{const db=dbFor('alice');const b=writeBatch(db);b.set(doc(db,'posts/a'),post());b.set(doc(db,'posts/b'),post());b.update(doc(db,'users/alice'),{postCount:1,counterMutation:{kind:'post',id:'a'}});await assertFails(b.commit());});
  it('rejects deleting another owners post',async()=>{await assertFails(deleteDoc(doc(dbFor('bob'),'posts/p')));});
  it('rejects owner deletion without counter',async()=>{await assertFails(deleteDoc(doc(dbFor('alice'),'posts/p')));});
  it('allows signed-in post list and denies guest',async()=>{await assertSucceeds(getDocs(collection(dbFor('bob'),'posts')));await assertFails(getDoc(doc(dbFor(null),'posts/p')));});
  it('allows avatar fanout only matching own profile',async()=>{await assertSucceeds(updateDoc(doc(dbFor('alice'),'posts/p'),{authorAvatarUrl:''}));await assertFails(updateDoc(doc(dbFor('alice'),'posts/p'),{authorAvatarUrl:'forged'}));});
 });
 describe('likes',()=>{
  it('allows atomic like then unlike',async()=>{const db=dbFor('bob');await assertSucceeds(like(db));await assertSucceeds(like(db,'bob',false));});
  it('rejects duplicate like count inflation',async()=>{const db=dbFor('bob');await like(db);await assertFails(like(db,'bob',true,2));});
  it('rejects like alone',async()=>{await assertFails(setDoc(doc(dbFor('bob'),'posts/p/likes/bob'),{createdAt:serverTimestamp()}));});
  it('rejects counter alone',async()=>{await assertFails(updateDoc(doc(dbFor('bob'),'posts/p'),{likeCount:1}));});
  it('rejects forged liker and +2',async()=>{await assertFails(like(dbFor('alice')));await assertFails(like(dbFor('bob'),'bob',true,2));});
  it('rejects unlike without existing like',async()=>{await assertFails(like(dbFor('bob'),'bob',false,-1));});
  it('rejects mixed caption and counter update',async()=>{const db=dbFor('bob');const b=writeBatch(db);b.set(doc(db,'posts/p/likes/bob'),{createdAt:serverTimestamp()});b.update(doc(db,'posts/p'),{likeCount:1,caption:'attack'});await assertFails(b.commit());});
  it('rejects guest like',async()=>{await assertFails(like(dbFor(null)));});
 });
 describe('comments',()=>{
  it('allows atomic own create and delete',async()=>{const db=dbFor('bob');await assertSucceeds(comment(db));await assertSucceeds(comment(db,'c',false));});
  it('rejects standalone comment',async()=>{await assertFails(setDoc(doc(dbFor('bob'),'posts/p/comments/c'),{authorId:'bob',authorUsername:'bob',text:'hi',createdAt:serverTimestamp()}));});
  it('rejects counter without comment',async()=>{await assertFails(updateDoc(doc(dbFor('bob'),'posts/p'),{commentCount:1,commentMutation:{kind:'comment',id:'absent'}}));});
  it('rejects forged author',async()=>{await assertFails(comment(dbFor('alice')));});
  for(const t of ['', '   ', 'x'.repeat(501)])it(`rejects invalid comment length ${t.length}`,async()=>{await assertFails(comment(dbFor('bob'),'c',true,{authorId:'bob',authorUsername:'bob',text:t,createdAt:serverTimestamp()}));});
  it('allows 500-character comment',async()=>{await assertSucceeds(comment(dbFor('bob'),'c',true,{authorId:'bob',authorUsername:'bob',text:'x'.repeat(500),createdAt:serverTimestamp()}));});
  it('rejects deletion and edits by other user',async()=>{await comment(dbFor('bob'));await assertFails(comment(dbFor('alice'),'c',false));await assertFails(updateDoc(doc(dbFor('alice'),'posts/p/comments/c'),{text:'hack'}));});
  it('rejects reusing witness for two comments',async()=>{const db=dbFor('bob');const b=writeBatch(db);for(const id of ['a','b'])b.set(doc(db,`posts/p/comments/${id}`),{authorId:'bob',authorUsername:'bob',text:'hi',createdAt:serverTimestamp()});b.update(doc(db,'posts/p'),{commentCount:1,commentMutation:{kind:'comment',id:'a'}});await assertFails(b.commit());});
 });
 describe('follow mirrors and counters',()=>{
  it('allows all four follow and unfollow writes',async()=>{const db=dbFor('alice');await assertSucceeds(follow(db));await assertSucceeds(follow(db,false));});
  for(const omit of ['users/bob/followers/alice','users/alice/following/bob','actor','target'])it(`rejects omitted ${omit}`,async()=>{await assertFails(follow(dbFor('alice'),true,omit));});
  it('rejects follow for another actor',async()=>{await assertFails(follow(dbFor('eve')));});
  it('rejects self follow',async()=>{await assertFails(follow(dbFor('alice'),true,'',1,'alice'));});
  it('rejects +2 and repeat follow',async()=>{const db=dbFor('alice');await assertFails(follow(db,true,'',2));await follow(db);await assertFails(follow(db,true,'',2));});
  it('rejects changing someone elses profile with follow',async()=>{const db=dbFor('alice');await follow(db);await assertFails(updateDoc(doc(db,'users/bob'),{bio:'hacked'}));});
  it('allows incoming follower list reads only when signed in',async()=>{await assertSucceeds(getDocs(collection(dbFor('bob'),'users/bob/followers')));await assertFails(getDocs(collection(dbFor(null),'users/bob/followers')));});
 });
 describe('private two-person chat',()=>{
  it('allows first message and reply in atomic batches',async()=>{await assertSucceeds(send(dbFor('alice')));await assertSucceeds(send(dbFor('bob'),{mid:'reply',sender:'bob'}));});
  it('allows both participants reads, denies outsider and guest',async()=>{await send(dbFor('alice'));for(const uid of ['alice','bob'])await assertSucceeds(getDoc(doc(dbFor(uid),'conversations/alice_bob/messages/m')));for(const uid of ['eve',null]){await assertFails(getDoc(doc(dbFor(uid),'conversations/alice_bob')));await assertFails(getDocs(collection(dbFor(uid),'conversations/alice_bob/messages')));}});
  it('requires participant filter on conversation list',async()=>{await send(dbFor('alice'));const db=dbFor('alice');await assertSucceeds(getDocs(query(collection(db,'conversations'),where('participants','array-contains','alice'))));await assertFails(getDocs(collection(db,'conversations')));});
  for(const omit of ['message','summary'])it(`rejects missing ${omit}`,async()=>{await assertFails(send(dbFor('alice'),{omit}));});
  it('rejects outsider sender',async()=>{await assertFails(send(dbFor('eve'),{sender:'eve'}));});
  it('rejects spoofed sender',async()=>{await assertFails(send(dbFor('alice'),{sender:'bob'}));});
  it('rejects three participants',async()=>{await assertFails(send(dbFor('alice'),{participants:['alice','bob','eve']}));});
  it('rejects changing participants',async()=>{await send(dbFor('alice'));await assertFails(send(dbFor('alice'),{mid:'new',participants:['alice','eve']}));});
  it('rejects mismatched summary',async()=>{await assertFails(send(dbFor('alice'),{summaryText:'forged'}));});
  for(const t of ['', '   ', 'x'.repeat(1001)])it(`rejects invalid message length ${t.length}`,async()=>{await assertFails(send(dbFor('alice'),{text:t}));});
  it('allows 1000-character message',async()=>{await assertSucceeds(send(dbFor('alice'),{text:'x'.repeat(1000)}));});
  it('rejects rewriting or deleting history',async()=>{await send(dbFor('alice'));const db=dbFor('alice');await assertFails(updateDoc(doc(db,'conversations/alice_bob/messages/m'),{text:'changed'}));await assertFails(deleteDoc(doc(db,'conversations/alice_bob/messages/m')));});
  it('denied write leaves no partial documents',async()=>{await assertFails(send(dbFor('alice'),{summaryText:'mismatch'}));await env.withSecurityRulesDisabled(async ctx=>{assert.equal((await getDoc(doc(ctx.firestore(),'conversations/alice_bob/messages/m'))).exists(),false);});});
 });
});

describe('T08 adversarial boundaries and cleanup',()=>{
 beforeEach(async()=>{await env.clearFirestore();await seed({'users/alice':profile('alice'),'users/bob':profile('bob'),'posts/p':{...post(),createdAt:stamp}});});
 it('rejects fake operation kind and immutable user changes',async()=>{const db=dbFor('alice');await assertFails(updateDoc(doc(db,'users/alice'),{postCount:1,counterMutation:{kind:'hack',id:'p'}}));await assertFails(updateDoc(doc(db,'users/alice'),{username:'other'}));await assertFails(updateDoc(doc(db,'users/alice'),{createdAt:serverTimestamp()}));});
 it('rejects too long profile fields and extra fields',async()=>{for(const patch of [{bio:'x'.repeat(151)},{displayName:'x'.repeat(31)},{role:'admin'}])await assertFails(updateDoc(doc(dbFor('alice'),'users/alice'),patch));});
 it('allows profile changes while preserving stored witness',async()=>{const db=dbFor('alice');await createPost(db);await assertSucceeds(updateDoc(doc(db,'users/alice'),{bio:'hello'}));});
 it('allows multiline comment and message',async()=>{await assertSucceeds(comment(dbFor('bob'),'c',true,{authorId:'bob',authorUsername:'bob',text:'hello\nworld',createdAt:serverTimestamp()}));await assertSucceeds(send(dbFor('alice'),{text:'hello\nworld'}));});
 it('rejects whitespace-only multiline comment',async()=>{await assertFails(comment(dbFor('bob'),'c',true,{authorId:'bob',authorUsername:'bob',text:' \n\t',createdAt:serverTimestamp()}));});
 it('rejects standalone decrement with unrelated existing comment',async()=>{await comment(dbFor('bob'));await assertFails(updateDoc(doc(dbFor('bob'),'posts/p'),{commentCount:0,commentMutation:{kind:'comment',id:'c'}}));});
 it('rejects adding comments or likes to nonexistent post',async()=>{await env.withSecurityRulesDisabled(ctx=>deleteDoc(doc(ctx.firestore(),'posts/p')));await assertFails(comment(dbFor('bob')));await assertFails(like(dbFor('bob')));});
 it('rejects reuse of already created message for summary',async()=>{const db=dbFor('alice');await send(db);await assertFails(send(db,{omit:'message'}));});
 it('rejects counter jumps on unfollow',async()=>{const db=dbFor('alice');await follow(db);await assertFails(follow(db,false,'',-1));});
 it('rejects guest follow, comment and message',async()=>{await assertFails(follow(dbFor(null)));await assertFails(comment(dbFor(null)));await assertFails(send(dbFor(null)));});
 it('rejects forged comment username and client timestamp',async()=>{for(const patch of [{authorUsername:'alice'},{createdAt:stamp}])await assertFails(comment(dbFor('bob'),'c',true,{authorId:'bob',authorUsername:'bob',text:'hi',createdAt:serverTimestamp(),...patch}));});
 it('rejects forged chat username and client timestamp',async()=>{const db=dbFor('alice');const b=writeBatch(db);b.set(doc(db,'conversations/alice_bob/messages/m'),{senderId:'alice',text:'hi',createdAt:stamp});b.set(doc(db,'conversations/alice_bob'),{participants:['alice','bob'],participantUsernames:{alice:'bob',bob:'alice'},lastMessageId:'m',lastMessageText:'hi',lastMessageAt:stamp,lastSenderId:'alice'});await assertFails(b.commit());});
 it('allows only owners to clean orphan comments and likes',async()=>{await seed({'posts/gone/comments/c':{authorId:'bob',authorUsername:'bob',text:'old',createdAt:stamp},'posts/gone/likes/bob':{createdAt:stamp}});await assertFails(deleteDoc(doc(dbFor('alice'),'posts/gone/comments/c')));await assertFails(deleteDoc(doc(dbFor('alice'),'posts/gone/likes/bob')));await assertSucceeds(deleteDoc(doc(dbFor('bob'),'posts/gone/comments/c')));await assertSucceeds(deleteDoc(doc(dbFor('bob'),'posts/gone/likes/bob')));});
 it('rejects arbitrary collections',async()=>{await assertFails(setDoc(doc(dbFor('alice'),'admin/settings'),{enabled:true}));});
});
