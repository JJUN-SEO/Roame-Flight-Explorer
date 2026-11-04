# Case study: finding and fixing the Android map's memory leak

Step 6.1, 2026‑11‑03 to 2026‑11‑04. Decision D31. Commits `bed22f2` and `623485d`.

Who did what: Claude (the AI assistant) ran the measurements, found the cause and wrote the fix. The owner chose the approach (D31), reviewed each step and accepted it. Every number here comes from a run on the owner's machine; nothing is estimated.

## 1. The short version

**The problem.** In the Android app, every time someone switched to the map, about 7 MB of memory stayed behind for good. After 18 switches the app's native memory had grown from 11 MB to 141 MB, and it kept rising in a straight line.

**The cause.** MapLibre, the map library, leaks native memory when one of its map views is created, or is removed from the screen and put back. The app did both on every switch.

**The fix.** Make the map once, and never take it off the screen. When the list is shown, the map stays in place underneath it, invisible and paused. When the Saved tab is shown, the Explore screen stays loaded but isn't drawn.

**The result.** Native memory stays flat: about 53 MB after 6, 12 or 18 switches, and the same after trips to the Saved tab. Switching back to the map is now instant, too.

| Switches to the map | Before | First fix attempt | Final fix |
|---|---|---|---|
| 0 (app just opened) | 11 MB | 11 MB | 11 MB |
| 6 | 59 MB | 63 MB | 53 MB |
| 12 | 100 MB | 88 MB | 53 MB |
| 18 | 141 MB | 113 MB | 54 MB |

These are Android's "native heap" figures from `dumpsys meminfo`, after forcing a garbage collection, on the emulator.

**An analogy.** Imagine a TV that leaves a little rubbish behind every time you unplug it and plug it back in. The first idea was "stop buying a new TV each time", which helped a bit. The real fix was "stop unplugging it": leave it plugged in and switch the screen off.

## 2. Background

Flight Explorer shows award flights as a list or on a map. On Android, the map is MapLibre's `MapView`, a classic Android view, placed inside the Jetpack Compose UI with `AndroidView`.

The original design, from Step 4.5:
- The map was created the first time Map was chosen, so someone who never opens the map never pays for it.
- It was destroyed when the List was chosen, and created again on the next switch. Compose's `Crossfade` did this automatically: it removes whichever screen isn't showing.
- Switching tabs to Saved removed the whole Explore screen, map included.

That's the normal, tidy way to write it, and it's what the leak punished.

## 3. How the leak was found

The leak wasn't reported by anyone; it turned up in a planned performance pass (PLAN Step 6.1, "Performance and leaks").

**First, fixing the measuring tool.** Earlier frame-time numbers had been useless, and the reason was that the emulator ran headless, which made it draw in software (`lavapipe`). Starting it with a window and `-gpu host` made it use the Mac's GPU ("Android Emulator OpenGL ES Translator (Apple A18 Pro)"). Lesson: check that the test bench measures what you think it measures.

**Second, a related fix that came first.** Android's StrictMode reported three disk reads on the main thread whenever the map opened, all inside MapLibre (loading its native library, finding its cache folder, finding the app's files folder). By reading MapLibre's compiled classes with `javap`, it turned out each of those results is cached by MapLibre. So the app now does all three on a background thread before creating the map, and StrictMode reports nothing.

**Then, the signal.** Running a stress loop (switching between List and Map five times, then five more) and reading `dumpsys meminfo` after each round showed the app's total memory (PSS) climbing: 171 MB, then 204 MB, then 256 MB.

That alone didn't prove a leak, because memory can be garbage that simply hasn't been collected yet. So the next step was to force a garbage collection before every reading and look at the parts separately:
- Java heap: 14–18 MB, and flat at 15 MB in the longer runs that followed. Objects written in Kotlin and Java were being freed.
- Views: back to 12 at rest. No views were being kept.
- Native heap: 153 MB and climbing. Native memory belongs to C/C++ code, and in this app the big C++ user is MapLibre.

## 4. Making the measurement trustworthy

Before testing ideas, the measurement had to be repeatable. A small script (`maploop.sh`, kept in the scratch folder, not the repo):
1. Force-stops and relaunches the app, and waits until it's really up.
2. Forces a garbage collection, then records native heap, Java heap, total memory, view count and thread count.
3. Taps Map, waits, taps List, waits, six times, using the same screen coordinates every run.
4. Records again, and repeats.

Two problems came up and were fixed along the way:
- **Heap dumps froze the app.** The first way to force a garbage collection was a heap dump (`am dumpheap`). The owner's Mac has 8 GB of RAM and was swapping heavily, so writing a dump made the whole app stall, and then `dumpsys` hung as well. The fix was a much lighter trigger: `adb shell run-as <package> kill -10 <pid>`. Android's runtime treats signal 10 (SIGUSR1) as "collect garbage now", and the log confirmed it: "SIGUSR1 forcing GC (no HPROF)".
- **Readings taken too early.** Right after an install the app takes a while to start, so the script waits for the process before measuring. Blank readings were discarded and retaken, never guessed.

Because the two garbage-collection methods differ, the early runs start from a higher baseline (19–26 MB) than the later ones (11 MB). Compare each run with itself, not across methods.

## 5. Narrowing down the cause

Each idea was tested by changing one thing and running the same measurement.

| Question | Test | Result | Conclusion |
|---|---|---|---|
| Is it really growing, or just slow to be freed? | Force garbage collection before each reading | Native heap 19 → 84 → 133 MB over 12 switches, against 15 → 21 MB for the Java heap | Native memory is really growing, and by far the most |
| Did today's change cause it? (The map's start-up had just moved to a background thread) | Build the code from before that change and run the same test | 64 → 105 MB over 6 → 12 switches | No: the leak was already there, since Step 4.5 |
| Are threads piling up? | Count the app's threads | 29 at start, 38 after 6 switches, still 38 after 12 | No thread leak |
| Is the map being shut down in the wrong order? The app called `onDestroy` without `onPause` and `onStop` first, which MapLibre's lifecycle expects | Add `onPause` and `onStop` before `onDestroy` | Still 26 → 87 → 130 MB | Not the cause. The correct order was kept anyway |
| Does it level off, like a cache, or keep going? | 18 switches with the lighter garbage-collection trigger | 11.5 → 59 → 100 → 141 MB; Java heap 15 MB throughout; views 12 and threads 42–43 after the first switches | It keeps going in a straight line, about 7 MB per switch. A leak, not a cache |
| Is it a known MapLibre bug? | Web search | Nothing specific to MapLibre. One Mapbox report blamed a location marker, which this map doesn't have | No known fix to borrow |
| Does iOS have it too? | The iOS QA in Step 5.8 measured memory across the same loops | 135 → 136 MB, flat | Android only |

At that point the leak was clearly inside MapLibre's native code, not in the app's Kotlin. Fixing MapLibre itself wasn't realistic for this project, so the question became how to use MapLibre in a way that doesn't trigger it.

## 6. Choosing an approach

Three options went to the owner, with their costs:

- **A. Keep one map alive.** Create it once and hide it instead of destroying it. Memory should stay at one map's worth, and switching back is instant. Cost: a bigger change to the Explore screen, the map's memory stays in use while it's hidden, and the map checks have to be redone.
- **B. Leave it and document it.** It only matters for someone who switches to the map many times in one session.
- **C. Try MapLibre's OpenGL build instead of Vulkan.** A library change that might not help, if the leak isn't in the Vulkan code.

The owner chose A. It's recorded as decision D31 in SUBMISSION_NOTES §2 and ARCHITECTURE §11.

## 7. First attempt: one map, but still detached

**What it did.** A new `MapHolder` class owned the single `MapView`, its loaded style and its listeners. It lived above both tabs, next to the camera state. When the user switched to the list, the map was paused and stopped instead of destroyed. When they came back, the same view was put back on screen.

**What it measured.** 11 → 63 → 88 → 113 MB over 18 switches. Better, but it still grew by about 4 MB per switch.

**Finding out why.** Hiding the map in this version still removed it from the window, then put it back. To separate the two possible culprits (the drawing surface being rebuilt, or the view being detached and reattached), there was one more experiment:

- **Home and back, with the map on screen.** Leaving the app destroys the map's drawing surface and rebuilds it on return, but the view itself stays attached to the window. Six round trips: 131.8 → 131.9 MB. **No leak.**
- **The app's own code.** The pill-drawing code only adds a pill image the first time it sees a label, so it wasn't adding memory on each show.

So rebuilding the surface was harmless; **detaching the view from the window and attaching it again** was what leaked. That gave the real fix.

## 8. Final fix: never detach the map

**The idea.** Once the map exists, it never leaves the window.

**List and Map.** The map is no longer inside the `Crossfade`. Once it has been opened, it sits underneath, and the list fades in on top with an opaque background. When the list is showing, the map is set to `View.INVISIBLE` and paused. An invisible view still belongs to the window, but it gets no taps and TalkBack doesn't read it.

```kotlin
AndroidView(
    // The same view comes back when the Explore tab does, so take it from where it was shown last.
    factory = {
        (mapView.parent as? ViewGroup)?.removeView(mapView)
        mapView
    },
    modifier = Modifier.fillMaxSize(),
    // An invisible view gets no taps and isn't read by TalkBack. Its surface goes too, as when the app is in
    // the background, but the view stays attached: detaching it is what leaks.
    update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
)
```

**The Saved tab.** Before, switching tabs removed the Explore screen and so detached the map. Now the Explore screen stays in the composition while Saved is shown: it's measured (so it keeps its size and layout) but not placed. Something that isn't placed isn't drawn and can't be tapped, and its accessibility information is cleared, so TalkBack can't reach it either.

```kotlin
private fun Modifier.notPlacedUnless(placed: Boolean): Modifier = if (placed) {
    this
} else {
    clearAndSetSemantics {}.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }
}
```

**Keeping MapLibre's lifecycle correct.** MapLibre expects `onStart`, `onResume`, `onPause`, `onStop` and `onDestroy` in the right order, never repeated. With a map that outlives screens, that's easy to get wrong, so `MapHolder` tracks its own state and makes each call at most once:

```kotlin
internal fun resume() {
    start()
    if (!resumed) view?.onResume()
    resumed = true
}

internal fun stop() {
    pause()
    if (started) view?.onStop()
    started = false
}
```

**Side effects of keeping a screen alive, and how each was handled:**
- *Back button.* The Explore screen's "back deselects the flight" handler only works while Explore is the active tab, so back on Saved doesn't change Explore's selection.
- *Keyboard.* Leaving the tab used to remove the search fields, which closed the keyboard. Now they stay, so leaving the tab clears the focus.
- *Card height.* Only the active tab reports the selection card's height, which the snackbars use.
- *Camera.* A flight chosen in the list while the map is hidden doesn't move the hidden map. That matches the old behaviour, and iOS.
- *Activity recreation* (for example, switching dark mode). The map holds a reference to the activity, so it's destroyed and made again with the activity. The camera position is saved separately, so the view comes back where it was.

## 9. Results

Native heap after a forced garbage collection, emulator, debug build:

| Scenario | Before (Step 4.5 design) | First attempt (one map, detached) | Final (one map, attached) |
|---|---|---|---|
| After 6 switches to the map | 59 MB | 63 MB | 53.1 MB |
| After 12 | 100 MB | 88 MB | 53.4 MB |
| After 18 | 141 MB | 113 MB | 53.6 MB |
| Per trip to Saved and back, on the map | a new map each time, so presumably about 7 MB (not measured separately) | about 5 MB (53.5 → 82.8 MB over 6) | about 0.1 MB (54.1 → 54.7 MB over 7) |
| Java heap | flat | flat | flat |
| Threads | flat after the first map | flat | flat |

Also:
- StrictMode: no main-thread disk access across 18 map opens (before Step 6.1: three reads on every first open).
- Switching back to the map is instant: no new view, no style to download again.

## 10. How it was checked for regressions

After the fix, a scripted pass on the emulator (through `adb` and `uiautomator`) plus screenshots checked that:
- Tapping a pill opens its sheet, and choosing a flight draws the route above the selection card.
- Switching to the list keeps the selection and the card, and the list fully covers the map.
- Switching back shows the map exactly as it was.
- On the list, the hidden map isn't in the accessibility tree. On Saved, nothing of Explore is in it.
- Back on the Saved tab leaves the app, as before, without deselecting Explore's flight.
- The keyboard closes when switching from Explore to Saved.
- Dark mode, which recreates the activity, makes a new map with the same camera.
- No StrictMode reports and no crashes in the log. The only crashes ever logged were `uiautomator` itself timing out on the overloaded emulator, which was checked and isn't the app.

## 11. Tradeoffs and limits

- **Memory held on purpose.** Once the map has been opened, about 42 MB of native memory (53 − 11) stays in use while the list or Saved is shown. It doesn't grow with switching or with the amount of data, so it's a fixed cost in exchange for removing an unbounded one.
- **The Explore screen stays alive under Saved.** It keeps being measured when the data changes. That's cheap here, but it's a structural rule worth knowing: the 6.3 restyle mustn't put the map back inside something that removes it, like a `Crossfade` or a tab that leaves composition. CLAUDE.md says so.
- **Measured on the emulator, not a phone.** The emulator ran with the Mac's GPU through a translation layer. The leak could be in MapLibre's Vulkan code or in that graphics layer, and the fix avoids both. On a real phone the per-switch leak might be smaller or larger, but keeping one attached map can't make it worse.
- **The root cause inside MapLibre isn't pinned down.** The app avoids triggering it. Finding the exact native allocation would need a native heap profiler.
- **Debug build.** The numbers are from a debug build, but the leak is in native code, which a release build doesn't change.

## 12. What would come next

1. Profile native allocations with Android's `heapprofd` through Perfetto, attach the call stacks, and report the leak upstream to MapLibre Android.
2. Repeat the memory test on a physical Android phone, with both the Vulkan and the OpenGL builds of MapLibre.
3. Turn the memory loop into an automated instrumented test, so a future change that detaches the map fails CI rather than a manual check.

## 13. Talking about it in an interview

**The 30-second version.** During a performance pass on my Flight Explorer app, I found that every time the user switched to the map on Android, about 7 MB of native memory leaked: 11 MB grew to 141 MB in 18 switches. I built a repeatable measurement, used A/B builds to show the leak was older than my latest change, and narrowed it to MapLibre leaking whenever its map view is created or detached from the window. A first fix (reuse one map) only cut it by 40%. A Home-button experiment showed that detaching, not surface recreation, was the trigger. So the final design keeps one map permanently attached, hidden and paused under the list. Memory went flat at 53 MB, and switching back became instant.

**Questions you might get, with answers:**

- *How did you know it was a leak and not a cache?* I forced a garbage collection before every reading and ran 18 switches. A cache levels off; this grew in a straight line, about 7 MB per switch, with no sign of stopping.
- *How did you know it was native, not your Kotlin code?* I split the memory reading: the Java heap stayed at about 15 MB, the view count and thread count stayed flat, and only the native heap grew. The app's big native user is MapLibre.
- *How did you rule out your own recent change?* I built the code from before that change and ran the same script. It leaked the same way.
- *Why didn't the first fix work?* It kept one map but still removed it from the window when hidden. A Home-button test, which rebuilds the surface but keeps the view attached, leaked nothing. That isolated detaching as the trigger.
- *What did the fix cost?* About 42 MB held once the map has been opened, plus a rule that the map must never leave the window. In exchange, memory no longer grows, and switching is instant.
- *What was hard about keeping a screen alive?* Everything that used to happen "for free" when the screen was removed: the back button, the keyboard, accessibility, touch handling and the card's height. Each needed an explicit rule.
- *What would you do differently?* Write the memory loop as an automated test from the start, and profile native allocations to report the bug upstream.
- *What did you learn about measuring?* Check the test bench first: a headless emulator renders in software, and a heavy tool like a heap dump can disturb what you're measuring. Change one thing at a time, and compare each run with itself.

## 14. Files and commits

- `androidApp/.../ui/ExplorerMap.kt`: `MapHolder`, `rememberMapHolder`, the background start-up, and the map's visibility and lifecycle handling.
- `androidApp/.../ui/ExplorerApp.kt`: the map stays under the list once opened; Explore stays composed but unplaced while Saved is shown; back, focus and card-height rules.
- `bed22f2`: main-thread disk reads moved off the main thread; leak found and measured.
- `623485d`: D31, one map, never detached.
- Records: SUBMISSION_NOTES §2 (D31), §5 and §7; ARCHITECTURE §9 and §11; TEST_PLAN Q24; REQUIREMENTS PERF-4.
