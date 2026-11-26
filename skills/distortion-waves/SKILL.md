---
name: distortion-waves
description: Use when adding or fixing a DistortionWave transition from the at.florianschuster.distortionwaves Compose Multiplatform library. Triggers on DistortionWave, DistortionWaveContent, DistortionWaveState, DistortionWaveStyle, distortionWaveSource, or a request for a colourful wave, ring or swell that carries one Compose view into another.
---

# Distortion Waves in Compose

`DistortionWave` carries one composable into another with a wave that opens from a source element. This skill covers how to wire it up so it looks right. Read the checklist before you write code, and run through it again before you finish.

## Add the dependency

If the project does not depend on the library yet, add it to the shared module's `commonMain`. Check [Maven Central](https://central.sonatype.com/artifact/at.florianschuster.distortionwaves/distortion-waves) for the latest version.

```kotlin
implementation("at.florianschuster.distortionwaves:distortion-waves:<version>")
```

## Pick the entry point

Go down this list and stop at the first that fits.

1. **A view that changes with a state value.** Use `DistortionWaveContent(targetState) { state -> }`, as you would `Crossfade`. This is also how to give a Material component a wave: put the Material component in the lambda.
2. **Two views and a flag.** Use the boolean overload of `DistortionWave`.
3. **A wave started from an event**, awaited, or tracked frame by frame. Use `DistortionWaveState`.

Use the boolean overload when a state flag already says which view shows.

```kotlin
DistortionWave(
    revealed = sent,
    modifier = Modifier.fillMaxSize(),
    overlay = {
        ConfirmButton(
            modifier = Modifier.align(Alignment.BottomCenter).distortionWaveSource(),
            onClick = { sent = true },
        )
    },
    revealedContent = { Receipt() },
    content = { Review() },
)
```

Every slot of a wave (`overlay`, `content`, `revealedContent`) is a `DistortionWaveScope`. It is a `BoxScope` with one more modifier, `distortionWaveSource()`, which marks the element the wave opens from. It works the same in all three entry points.

Use `DistortionWaveState` when you start the wave from an event, need to wait for it, or change your own content in step with it.

```kotlin
val wave = rememberDistortionWaveState()
val scope = rememberCoroutineScope()

DistortionWave(
    state = wave,
    overlay = {
        ConfirmButton(
            modifier = Modifier.align(Alignment.BottomCenter).distortionWaveSource(),
            onClick = { scope.launch { wave.reveal() } },
        )
    },
    revealedContent = { Receipt() },
    content = { Review() },
)
```

`reveal()`, `collapse()`, `toggle()` and `play()` suspend until the wave has finished. Put follow-up work after them in the same coroutine. `snapTo()` and `scrubTo()` do not suspend, so a slider can call them directly. `rememberDistortionWaveState()` keeps the side on show across a configuration change.

## Checklist

1. **Opaque backgrounds on both sides.** The wave bends each side as a flat picture. A transparent side shows the other one through it, and the lens smears the gap. Give `content` and `revealedContent` a `background`.
2. **Live controls go in `overlay`.** Anything inside the two sides is bent and blurred while the wave passes. The button that starts the wave, a spinner or a video belongs in `overlay`, which stays sharp above both.
3. **Tell the wave where it starts.** Put `Modifier.distortionWaveSource()` on the element the wave opens from, inside any of the wave's slots. Place it after padding and size modifiers, so it reports the element's own bounds. For an element outside the wave, use `Modifier.distortionWaveSource(state)`. Without a source the wave opens from a point on the edge, which looks wrong next to a real button.
4. **Match the type to the source.** `Vertical` wants a wide source near the bottom. `Horizontal` wants a tall source at the start edge, the right one in a right-to-left layout. `Center` wants a round source and keeps it sharp, so the source can change in place.
5. **Set `cornerRadius` to the container's corners.** It is a `Dp`, the same value the container's shape takes, whatever the `scale`. The colour on the edges follows it. A full-screen wave on a phone takes the display's corner radius. A card takes the card's radius. Clip the `DistortionWave` to the same shape.
6. **Scale the preset to the container.** `Vertical`, `Horizontal` and `Center` are tuned for a full phone screen, `Success` for a card about 500 dp tall. On a small card set `scale` near the container's height divided by 850, or by 500 for `Success`.
7. **Do not recompose per frame.** Read `state.progress` and `state.elapsed` inside `graphicsLayer {}`, a draw lambda or `derivedStateOf`. A plain read in a composable body recomposes it on every frame of the wave.
8. **Let repeat taps through.** `reveal()` on a wave that is out does nothing, and `toggle()` mid-wave turns the wave around. Check `state.isRunning` only if a tap mid-wave must do nothing.
9. **Do not start work in a side's composition.** Both sides of `DistortionWave` stay composed the whole time, so a `LaunchedEffect` in `revealedContent` runs before the wave is ever sent. Start that work from `onFinish` or after `reveal()` returns. The side that is not on show takes no touches and no focus. `DistortionWaveContent` composes only the view on show while at rest, and the new view keeps what it remembers when the wave has gone.
10. **Leave the fallback alone.** Below Android 13, and when the system asks for less motion, the wave is a crossfade. Do not add your own `Build.VERSION` check. Pass `motion` only if the product needs to override the system. The wave runs on Android, iOS, desktop and Wasm. Desktop does not report the system setting, so pass `motion = DistortionWaveMotion.Reduced` there if the app has its own.

## Recipes

**Toggle.** A tap mid-wave turns the wave around.

```kotlin
scope.launch { wave.toggle() }
```

**Processing, then success.** Do the work first and send the wave when it succeeds.

```kotlin
scope.launch {
    processing = true
    pay()
    wave.reveal()
    processing = false
}
```

**A list of pages.** Show page `i` and page `i + 1`. When the wave has gone, move the index and put the wave back at rest.

```kotlin
scope.launch {
    wave.reveal()
    index++
    wave.snapTo(revealed = false)
}
```

**One view that changes in place.** Pass the same composable to both sides and drive its changes from `wave.elapsed`. `wave.launchTime` is the moment the front leaves the source.

```kotlin
val morph = { ((wave.elapsed - wave.launchTime - 0.4f) / 0.35f).coerceIn(0f, 1f) }
DistortionWave(
    state = wave,
    revealedContent = { Screen(morph) },
    content = { Screen(morph) },
)
```

Inside `Screen`, call `morph()` only in `graphicsLayer {}` or a draw lambda. Use `wave.play()` if the view should be back at rest when the wave ends.

**A success on a card.** `Success` is a slower green ring for a card about 500 dp tall.

```kotlin
DistortionWaveStyle.Success.copy(cornerRadius = 28.dp)
```

**A quiet ring from a corner, no pull.**

```kotlin
val preset = DistortionWaveStyle.Center
preset.copy(
    scale = 1.7f,
    palette = DistortionWavePalette.Mono,
    anticipation = preset.anticipation.copy(duration = 0f),
    band = preset.band.copy(opacity = 0.4f),
    rim = preset.rim.copy(opacity = 0.3f),
)
```

## Styling

Start from a preset: `Vertical`, `Horizontal`, `Center`, or `Success`. `copy` what you need. Values that belong to one part of the wave sit in that part's group: `anticipation`, `lens`, `blur`, `band`, `rim`, `hairline`, `particles`. Change a group with its own `copy`, so the rest of the preset stays.

```kotlin
val preset = DistortionWaveStyle.Vertical
val style = preset.copy(
    speed = 1.5f,
    lens = preset.lens.copy(aberration = 0f),
)
```

Times are seconds. Lengths are plain numbers in dp times `scale`. `cornerRadius` is the exception: a `Dp` that `scale` leaves alone.

| Want | Change |
| --- | --- |
| Other colours | `palette`: `Colorful`, `Green`, `Ocean`, `Sunset`, `Mono`, `DistortionWavePalette.single(color)`, or your own with up to eight colours. `DistortionWavePalette.Presets` lists the built-in ones |
| Faster or slower | `speed` |
| Calmer distortion | lower `lens.amplitude`, `blur.radius`, `lens.aberration` |
| No colour split on white text | `lens.aberration = 0f` |
| No dots | `style.withoutParticles()` |
| Clean edges | `style.withoutRim()` |
| Only the distortion | `style.withoutColor()` |
| Less lean toward the press | `lean` from 0 to 1 |
| Shorter wind-up | `anticipation.duration`, `anticipation.pull` |
| Fainter colour | `band.opacity`, `rim.opacity` |

Build the style with `remember` if you derive it in a composable. Styles with equal values are equal, so they do not restart anything.

## Mistakes to avoid

- Wrapping the wave's content in `AnimatedContent` or `Crossfade`. The wave is the transition. To wave between states, use `DistortionWaveContent` in their place.
- Putting the trigger button inside `content`. It distorts with the screen, and it disappears when the wave has passed.
- Passing a `source` rect or a `focus` in root or window coordinates. Both are in the wave's own pixel coordinates, on the composables and on `reveal(focus)`. Prefer `distortionWaveSource`, which needs neither.
- Using `Success` at its preset size on a full screen. It is tuned for a card. Raise `scale`, or start from `Center`.
- Writing `DistortionWaveStyle.Lens(aberration = 0f)` to change one value. That resets the rest of the group to the defaults. Use `preset.lens.copy(aberration = 0f)`.
- Placing platform views, such as `AndroidView`, a map or a camera preview, inside a side. They do not pass through the render effect. Put them in `overlay` or swap them in after the wave.

## Verify

Run the wave once and check five things. The front leaves from the source's outline. The button stays sharp. Neither side shows through the other before the front arrives. The edge colour follows the container's corners. The screen is interactive again after `reveal()` returns.
