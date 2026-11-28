const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('/usr/local/lib/node_modules/@firebase/rules-unit-testing/dist/index.cjs.js');
const firebase = require('/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/compat/app');
require('/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/compat/firestore');

const PROJECT_ID = 'gen-lang-client-0692772699';
const FieldValue = firebase.firestore.FieldValue;

let testEnv;

test.before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: '127.0.0.1',
      port: 8085,
      rules: fs.readFileSync('firestore.rules', 'utf8')
    }
  });
});

test.after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
});

function dbFor(uid) {
  const ctx = uid ? testEnv.authenticatedContext(uid) : testEnv.unauthenticatedContext();
  return ctx.firestore();
}

test('unauthenticated users are denied everywhere', async () => {
  const anon = dbFor(null);
  await assertFails(anon.doc('users/alice').get());
  await assertFails(anon.doc('roomInvites/STEM26').get());
  await assertFails(anon.collection('studyRooms').get());
  await assertFails(anon.doc('users/alice').set({
    userId: 'alice',
    displayName: 'Alice',
    dailyGoalMinutes: 180
  }));
});

test('owner can manage private profile, subjects, sessions, todos, examGoals, blocks, and brainDumps; cross-user denied', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  await assertSucceeds(aliceDb.doc('users/alice').set({
    userId: 'alice',
    displayName: 'Alice',
    email: 'alice@example.com',
    dailyGoalMinutes: 240,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertFails(bobDb.doc('users/alice').get());
  await assertFails(aliceDb.collection('users').get());
  await assertFails(aliceDb.doc('users/alice').set({
    userId: 'bob',
    displayName: 'Alice',
    dailyGoalMinutes: 240
  }));

  await assertSucceeds(aliceDb.doc('users/alice/subjects/sub_1').set({
    userId: 'alice',
    localId: 1,
    name: 'Physics',
    colorHex: 0xFF38BDF8,
    dailyTargetMinutes: 90,
    sortOrder: 0,
    archived: false,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.collection('users/alice/sessions').add({
    userId: 'alice',
    subjectId: 1,
    subjectName: 'Physics',
    subjectColorHex: 0xFF38BDF8,
    mode: 'POMODORO',
    startMillis: 1700000000000,
    endMillis: 1700001500000,
    durationSeconds: 1500,
    dayEpoch: 20000,
    interruptionsCount: 0,
    notes: '',
    createdAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.doc('users/alice/todos/todo_1').set({
    userId: 'alice',
    localId: 1,
    subjectId: 1,
    subjectName: 'Physics',
    subjectColorHex: 0xFF38BDF8,
    title: 'Solve 20 Electrostatics MCQs',
    dayEpoch: 20000,
    completed: false,
    estimatedMinutes: 45,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.doc('users/alice/examGoals/goal_1').set({
    userId: 'alice',
    localId: 1,
    title: 'JEE Final',
    targetDateEpoch: 20060,
    targetDailyMinutes: 240,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.doc('users/alice/blocks/blk_1').set({
    userId: 'alice',
    dayEpoch: 20000,
    title: 'Deep Focus Block',
    why: 'High leverage chapter',
    microStep: 'Open page 12',
    startMinuteOfDay: 540,
    durationMinutes: 60,
    category: 'STUDY',
    decompressionBuffer: false,
    completed: false,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.collection('users/alice/brainDumps').add({
    userId: 'alice',
    text: 'Check optics formula sheet',
    createdAtMillis: 1700000000000,
    sentToPlanner: false,
    createdAt: FieldValue.serverTimestamp()
  }));

  await assertFails(bobDb.collection('users/alice/sessions').get());
  await assertFails(bobDb.doc('users/alice/todos/todo_hack').set({
    userId: 'alice',
    localId: 9,
    subjectId: 1,
    title: 'Hack',
    dayEpoch: 20000,
    completed: false,
    estimatedMinutes: 10
  }));
});

test('multi-user study room creation, invite lookup, join via arrayUnion, member presence & nudges', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const charlieDb = dbFor('charlie');

  await assertSucceeds(aliceDb.doc('studyRooms/room_1').set({
    name: 'STEM Deep Squad',
    inviteCode: 'STEM26',
    description: 'Silent accountability room',
    dailyGoalMinutes: 240,
    ownerId: 'alice',
    memberUids: ['alice'],
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.doc('roomInvites/STEM26').set({
    inviteCode: 'STEM26',
    roomId: 'room_1',
    ownerId: 'alice',
    createdAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(aliceDb.doc('studyRooms/room_1/members/alice').set({
    uid: 'alice',
    displayName: 'Alice',
    status: 'STUDYING',
    activeSubject: 'Physics',
    subjectColorHex: 0xFF38BDF8,
    todayMinutes: 85,
    updatedAt: FieldValue.serverTimestamp()
  }));

  // Blanket listing of invites or unjoined rooms is denied
  await assertFails(bobDb.collection('roomInvites').get());
  await assertFails(bobDb.collection('studyRooms').get());
  await assertFails(charlieDb.doc('studyRooms/room_1').get());

  // Bob looks up invite doc by code, joins via arrayUnion, then writes his member status
  const inviteSnap = await assertSucceeds(bobDb.doc('roomInvites/STEM26').get());
  assert.equal(inviteSnap.data().roomId, 'room_1');

  await assertSucceeds(bobDb.doc('studyRooms/room_1').update({
    memberUids: FieldValue.arrayUnion('bob'),
    updatedAt: FieldValue.serverTimestamp()
  }));

  await assertSucceeds(bobDb.doc('studyRooms/room_1/members/bob').set({
    uid: 'bob',
    displayName: 'Bob',
    status: 'STUDYING',
    activeSubject: 'Math',
    subjectColorHex: 0xFF2EE6A6,
    todayMinutes: 40,
    updatedAt: FieldValue.serverTimestamp()
  }));

  // Bob can query only rooms he belongs to
  const bobRooms = await assertSucceeds(
    bobDb.collection('studyRooms').where('memberUids', 'array-contains', 'bob').get()
  );
  assert.equal(bobRooms.size, 1);

  // Bob can read Alice's live presence and send a nudge, but cannot impersonate Alice
  await assertSucceeds(bobDb.doc('studyRooms/room_1/members/alice').get());
  await assertFails(bobDb.doc('studyRooms/room_1/members/alice').set({
    uid: 'alice',
    displayName: 'Hacked',
    status: 'OFFLINE',
    activeSubject: 'None',
    subjectColorHex: 0,
    todayMinutes: 0
  }));

  await assertSucceeds(bobDb.collection('studyRooms/room_1/nudges').add({
    fromUid: 'bob',
    fromName: 'Bob',
    toUid: 'alice',
    toName: 'Alice',
    message: 'Keep crushing Physics!',
    createdAt: FieldValue.serverTimestamp()
  }));

  // Charlie (non-member) cannot send nudges or read members
  await assertFails(charlieDb.doc('studyRooms/room_1/members/alice').get());
  await assertFails(charlieDb.collection('studyRooms/room_1/nudges').add({
    fromUid: 'charlie',
    fromName: 'Charlie',
    toUid: 'alice',
    toName: 'Alice',
    message: 'Spam'
  }));

  // Bob can leave room cleanly using arrayRemove
  await assertSucceeds(bobDb.doc('studyRooms/room_1').update({
    memberUids: FieldValue.arrayRemove('bob'),
    updatedAt: FieldValue.serverTimestamp()
  }));
});
