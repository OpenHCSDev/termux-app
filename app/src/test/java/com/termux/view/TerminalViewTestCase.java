package com.termux.view;

import android.app.Activity;
import android.app.Application;
import android.content.res.XmlResourceParser;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.termux.R;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Shared real-view/recognizer/emulator fixture; no renderer or gesture mocks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public abstract class TerminalViewTestCase {
    protected final RecordingOutput output = new RecordingOutput();
    protected final RecordingClient client = new RecordingClient();
    protected TerminalView view;
    private ActivityController<Activity> activity;

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        // Use the actual app attributes: a null AttributeSet loses focusability and selection.
        try (XmlResourceParser layout = activity.get().getResources().getLayout(R.layout.activity_termux)) {
            while (layout.next() != XmlPullParser.END_DOCUMENT) {
                if (layout.getEventType() == XmlPullParser.START_TAG
                    && layout.getIdAttributeResourceValue(0) == R.id.terminal_view) {
                    view = new TerminalView(activity.get(), layout);
                    break;
                }
            }
        }
        assertNotNull("The app layout must declare the terminal view", view);
        assertTrue("Native selection requires the app's touch focusability", view.isFocusableInTouchMode());
        view.setTerminalViewClient(client);
        view.setTextSize(16);
        assertTrue("Gesture coordinates require real font width", view.mRenderer.mFontWidth > 0);
        assertTrue("Gesture coordinates require real line spacing", view.mRenderer.mFontLineSpacing > 0);
        view.mEmulator = new TerminalEmulator(output, 80, 24, 8, 16, null,
            new TermuxTerminalSessionClientBase());
        view.mGestureRecognizer.mListener.onDown(0, 0);
        activity.get().setContentView(view);
    }

    @After
    public void tearDown() {
        activity.pause().stop().destroy();
    }

    protected void input(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        view.mEmulator.append(bytes, bytes.length);
    }

    protected MotionEvent event(long down, int action, float column, float row, int source) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
            column * view.mRenderer.mFontWidth,
            view.mRenderer.mFontLineSpacingAndAscent + row * view.mRenderer.mFontLineSpacing, 0);
        event.setSource(source);
        return event;
    }

    protected void touch(long down, int action, float column, float row, int source) {
        MotionEvent event = event(down, action, column, row, source);
        view.onTouchEvent(event);
        event.recycle();
    }

    protected void tap(int source) {
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, 2.5f, 3.5f, source);
        touch(down, MotionEvent.ACTION_UP, 2.5f, 3.5f, source);
    }

    protected void longPress(int source) {
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, 2.5f, 3.5f, source);
        advance(ViewConfiguration.getTapTimeout() + ViewConfiguration.getLongPressTimeout() + 1);
        touch(down, MotionEvent.ACTION_UP, 2.5f, 3.5f, source);
    }

    protected void advance(long milliseconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS);
    }

    protected static final class RecordingClient extends TermuxTerminalViewClientBase {
        int longPresses;
        int taps;
        int keyboardRequests;
        boolean rightClickOnLongPress;
        boolean handleLongPress = true;
        @Override public boolean shouldSendRightClickOnLongPress() { return rightClickOnLongPress; }
        @Override public boolean onLongPress(MotionEvent event) { longPresses++; return handleLongPress; }
        @Override public void onSingleTapUp(MotionEvent event) { taps++; }
        @Override public void onRequestSoftKeyboard() { keyboardRequests++; }
    }

    protected static final class RecordingOutput extends TerminalOutput {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String text() { return new String(bytes.toByteArray(), StandardCharsets.UTF_8); }
        @Override public void write(byte[] data, int offset, int count) { bytes.write(data, offset, count); }
        @Override public void titleChanged(String oldTitle, String newTitle) { }
        @Override public void onCopyTextToClipboard(String text) { }
        @Override public void onPasteTextFromClipboard() { }
        @Override public void onBell() { }
        @Override public void onColorsChanged() { }
    }
}
