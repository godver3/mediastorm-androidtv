# MediaStorm for Android

Native Android clients for the [MediaStorm](https://github.com/godver3/mediastorm) server,
built with Kotlin, Jetpack Compose and Media3 ExoPlayer: an Android TV app and a phone and
tablet app that share one core.

| Module | What it is |
| --- | --- |
| `core` | Shared logic: API client, session, stores, player engine and its rules, iroh remote access, Dolby Vision. No Compose for TV components. |
| `app` | The Android TV app (`com.strmr.tv`), Compose for TV. |
| `mobile` | The phone and tablet app (`com.strmr.mobile`), Material 3. |

## Building

Requires JDK 17 and the Android SDK.

```sh
./gradlew :app:assembleDebug      # Android TV
./gradlew :mobile:assembleDebug   # phone and tablet
```

An optional, gitignored `secrets.properties` (`serverUrl`, `username`, `password`,
`profileName`, `profilePin`) prefills the sign-in screens in debug builds.

## License

MIT. See [LICENSE](LICENSE).
