<p align="center"><img alt="logo" width="160" src="media/logo.svg"></p>

<h1 align="center">Distortion Waves</h1>

<p align=center>
    <a href="https://search.maven.org/artifact/at.florianschuster.distortionwaves/distortion-waves"><img alt="version" src="https://img.shields.io/maven-central/v/at.florianschuster.distortionwaves/distortion-waves?label=version&logoColor=f88909" /></a>
    <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-MIT-blue.svg?color=7b6fe2" /></a>
</p>

<p align=center>
    <a href="https://github.com/floschu/distortion-waves/"><img alt="last commit" src="https://img.shields.io/github/last-commit/floschu/distortion-waves?logoColor=ffffff" /></a>
    <a href="https://github.com/floschu/distortion-waves/actions"><img alt="build" src="https://github.com/floschu/distortion-waves/workflows/build/badge.svg" /></a>
</p>

> [!NOTE]
> A Compose Multiplatform port of [Distortion Waves](https://libraries.dev/waves) by [Jakub Antalik](https://x.com/jakubantalik):
>
> *A colourful wave that carries one screen into the next. The screen pulls toward the button first, then the wave leaves from where it was touched, leaning that way, and leaves its colour on the edges. Vertical, horizontal and center. React and SwiftUI.*

<p align="center"><img alt="the sample app's demos, each sending its wave" width="100%" src="media/showcase.webp"></p>

## installation

``` kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("at.florianschuster.distortionwaves:distortion-waves:$version")
}
```

Runs on Android, iOS, desktop and Wasm. On Android the wave needs Android 13. Below that, and when the system asks for less motion, it is a crossfade.

## usage

``` kotlin
var sent by remember { mutableStateOf(false) }

DistortionWave(
    revealed = sent,
    modifier = Modifier.fillMaxSize(),
    overlay = {
        Button(
            onClick = { sent = !sent },
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
                .distortionWaveSource(),
        ) {
            Text(text = if (sent) "Done" else "Confirm")
        }
    },
    revealedContent = { Receipt() },
    content = { Review() },
)
```

## skills

Check out the [distortion-waves skill](skills/distortion-waves) to implement `distortion-waves` with your **AI agents**. It also documents `DistortionWaveContent`, `DistortionWaveState`, styling and platform support.

## author

visit my [website](https://flosch.at/).
