# InstaClone

An Instagram-style Android app for the USTH Mobile Application Development final project.
Members sign up, post photos, browse a feed, like, comment, follow each other and chat one-to-one.

**Demo target: Saturday 24 Oct 2026.**

## Tech stack

| Part | What we use |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Architecture | MVVM: Screen → ViewModel → Repository interface |
| Accounts | Firebase Authentication (email + password) |
| Data | Cloud Firestore |
| Photos | Cloud Storage for Firebase |
| Min Android | 8.0 (API 26) |

## Run it on your machine

1. Android Studio → **File → New → Project from Version Control** → paste this repo's URL → **Clone**.
2. Wait for the first Gradle sync to finish (a few minutes the first time).
3. Pick an emulator and press **Run ▶**.

`google-services.json` is already in `app/`, so Firebase works with no extra setup.
If the sync fails, check that you have the latest Android Studio, then ask in the group chat before changing anything.

## Where things go

```
app/src/main/java/vn/edu/usth/instaclone/
├── data/
│   ├── model/        Data classes: User, Post, Comment, Conversation, Message
│   └── repository/   Interfaces + their Fake and Firebase versions
├── ui/
│   ├── auth/         Login, Register          (Member 2, Phong)
│   ├── feed/         Home feed, post detail   (Member 4)
│   ├── post/         New post                 (Nam)
│   ├── profile/      Profile, edit profile    (Member 5, Nam)
│   ├── search/       Search users             (Member 5)
│   ├── chat/         Conversations, chat      (Member 6)
│   └── theme/        Colors, typography
└── MainActivity.kt
```

Work mainly inside **your own** `ui/<feature>/` folder. That keeps merge conflicts rare.

## How we build features

1. Screens only talk to a **repository interface** in `data/repository/`, never to Firebase directly.
2. First write a **fake** of your interface (`FakePostRepository`, …) that returns hard-coded data, and build your screens with it.
3. Then write the **Firebase** version (`FirebasePostRepository`, …). Swapping the fake for the real one changes no screen code.

Field names in `data/model/` match the Firestore data model in the requirements docs (SRS section 4). Firestore matches fields by name, so **don't rename fields** without telling the team.

## Team rules

**Branches**
- `master` must always build and run. Never push broken code to it.
- Work on your own branch: `feature/T-13-create-post` (task ID + short name).
- When it works, open a Pull Request to `master` and ask one teammate to check it.

**Commits**
- Start every message with the task ID: `T-18: add pull to refresh to home feed`.
- Small commits, often. The course grades commit messages.

**Pull before you start each day**
- `Git → Pull` on `master`, then merge `master` into your branch, so you build on everyone's latest work.

**Don't**
- Don't commit `local.properties`, `build/` or `.idea/` (the `.gitignore` already hides them).
- Don't update library versions in `gradle/libs.versions.toml` on your own. One mismatch breaks everyone's build.
- Don't change an interface in `data/repository/` without telling the person who owns it.

## Tasks and deadlines

Tasks (T-01 … T-34), owners and status live in the **Work split** tab of the requirements docs.

| Date | Milestone |
|---|---|
| Sun 18 Oct | Kick-off: everyone can clone and run |
| Mon 19 Oct | App shell, seed data, security rules |
| Wed 21 Oct | Core loop works: register → log in → post → feed → like |
| Thu 22 Oct | All Must features done |
| Fri 23 Oct | Feature freeze: everything works, bug fixes only after this |
| Sat 24 Oct | Ready to demo |

## Team

| Member | Area |
|---|---|
| Phong | Foundation: project setup, app shell, login, log out |
| Member 2 | Accounts and security |
| Nam | Posting and media |
| Member 4 | Feed, likes, comments |
| Member 5 | Profiles, follow, search |
| Member 6 | Chat and demo |
