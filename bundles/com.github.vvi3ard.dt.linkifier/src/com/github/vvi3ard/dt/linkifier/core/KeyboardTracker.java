package com.github.vvi3ard.dt.linkifier.core;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;

/**
 * Tracks the live keyboard state (specifically Ctrl / MOD1 key) with zero overhead.
 */
public class KeyboardTracker {

    private static volatile boolean ctrlPressed = false;
    private static volatile boolean initialized = false;

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }

        Listener filter = event -> {
            switch (event.type) {
                case SWT.KeyDown:
                    if (event.keyCode == SWT.CTRL || (event.stateMask & SWT.MOD1) != 0) {
                        ctrlPressed = true;
                    }
                    break;
                case SWT.KeyUp:
                    if (event.keyCode == SWT.CTRL) {
                        ctrlPressed = false;
                    }
                    break;
                case SWT.MouseMove:
                    boolean hasMod = (event.stateMask & SWT.MOD1) != 0 || (event.stateMask & SWT.CTRL) != 0;
                    if (hasMod) {
                        ctrlPressed = true;
                    } else if (ctrlPressed && event.keyCode == 0) {
                        // Mouse moved without modifier, sync state
                        ctrlPressed = false;
                    }
                    break;
                case SWT.FocusOut:
                case SWT.Deactivate:
                    ctrlPressed = false;
                    break;
                default:
                    break;
            }
        };

        display.addFilter(SWT.KeyDown, filter);
        display.addFilter(SWT.KeyUp, filter);
        display.addFilter(SWT.MouseMove, filter);
        display.addFilter(SWT.FocusOut, filter);
        display.addFilter(SWT.Deactivate, filter);

        initialized = true;
    }

    public static boolean isCtrlPressed() {
        return ctrlPressed;
    }

    public static int getCurrentStateMask() {
        return ctrlPressed ? SWT.MOD1 : SWT.NONE;
    }
}
