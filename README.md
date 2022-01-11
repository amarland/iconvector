# iconvector

iconvector is a JVM library whose goal is to provide a set of APIs for rendering [IconVG](https://github.com/google/iconvg) assets. It is based on the [Flutter implementation](https://github.com/google/iconvg/tree/main/src/dart) of the renderer and aims to support both JVM implementations of Jetpack Compose ([Android](https://developer.android.com/jetpack/compose) and [Desktop](https://github.com/JetBrains/compose-jb)) as well as expose convenience functions for use with `android.graphics.Drawable`-reliant APIs.

**NOTE:** This library was implemented **in accordance with the FFV0 specification**, which is considered experimental and is meant to be superseded by the (non-compatible) FFV1 specification. Newer IconVG files (which adhere to the aforementioned specification, and whose extension typically is `iconvg`, as opposed to `ivg`) are therefore not supported (yet?).

⚠️ *You should probably not expect much from this library. The implementation does not always yield satisfactory results, especially with non-basic assets and is not being developed actively enough to be relied upon.*
