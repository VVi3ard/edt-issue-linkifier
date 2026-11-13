package com.github.vvi3ard.dt.linkifier.core;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

/**
 * Global SWT listener that monitors popup shells and hover controls
 * (such as Git Blame / Revisions hover) to attach linkifiers.
 * Strictly ignores the main workbench shell and code editors.
 */
public class GlobalHoverListener {

    private static Listener showListener;
    private static Display registeredDisplay;

    public static synchronized void install(final Display display) {
        if (display == null || display.isDisposed()) {
            return;
        }

        uninstall();
        registeredDisplay = display;

        showListener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (event.widget instanceof Shell) {
                    Shell shell = (Shell) event.widget;
                    if (isPopupShell(shell)) {
                        findAndHookStyledTexts(shell);
                    }
                }
            }
        };

        display.addFilter(SWT.Show, showListener);

        // Process any currently open popup shells
        for (Shell shell : display.getShells()) {
            if (isPopupShell(shell)) {
                findAndHookStyledTexts(shell);
            }
        }
    }

    public static synchronized void uninstall() {
        if (registeredDisplay != null && !registeredDisplay.isDisposed()) {
            if (showListener != null) {
                registeredDisplay.removeFilter(SWT.Show, showListener);
                showListener = null;
            }
        }
        registeredDisplay = null;
    }

    public static boolean isPopupShell(Shell shell) {
        if (shell == null || shell.isDisposed()) {
            return false;
        }
        int style = shell.getStyle();
        // Hover/information controls are floating popup shells:
        // Must have TOOL or ON_TOP or NO_TRIM, and MUST NOT be a standard trimmed workbench window
        boolean hasPopupStyle = (style & (SWT.TOOL | SWT.ON_TOP)) != 0 || (style & SWT.NO_TRIM) != 0;
        boolean hasTrim = (style & (SWT.MIN | SWT.MAX)) != 0;
        return hasPopupStyle && !hasTrim;
    }

    private static void findAndHookStyledTexts(Widget widget) {
        if (widget == null || widget.isDisposed()) {
            return;
        }

        if (widget instanceof StyledText) {
            StyledTextLinkifier.hook((StyledText) widget);
        } else if (widget instanceof Composite) {
            Composite composite = (Composite) widget;
            Control[] children = composite.getChildren();
            if (children != null) {
                for (Control child : children) {
                    findAndHookStyledTexts(child);
                }
            }
        }
    }
}
