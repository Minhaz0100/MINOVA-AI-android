const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- SECURITY BOUNDS TESTS ---

test("Unauthenticated user: cannot read or write any user data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );
});

test("Cross-user isolation: Bob cannot read Alice's conversations", async () => {
  // Seed Alice's conversation using admin context
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const now = new Date();
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        id: "conv_1",
        userId: ALICE_UID,
        title: "Alice Private Chat",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );
});

test("Cross-user isolation: Bob cannot create a conversation under Alice", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bob_spoof")
      .set({
        id: "conv_bob_spoof",
        userId: BOB_UID,
        title: "Spoofed Chat",
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Owner access: Alice can create and read her own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      createdAt: now,
      lastLoginAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Owner access: Alice can create and read her own conversation and message", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Create conversation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_100")
      .set({
        id: "conv_100",
        userId: ALICE_UID,
        title: "Kotlin Coroutines Discussion",
        createdAt: now,
        updatedAt: now,
        isPinned: false,
        isArchived: false,
        lastMessage: "Hello AI",
      })
  );

  // Read conversation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_100")
      .get()
  );

  // List conversations
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );

  // Create message
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_100")
      .collection("messages")
      .doc("msg_200")
      .set({
        id: "msg_200",
        conversationId: "conv_100",
        sender: "USER",
        content: "Explain Kotlin Flow please",
        timestamp: now,
        status: "SENT",
        isPinned: false,
      })
  );

  // Read message
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_100")
      .collection("messages")
      .doc("msg_200")
      .get()
  );
});

test("Schema validation: rejects conversation with missing required title", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad")
      .set({
        id: "conv_bad",
        userId: ALICE_UID,
        // title is missing
        createdAt: now,
        updatedAt: now,
      })
  );
});
