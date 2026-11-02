package com.termux.view;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.junit.Test;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

/** Real terminal input bounds and touch intent; Android IME display needs the phone. */
public class TerminalViewKeyboardTest extends TerminalViewTestCase {
    private void trackingTap() {
        input("\033[?1000h\033[?25l");
        tap(InputDevice.SOURCE_TOUCHSCREEN);
    }

    private void inputResponse() {
        input("\033]777;input-region;1;2;8;3\033\\");
        view.onScreenUpdated();
    }

    @Test
    public void declaredInputAfterTrackingTapRequestsKeyboardOnce() {
        trackingTap();
        assertEquals(0, client.keyboardRequests);
        inputResponse();
        assertEquals(1, client.keyboardRequests);
        inputResponse();
        view.onScreenUpdated();
        assertEquals(1, client.keyboardRequests);
    }

    @Test
    public void unsolicitedInputBoundsDoNotRequestKeyboard() {
        input("\033[?1000h");
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void visibleCursorAndScreenRepaintsAreNotInputFocus() {
        trackingTap();
        input("\033[4;3H\033[?25h");
        view.onScreenUpdated();
        view.mEmulator.setCursorBlinkState(false);
        view.mEmulator.setCursorBlinkState(true);
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void inputElsewhereOnTheCursorRowDoesNotAuthorizeTranscriptTap() {
        input("\033[?1000h\033[4;3H\033[?25h");
        input("\033]777;input-region;10;3;5;1\033\\");
        tap(InputDevice.SOURCE_TOUCHSCREEN);
        view.onScreenUpdated();
        advance(ViewConfiguration.getDoubleTapTimeout() + 1);
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void knownInputBoundsRequestImmediatelyAndOnlyOncePerTap() {
        input("\033[?1000h");
        inputResponse();
        tap(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals(1, client.keyboardRequests);
        view.onScreenUpdated();
        assertEquals(1, client.keyboardRequests);
        tap(InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals(2, client.keyboardRequests);
    }

    @Test
    public void rightAndBottomBoundsAreExclusive() {
        input("\033[?1000h");
        inputResponse();
        tapAt(9.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        tapAt(2.5f, 5.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
        tapAt(8.5f, 4.5f, InputDevice.SOURCE_TOUCHSCREEN);
        assertEquals(1, client.keyboardRequests);
    }

    @Test
    public void clearedBoundsDoNotAuthorizeTap() {
        input("\033[?1000h");
        inputResponse();
        input("\033]777;input-region;0;0;0;0\033\\");
        tap(InputDevice.SOURCE_TOUCHSCREEN);
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void malformedBoundsClearThePreviousAdvertisement() {
        input("\033[?1000h");
        String[] malformed = {
            "input-region;1;2;999999;3", "input-region;-1;2;8;3",
            "input-region;1;2;8", "input-region;1;2;8;no",
            "input-region;1;2;2147483648;3", "input-region;1;2;8;3;extra"
        };
        for (String parameter : malformed) {
            touch(SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
            inputResponse();
            input("\033]777;" + parameter + "\033\\");
            tap(InputDevice.SOURCE_TOUCHSCREEN);
            view.onScreenUpdated();
            assertEquals(parameter, 0, client.keyboardRequests);
        }
    }

    @Test
    public void incompleteControlIsNotAnAdvertisement() {
        trackingTap();
        input("\033]777;input-region;1;2;8;3");
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
        input("\033\\");
        view.onScreenUpdated();
        assertEquals(1, client.keyboardRequests);
    }

    @Test
    public void delayedTapDeliveryDoesNotCreateFreshKeyboardIntent() {
        input("\033[?1000h");
        inputResponse();
        MotionEvent up = event(SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
            2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        ShadowSystemClock.advanceBy(Duration.ofMillis(TerminalView.MOUSE_TAP_KEYBOARD_TIMEOUT_MILLIS + 1));
        view.mGestureRecognizer.mListener.onUp(up);
        up.recycle();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void responseAfterRecentTapWindowHasExpiredIsIgnored() {
        trackingTap();
        advance(TerminalView.MOUSE_TAP_KEYBOARD_TIMEOUT_MILLIS + 1);
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void newDownInvalidatesEarlierTap() {
        trackingTap();
        touch(SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void cancelInvalidatesEarlierTap() {
        trackingTap();
        touch(SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void lostWindowFocusInvalidatesEarlierTap() {
        trackingTap();
        view.onWindowFocusChanged(false);
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void detachInvalidatesEarlierTap() {
        trackingTap();
        view.onDetachedFromWindow();
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void trackingOffAndOnInOneReadRetiresEarlierTap() {
        trackingTap();
        input("\033[?1000l\033[?1000h");
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void resizeRetiresEarlierTapAndBounds() {
        trackingTap();
        view.mEmulator.resize(81, 24, 8, 16);
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void bufferSwitchRetiresEarlierTap() {
        trackingTap();
        input("\033[?1049h");
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void trackingOffTapKeepsExistingClientCallback() {
        tap(InputDevice.SOURCE_TOUCHSCREEN);
        advance(ViewConfiguration.getDoubleTapTimeout() + 1);
        assertEquals(1, client.taps);
        inputResponse();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void mouseDeviceTapDoesNotAuthorizeKeyboard() {
        input("\033[?1000h");
        inputResponse();
        tap(InputDevice.SOURCE_MOUSE);
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void longPressDoesNotAuthorizeKeyboardOnRelease() {
        input("\033[?1000h");
        inputResponse();
        longPress(InputDevice.SOURCE_TOUCHSCREEN);
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
    }

    @Test
    public void fingerScrollDoesNotAuthorizeKeyboardOnRelease() {
        input("\033[?1000h");
        inputResponse();
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, 2.5f, 15.5f, InputDevice.SOURCE_TOUCHSCREEN);
        touch(down, MotionEvent.ACTION_MOVE, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        touch(down, MotionEvent.ACTION_UP, 2.5f, 3.5f, InputDevice.SOURCE_TOUCHSCREEN);
        view.onScreenUpdated();
        assertEquals(0, client.keyboardRequests);
    }
}
