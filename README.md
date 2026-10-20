# InstaClone

An Instagram-style Android app for the USTH Mobile Application Development final project.
Members sign up, post photos, browse a feed, like, comment, follow each other and chat one-to-one.

**Demo target: Saturday 24 Oct 2026.**

## Progress (updated Tue 20 Oct)

| Done | Task |
|---|---|
| ✅ | T-02 Project setup, Firebase connected, data models, repository interfaces |
| ✅ | T-03 App shell: bottom bar, routes, a placeholder screen for every screen (PR #1) |
| ✅ | T-11 Firebase Blaze plan + budget alert |
| 🔄 | T-10 Login screen + `AppContainer` (Phong, in progress) |

**Next milestone, Wed 21 Oct:** the core loop works on real Firebase: register → log in → post → feed → like.
Full task list and owners: the **Work split** tab of the requirements docs.

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
│   ├── AppContainer.kt   Picks Fake or Firebase for each repository (arrives with T-10)
│   ├── model/            Data classes: User, Post, Comment, Conversation, Message
│   └── repository/       Interfaces
│       ├── fake/         Fake versions (hard-coded data)
│       └── firebase/     Real versions
├── ui/
│   ├── navigation/   Routes, bottom bar, AppNavHost (the map of all screens)
│   ├── common/       Shared pieces, e.g. PlaceholderScreen
│   ├── auth/         Login, Register          (Đạt, Phong)
│   ├── feed/         Home feed, post detail   (Trâm)
│   ├── post/         New post                 (Nam)
│   ├── profile/      Profile, edit profile    (Trang, Nam)
│   ├── search/       Search users             (Trang)
│   ├── chat/         Conversations, chat      (Thịnh)
│   └── theme/        Colors, typography
└── MainActivity.kt
```

Work mainly inside **your own** `ui/<feature>/` folder. That keeps merge conflicts rare.

### Plugging your screen in

Every screen already exists as a placeholder in `ui/navigation/AppNavHost.kt`, labelled with its owner and task.
Replace **only your own** `PlaceholderScreen(...)` line with your real screen. Don't edit other people's lines.

Your screen takes navigation as parameters (e.g. `onOpenPost: (String) -> Unit`) and never touches `navController` itself.
Navigate with the helpers in `Routes`: `Routes.postDetail(id)`, `Routes.profile(userId)`, `Routes.chat(userId)`.

## How we build features

1. Screens only talk to a **repository interface** in `data/repository/`, never to Firebase directly.
2. First write a **fake** of your interface (`FakePostRepository`, …) in `data/repository/fake/` that returns hard-coded data, and build your screens with it.
3. Register it in `data/AppContainer.kt` with **one line**:
   `val postRepository: PostRepository by lazy { FakePostRepository() }`
4. Then write the **Firebase** version (`FirebasePostRepository`, …) in `data/repository/firebase/` and change only that one line in `AppContainer`. Swapping the fake for the real one changes no screen code.

Screens follow the pattern of `ui/auth/LoginScreen.kt` + `LoginViewModel.kt`: the ViewModel holds a `UiState` data class and calls the repository; the screen only draws the state.

Field names in `data/model/` match the Firestore data model in the requirements docs (SRS section 4). Firestore matches fields by name, so **don't rename fields** without telling the team.

## Team rules

**Branches**
- `master` must always build and run. Never push broken code to it.
- Work on your own branch: `feature/T-13-create-post` (task ID + short name).
- When it works, open a Pull Request to `master` and ask one teammate to check it.

**The loop for every task**
1. `Git → Pull` on `master`.
2. New branch from `master`: `feature/T-xx-short-name`.
3. Code, run, test against your user story's acceptance criteria.
4. Commit and push your branch.
5. On GitHub: **Pull request → Merge**. Then `Git → Pull` on `master` again.

**Commits**
- Start every message with the task ID: `T-18: add pull to refresh to home feed`.
- Small commits, often. The course grades commit messages.
- ⚠️ **Before every commit, check "Unversioned Files"** in the commit panel. Files you just created start there **unticked**. If you forget them, your code builds on your machine but breaks for everyone else.

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
| Đạt | Accounts and security |
| Nam | Posting and media |
| Trâm | Feed, likes, comments |
| Trang | Profiles, follow, search |
| Thịnh | Chat and demo |
