# Stock scrolling and application-declared keyboard intent

Scrolling uses upstream Termux's gesture and Scroller implementation. `onScroll`,
`onFling`, `onDown`, `doScroll` and `onTouchEvent` are restored byte-for-byte from
upstream8629e632. Vertical movement keeps its fractional row remainder and the
existing wheel encoder; tracked flings keep Scroller's original row units and
half-screen destination. Untracked scrollback keeps its original physics.

The earlier fork removed tracked momentum, then introduced a pixel conversion,
500ms cap, one-report-per-frame limit and axis lock. Those policies produced an
unusable phone result despite passing their own controls. They are removed,
including horizontal-wheel behavior. Native selection and the opt-in RIGHT
long press remain (see `mouse-long-press.md`).

## Application-declared input bounds

An application advertises its focused, visible editable content rectangle:

```text
ESC ] 777 ; input-region ; x ; y ; width ; height ESC \
```

The actual control has no spaces. Coordinates and dimensions are decimal terminal
cells, zero-based, with exclusive right/bottom edges, within the terminal size.
`0;0;0;0` clears it. BEL also terminates OSC. Malformed controls are logged and clear
the previous advertisement. Resize, reset, buffer and tracking-mode changes
invalidate the input context and its bounds.

A short tracked finger tap records its cell. Existing bounds or an application
reply can request the keyboard once when that cell is inside the advertised
rectangle, for at most three seconds from the event. Android touch dispatch
retires the old intent on a new touch or cancel without changing the stock
gesture methods. Selection, window loss, detach, session and input-context
changes also retire it. Unsolicited bounds, visible cursors, hardware mouse taps,
long presses and scrolling do not create a keyboard request.

The application owns input identity and geometry, independently of cursor
appearance. The paired Textual sender includes normal/command-mode editors and
withdraws read-only targets. A running Toad must be relaunched on a build with
that sender; installing an APK alone cannot provide the missing application
data. Applications without this protocol retain the explicit keyboard toggle.
Tracking-off shell taps retain their existing client behavior.

The view controls exercise real emulator decoding, touch dispatch and Scroller
row displacement. They are not physical Android IME or phone smoothness evidence.
The paired sender and a real tap/flick on the phone are the acceptance check.
