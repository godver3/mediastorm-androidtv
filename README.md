# MediaStorm for Android TV

A native Android TV client for the [MediaStorm](https://github.com/godver3/mediastorm)
server, built with Kotlin, Jetpack Compose for TV and Media3 ExoPlayer.

## Building

Requires JDK 17 and the Android SDK.

```sh
./gradlew assembleDebug
```

An optional, gitignored `secrets.properties` (`serverUrl`, `username`, `password`,
`profileName`, `profilePin`) prefills the sign-in screens in debug builds.

## License

MIT. See [LICENSE](LICENSE).
