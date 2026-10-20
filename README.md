# InstaClone

An Instagram-style Android app for the USTH Mobile Application Development final project: sign up, post photos, browse a feed, like, comment, follow and chat one-to-one.

**Tasks, owners, progress and requirements:** [team docs](https://claude.ai/artifact/VyGpjeybkqDMoTTGyg3wz3) (see the **Work split** tab). This README only covers the code.

## Run it

1. Android Studio → **File → New → Project from Version Control** → paste this repo's URL → **Clone**.
2. Wait for Gradle sync, pick an emulator, press **Run ▶**.

`google-services.json` is already in `app/`, so Firebase works with no setup.

**Stack:** Kotlin, Jetpack Compose (Material 3), MVVM, Firebase Auth + Firestore + Storage. Min Android 8.0 (API 26).

## Where things go

```
app/src/main/java/vn/edu/usth/instaclone/
├── data/
│   ├── AppContainer.kt   One line per repository: Fake or Firebase
│   ├── model/            User, Post, Comment, Conversation, Message
│   └── repository/       Interfaces  (fake/ and firebase/ hold the versions)
└── ui/
    ├── navigation/       Routes, bottom bar, AppNavHost
    ├── auth/  feed/  post/  profile/  search/  chat/   One folder per feature
    └── common/  theme/
```

## Adding your feature

1. Write a fake of your repository in `data/repository/fake/` that returns hard-coded data.
2. Register it in `AppContainer.kt`: `val postRepository: PostRepository by lazy { FakePostRepository() }`
3. Build your screen in `ui/<feature>/`, copying the pattern of `ui/auth/LoginScreen.kt` + `LoginViewModel.kt`.
4. In `AppNavHost.kt`, replace **only your own** `PlaceholderScreen(...)` with your screen.
5. Write the Firebase version in `data/repository/firebase/` and change that one line in `AppContainer`.

Screens talk only to repository interfaces, never to Firebase directly. Don't rename fields in `data/model/`: Firestore matches them by name.

## Git workflow

1. `Git → Pull` on `master`.
2. New branch: `feature/T-xx-short-name`.
3. Code, run, test. Commit with the task ID: `T-18: add pull to refresh`.
   ⚠️ Tick **Unversioned Files** in the commit panel, or your new files stay behind.
4. Push, open a Pull Request, merge, then `Git → Pull` on `master` again.

**Never** commit on `master` directly, change `libs.versions.toml` versions, or edit someone else's interface without telling them.

## Team

| Member | Area |
|---|---|
| Phong | Foundation, login, log out |
| Đạt | Register and security rules |
| Nam | Posting and media |
| Trâm | Feed, likes, comments |
| Trang | Profiles, follow, search |
| Thịnh | Chat and demo |
