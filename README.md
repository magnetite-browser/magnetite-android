# Magnetite for Android

The phone and tablet app of [Magnetite](https://github.com/magnetite-browser/magnetite).
It browses with the system's web engine (Android System WebView) and shows
the spaces, favorites and pinned tabs of a sync chain. It never changes the
chain's sidebar. What it writes is its own entry among the chain's devices:
the tabs open on it, which another device can take over ("Send to Desktop"
asks for that).

## Build

Needs JDK 17, the Android SDK (platform 37) with an NDK, Rust (with the
targets in the core's `rust-toolchain.toml`) and `cargo-ndk`. The shared Rust
core is its own repository,
[magnetite-mobile-core](https://github.com/magnetite-browser/magnetite-mobile-core),
expected beside this one as `../magnetite-mobile-core`.

```sh
export ANDROID_HOME=…                        # the SDK
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/…  # the NDK in it
echo "sdk.dir=$ANDROID_HOME" > local.properties
scripts/core.sh              # builds the core into core/ (again after the core changed)
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

## Test

The UI tests join a demo chain and open a page from it; one runs on a phone,
the other on a tablet (a screen at least 720 dp wide). Start the demo server
and an emulator or device, then:

```sh
(cd ../magnetite-mobile-core && cargo run --example demo_server) &
scripts/test.sh              # erases the app's data on the device first
```

With the demo server on another port (`DEMO_PORT=8790 cargo run --example
demo_server`), run `DEMO_PORT=8790 scripts/test.sh`: the device's port 8787
then leads to it.

The pictures the tests take land in `build/shots`. To try the app by hand,
run `adb reverse tcp:8787 tcp:8787` and join `http://localhost:8787` ("Your
own server") with the words the demo server prints.
`curl -X POST localhost:8787/demo/take` plays a desktop that takes over the
tabs the device lists.

## Layout

- `model/`: the tabs of this device (`Browser`), the sync chain (`Sync`), the
  sync code encrypted with a key of the device's key store (`SecretStore`),
  what the address field's text means (`Address`) and offers while typing
  (`Suggestions`), site icons (`Favicons`: kept when a page of the site was
  loaded here; a row never contacts a site).
- `web/`: one tab's web view and its state.
- `ui/`: the page with its bars (`Bars.kt`), the sidebar (a page per space,
  swiped through as on the desktop; `SpaceIcon` draws the desktop's space
  icons), joining (by typing the 24 words or by scanning the QR code a
  computer shows beside them), settings. On a wide screen (tablet) the
  sidebar is a column beside the page; on a narrow one it is the start page
  and a sheet, and the bar floats over the page's bottom.
- `core/`: generated, not in the repository.

Each profile of the chain is a profile of the web engine, so spaces keep
their cookies and logins apart as on the desktop. That needs a web engine
with profiles (`WebViewFeature.MULTI_PROFILE`); on an older one all spaces share one, and a private space is not
private.

## Licence

[EUPL-1.2](LICENSE), as Magnetite itself. The folder, computer, desktop,
link, find, text size and not-secure icons in `app/src/main/res/drawable`
are from Google's
[Material Icons](https://github.com/google/material-design-icons), the space
icons (`ic_space_*`) and the new-tab icons from its Material Symbols
(both Apache-2.0).
QR codes are read with [ZXing](https://github.com/zxing/zxing) (Apache-2.0).
