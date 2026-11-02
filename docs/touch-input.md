# Tracked touch scrolling and keyboard intent

Finger flicks in mouse-tracking applications retain momentum. Both axes use the
same axis owner and mouse encoder as the finger drag. The continuation runs on
Android display frames, sends at most one wheel report per frame, lasts at most
500 ms, and contributes no more wheel steps than one terminal viewport on its
axis. It drops any remaining distance when it settles; it does not flush a tail
of queued reports. A new touch, cancellation, selection, window loss or detach
stops it. Session, tracking-mode, screen-buffer and size changes retire the old
input context. Untracked terminal scrollback keeps its existing fling physics.

The original release patch incorrectly deleted tracked momentum. It also treated
a visible terminal cursor after any tap as permission to open the keyboard. A
cursor can remain visible in an input while the user taps somewhere else.

## Application-declared input bounds

An application can advertise its focused, visible text-input content rectangle:

```text
ESC ] 777 ; input-region ; x ; y ; width ; height ESC \
```

Coordinates and dimensions are decimal terminal cells. The origin is zero-based;
right and bottom edges are exclusive. The rectangle must fit the current terminal
size. `0;0;0;0` clears it. BEL also terminates OSC, as for other terminal controls.
Malformed controls are logged and clear the previous advertisement. Resize,
terminal reset, screen-buffer changes and mouse-tracking changes invalidate it.

A short finger tap in a tracked terminal requests the keyboard once if its cell
is inside the advertised rectangle. If the tap changes focus, the application
may advertise the newly focused input in its reply; the same hit test can consume
that tap for up to three seconds. Another touch, cancellation, selection, window
loss, detach or input-context change retires the request. An unsolicited region
update, visible cursor, hardware mouse event, long press or scroll does not open
the keyboard.

The application owns widget identity and geometry. The terminal never guesses
an input box from cursor visibility, cursor shape or a matching row. Applications
that do not advertise input bounds keep the explicit keyboard toggle; ordinary
tracking-off shell taps retain the existing client behavior. Native selection and
the opt-in RIGHT long press are unchanged (see `mouse-long-press.md`).

The accompanying view controls exercise actual emulator decoding and native
view/gesture events. They test request intent, not physical Android IME display
or remote-widget integration; those require the paired sender and phone run.
