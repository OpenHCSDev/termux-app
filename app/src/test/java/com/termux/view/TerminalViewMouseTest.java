package com.termux.view;

import android.view.InputDevice;
import android.view.MotionEvent;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;

import static org.junit.Assert.*;

/** Stock scrolling/Scroller units and the independently retained long-press policy. */
public class TerminalViewMouseTest extends TerminalViewTestCase {

    private void scroll(float columns, float rows) {
        MotionEvent event = event(1, MotionEvent.ACTION_MOVE, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onScroll(event,
            columns * view.mRenderer.mFontWidth, rows * view.mRenderer.mFontLineSpacing);
        event.recycle();
    }

    @Test
    public void fractionalVerticalMotionAccumulatesThroughTheStockEncoder() {
        input("\033[?1000h\033[?1006h");
        scroll(0, 0.4f);
        assertEquals("", output.text());
        scroll(0, 0.7f);
        assertEquals("\033[<65;3;4M", output.text());
    }

    @Test
    public void diagonalMotionRetainsItsVerticalDistance() {
        input("\033[?1000h\033[?1006h");
        scroll(5, 1);
        scroll(-5, 2);
        assertEquals("\033[<65;3;4M\033[<65;3;4M\033[<65;3;4M", output.text());
    }

    @Test
    public void negativeVerticalMotionUsesTheLegacyUpWheelEncoder() {
        input("\033[?1002h");
        scroll(0, -2.25f);
        assertEquals("\033[M`#$\033[M`#$", output.text());
    }

    @Test
    public void verticalGestureKeepsItsOriginalCell() {
        input("\033[?1000h\033[?1006h");
        scroll(0, -1);
        MotionEvent event = event(1, MotionEvent.ACTION_MOVE, 9.5f, 8.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.mGestureRecognizer.mListener.onScroll(event, 100, -view.mRenderer.mFontLineSpacing);
        event.recycle();
        assertEquals("\033[<64;3;4M\033[<64;3;4M", output.text());
    }

    @Test
    public void mouseDragStillSendsMotionRatherThanWheel() {
        input("\033[?1002h\033[?1006h");
        MotionEvent event = event(1, MotionEvent.ACTION_MOVE, 2.5f, 3.5f, InputDevice.SOURCE_MOUSE);
        view.mGestureRecognizer.mListener.onScroll(event, 100, 0);
        event.recycle();
        assertEquals("\033[<32;3;4M", output.text());
    }

    private void settleStockFling() {
        // Advance the native clock before draining the stock immediate-post loop.
        // This exercises its final displacement without a zero-time scheduler spin.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30));
        advance(1);
    }

    private void assertTrackedVerticalMomentum(int mode) {
        input("\033[?" + mode + "h\033[?1006h");
        scroll(0, 2);
        String initial = output.text();
        assertEquals("\033[<65;3;4M\033[<65;3;4M", initial);
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        try {
            view.mGestureRecognizer.mListener.onFling(up, 0, -20000);
            assertFalse(view.mScroller.isFinished());
            int stockExtent = view.mEmulator.mRows / 2;
            assertEquals(stockExtent, view.mScroller.getFinalY());
            settleStockFling();
            String continuation = output.text().substring(initial.length());
            String report = "\033[<65;3;4M";
            assertEquals("", continuation.replace(report, ""));
            assertEquals(stockExtent, continuation.length() / report.length());
            assertTrue(view.mScroller.isFinished());
            String settled = output.text();
            advance(1000);
            assertEquals(settled, output.text());
        } finally {
            up.recycle();
        }
    }

    @Test
    public void tracked1000ReleaseUsesStockRowMomentum() { assertTrackedVerticalMomentum(1000); }

    @Test
    public void tracked1002ReleaseUsesStockRowMomentum() { assertTrackedVerticalMomentum(1002); }

    @Test
    public void reverseFlickUsesTheStockNegativeRowExtent() {
        input("\033[?1000h\033[?1006h");
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        try {
            view.mGestureRecognizer.mListener.onFling(up, 0, 20000);
            int stockExtent = view.mEmulator.mRows / 2;
            assertEquals(-stockExtent, view.mScroller.getFinalY());
            settleStockFling();
            String report = "\033[<64;3;4M";
            assertEquals("", output.text().replace(report, ""));
            assertEquals(stockExtent, output.text().length() / report.length());
        } finally {
            up.recycle();
        }
    }

    @Test
    public void activeFlingKeepsItsExistingScrollerDestination() {
        input("\033[?1000h\033[?1006h");
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        try {
            view.mGestureRecognizer.mListener.onFling(up, 0, -20000);
            int destination = view.mScroller.getFinalY();
            view.mGestureRecognizer.mListener.onFling(up, 0, 20000);
            assertEquals(destination, view.mScroller.getFinalY());
            settleStockFling();
        } finally {
            up.recycle();
        }
    }

    private void prepareScrollback() {
        for (int i = 0; i < 60; i++) input("row\r\n");
        view.onScreenUpdated();
        assertTrue(view.mEmulator.getScreen().getActiveTranscriptRows() > 0);
    }

    @Test
    public void trackingOffScrollbackRetainsStockFlingPhysics() {
        prepareScrollback();
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        try {
            view.mGestureRecognizer.mListener.onFling(up, 0, 2000);
            assertFalse(view.mScroller.isFinished());
            int destination = view.mScroller.getFinalY();
            assertTrue(destination < 0);
            settleStockFling();
            assertTrue(view.mScroller.isFinished());
            assertEquals(destination, view.getTopRow());
            assertEquals("", output.text());
        } finally {
            up.recycle();
        }
    }

    @Test
    public void enablingTrackingAbortsAnUntrackedFlingWithoutWheelReports() {
        prepareScrollback();
        MotionEvent up = event(1, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        try {
            view.mGestureRecognizer.mListener.onFling(up, 0, 2000);
            input("\033[?1000h\033[?1006h");
            advance(1);
            assertTrue(view.mScroller.isFinished());
            assertEquals("", output.text());
        } finally {
            up.recycle();
        }
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
