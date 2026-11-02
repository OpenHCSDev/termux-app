package com.termux.view;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

public class TerminalViewMouseTest extends TerminalViewTestCase {

    private void scroll(float columns, float rows) {
        MotionEvent event = event(1, MotionEvent.ACTION_MOVE, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onScroll(event,
            columns * view.mRenderer.mFontWidth, rows * view.mRenderer.mFontLineSpacing);
        event.recycle();
    }

    @Test
    public void horizontalFractionalMotionUsesRightWheelAndLocksAxis() {
        input("\033[?1000h\033[?1006h");
        scroll(0.4f, 0);
        assertEquals("", output.text());
        scroll(0.7f, 0);
        assertEquals("\033[<67;3;4M", output.text());
        scroll(1, 4);
        assertEquals("\033[<67;3;4M\033[<67;3;4M", output.text());
    }

    @Test
    public void negativeMotionUsesLeftWheelThroughLegacyEncoder() {
        input("\033[?1002h");
        scroll(-2.25f, 0);
        assertEquals("\033[Mb#$\033[Mb#$", output.text());
    }

    @Test
    public void verticalGestureKeepsUpWheelOriginAndAxis() {
        input("\033[?1000h\033[?1006h");
        scroll(0, -1);
        MotionEvent event = event(1, MotionEvent.ACTION_MOVE, 9.5f, 8.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onScroll(event, 100, -view.mRenderer.mFontLineSpacing);
        event.recycle();
        assertEquals("\033[<64;3;4M\033[<64;3;4M", output.text());
    }

    @Test
    public void trackingOffDoesNotSendHorizontalWheel() {
        scroll(4, 0);
        assertEquals("", output.text());
        assertEquals(0, view.getTopRow());
    }

    @Test
    public void mouseDragStillSendsMotionRatherThanWheel() {
        input("\033[?1002h\033[?1006h");
        MotionEvent event = event(1, MotionEvent.ACTION_MOVE, 2.5f, 3.5f, InputDevice.SOURCE_MOUSE);
        view.mGestureRecognizer.mListener.onScroll(event, 100, 0);
        event.recycle();
        assertEquals("\033[<32;3;4M", output.text());
    }

    private void fling(float velocityX, float velocityY) {
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onFling(up, velocityX, velocityY);
        // Android owns/recycles the source event; the continuation owns its copy.
        up.setLocation(1000, 1000);
        up.recycle();
    }

    @Test
    public void horizontalReleaseGlidesOnItsLockedAxis() {
        input("\033[?1000h\033[?1006h");
        scroll(2, 0);
        String initial = output.text();
        fling(-20000, 20000);
        assertFalse(view.mScroller.isFinished());
        assertEquals(initial, output.text());
        advance(TerminalView.MAX_MOUSE_FLING_MILLIS + 32);
        String continuation = output.text().substring(initial.length());
        String report = "\033[<67;3;4M";
        assertFalse(continuation.isEmpty());
        assertEquals("", continuation.replace(report, ""));
        assertTrue(continuation.length() / report.length() <= view.mEmulator.mColumns);
        assertTrue(view.mScroller.isFinished());
    }

    private void assertTrackedVerticalMomentum(int mode) {
        input("\033[?" + mode + "h\033[?1006h");
        scroll(0, 2);
        String initial = "\033[<65;3;4M\033[<65;3;4M";
        assertEquals(initial, output.text());
        fling(0, -20000);
        assertFalse(view.mScroller.isFinished());
        assertEquals(initial, output.text());
        advance(TerminalView.MAX_MOUSE_FLING_MILLIS + 32);
        String continuation = output.text().substring(initial.length());
        String report = "\033[<65;3;4M";
        assertFalse(continuation.isEmpty());
        assertEquals("", continuation.replace(report, ""));
        assertTrue(continuation.length() / report.length() <= view.mEmulator.mRows);
        assertTrue(view.mScroller.isFinished());
        String settled = output.text();
        advance(1000);
        assertEquals(settled, output.text());
    }

    @Test
    public void tracked1000VerticalReleaseHasBoundedMomentum() { assertTrackedVerticalMomentum(1000); }

    @Test
    public void tracked1002VerticalReleaseHasBoundedMomentum() { assertTrackedVerticalMomentum(1002); }

    @Test
    public void newDownStopsMomentumBeforeAnotherWheelReport() {
        input("\033[?1000h\033[?1006h");
        fling(0, -20000);
        touch(SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        advance(1000);
        assertTrue(view.mScroller.isFinished());
        assertEquals("", output.text());
    }

    @Test
    public void cancellationStopsMomentumBeforeAnotherWheelReport() {
        input("\033[?1000h\033[?1006h");
        fling(0, -20000);
        touch(SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        advance(1000);
        assertTrue(view.mScroller.isFinished());
        assertEquals("", output.text());
    }

    @Test
    public void trackingRoundTripInOneReadRetiresOldMomentum() {
        input("\033[?1000h\033[?1006h");
        fling(0, -20000);
        input("\033[?1000l\033[?1000h");
        advance(1000);
        assertTrue(view.mScroller.isFinished());
        assertEquals("", output.text());
    }

    @Test
    public void bufferSwitchRetiresOldMomentum() {
        input("\033[?1000h\033[?1006h");
        fling(0, -20000);
        input("\033[?1049h");
        advance(1000);
        assertTrue(view.mScroller.isFinished());
        assertEquals("", output.text());
    }

    @Test
    public void resizeRetiresOldMomentum() {
        input("\033[?1000h\033[?1006h");
        fling(0, -20000);
        view.mEmulator.resize(81, 24, 8, 16);
        advance(1000);
        assertTrue(view.mScroller.isFinished());
        assertEquals("", output.text());
    }

    private void prepareScrollback() {
        for (int i = 0; i < 60; i++) input("row\r\n");
        view.onScreenUpdated();
        assertTrue(view.mEmulator.getScreen().getActiveTranscriptRows() > 0);
    }

    @Test
    public void trackingOffScrollbackStillStartsFling() {
        prepareScrollback();
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onFling(up, 0, 2000);
        up.recycle();
        assertFalse(view.mScroller.isFinished());
        advance(1000);
        assertTrue(view.mScroller.isFinished());
        assertTrue(view.getTopRow() < 0);
        assertEquals("", output.text());
    }

    @Test
    public void enablingTrackingAbortsAnUntrackedFlingWithoutWheelReports() {
        prepareScrollback();
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onFling(up, 0, 2000);
        up.recycle();
        input("\033[?1000h\033[?1006h");
        advance(32);
        assertTrue(view.mScroller.isFinished());
        assertEquals("", output.text());
    }

    @Test
    public void nextDownClearsCancelledGestureFractionAndAxis() {
        input("\033[?1000h\033[?1006h");
        scroll(0.8f, 0);
        MotionEvent cancel = event(1, MotionEvent.ACTION_CANCEL, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onTouchEvent(cancel);
        cancel.recycle();
        MotionEvent down = event(2, MotionEvent.ACTION_DOWN, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onTouchEvent(down);
        down.recycle();
        scroll(0.3f, 0);
        assertEquals("", output.text());
    }

    @Test
    public void actualTouchSwipeDoesNotAlsoProduceVerticalWheelOrLeftTap() {
        input("\033[?1000h\033[?1006h");
        long time = SystemClock.uptimeMillis();
        MotionEvent down = event(time, MotionEvent.ACTION_DOWN, 30.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onTouchEvent(down);
        down.recycle();
        MotionEvent move = event(time, MotionEvent.ACTION_MOVE, 10.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onTouchEvent(move);
        move.recycle();
        MotionEvent up = event(time, MotionEvent.ACTION_UP, 10.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onTouchEvent(up);
        up.recycle();
        assertFalse(output.text().isEmpty());
        assertFalse(output.text().contains("\033[<0;"));
        assertFalse(output.text().contains("\033[<64;"));
        assertFalse(output.text().contains("\033[<65;"));
        assertTrue(output.text().contains("\033[<67;"));
    }

    @Test
    public void sgrLongPressReportsRightPressAndReleaseWithoutLeftTap() {
        client.rightClickOnLongPress = true;
        input("\033[?1000h\033[?1006h");
        longPress(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals("\033[<2;3;4M\033[<2;3;4m", output.text());
        assertEquals(0, client.longPresses);
        assertEquals(0, client.taps);
        assertFalse(view.isSelectingText());
    }

    @Test
    public void legacyLongPressUsesExistingReleaseEncoding() {
        client.rightClickOnLongPress = true;
        input("\033[?1002h");
        longPress(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals("\033[M\"#$\033[M##$", output.text());
        assertEquals(0, client.longPresses);
    }

    @Test
    public void trackingOffKeepsClientLongPressAndDoesNotEmitMouseInput() {
        client.rightClickOnLongPress = true;
        longPress(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals("", output.text());
        assertEquals(1, client.longPresses);
        assertEquals(0, client.taps);
    }

    @Test
    public void mouseDeviceLongPressKeepsClientCallbackAndLeftButtonPath() {
        client.rightClickOnLongPress = true;
        input("\033[?1000h\033[?1006h");
        longPress(InputDevice.SOURCE_MOUSE);
        assertEquals("\033[<0;3;4M\033[<0;3;4m", output.text());
        assertEquals(1, client.longPresses);
    }

    @Test
    public void optedIn1002SgrLongPressAlsoUsesRightButton() {
        client.rightClickOnLongPress = true;
        input("\033[?1002h\033[?1006h");
        longPress(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals("\033[<2;3;4M\033[<2;3;4m", output.text());
        assertFalse(view.isSelectingText());
    }

    private void assertDefaultSelection(int mode) {
        client.handleLongPress = false;
        input("\033[?" + mode + "h\033[?1006h");
        longPress(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals("", output.text());
        assertEquals(1, client.longPresses);
        assertTrue(view.isSelectingText());
    }

    @Test
    public void default1000SgrLongPressSelectsText() { assertDefaultSelection(1000); }

    @Test
    public void default1002SgrLongPressSelectsText() { assertDefaultSelection(1002); }

    @Test
    public void rightClickPropertyIsExplicitlyOptIn() {
        String key = TermuxPropertyConstants.KEY_MOUSE_RIGHT_CLICK_ON_LONG_PRESS;
        assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST.contains(key));
        assertEquals(Boolean.FALSE, TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            RuntimeEnvironment.getApplication(), key, null));
        assertEquals(Boolean.FALSE, TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            RuntimeEnvironment.getApplication(), key, "false"));
        assertEquals(Boolean.TRUE, TermuxSharedProperties.getInternalTermuxPropertyValueFromValue(
            RuntimeEnvironment.getApplication(), key, "true"));
    }

}
