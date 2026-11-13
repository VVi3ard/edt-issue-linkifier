package com.github.vvi3ard.dt.linkifier.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.ExtendedModifyListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.github.vvi3ard.dt.linkifier.model.ActivationMode;
import com.github.vvi3ard.dt.linkifier.model.LinkRange;
import com.github.vvi3ard.dt.linkifier.model.RuleManager;
import com.github.vvi3ard.dt.linkifier.model.RuleManager.ActiveRule;
import com.github.vvi3ard.dt.linkifier.model.ScopeRegistry;

/**
 * Attaches link detection, styling, and click interaction to SWT StyledText widgets (hovers, Blame, Git panels).
 */
public class StyledTextLinkifier {

    private static final String KEY_HOOKED = "LINKIFIER_HOOKED";
    private static final String KEY_RANGES = "LINKIFIER_RANGES";

    private static final Map<StyledText, Boolean> activeWidgets = Collections.synchronizedMap(new WeakHashMap<>());

    public static void hook(final StyledText styledText) {
        if (styledText == null || styledText.isDisposed()) {
            return;
        }

        if (Boolean.TRUE.equals(styledText.getData(KEY_HOOKED))) {
            applyLinks(styledText);
            return;
        }

        styledText.setData(KEY_HOOKED, Boolean.TRUE);
        activeWidgets.put(styledText, Boolean.TRUE);

        applyLinks(styledText);

        styledText.addExtendedModifyListener(new ExtendedModifyListener() {
            @Override
            public void modifyText(ExtendedModifyEvent event) {
                if (!styledText.isDisposed()) {
                    applyLinks(styledText);
                }
            }
        });

        styledText.addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                if (styledText.isDisposed()) {
                    return;
                }

                LinkRange link = findLinkAtPoint(styledText, e.x, e.y);
                if (link != null) {
                    Cursor handCursor = styledText.getDisplay().getSystemCursor(SWT.CURSOR_HAND);
                    styledText.setCursor(handCursor);
                    styledText.setToolTipText(link.getUrl());
                } else {
                    styledText.setCursor(null);
                    styledText.setToolTipText(null);
                }
            }
        });

        styledText.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                if (styledText.isDisposed() || e.button != 1) { // Left click only
                    return;
                }

                LinkRange link = findLinkAtPoint(styledText, e.x, e.y);
                if (link == null || link.getUrl() == null || link.getUrl().isEmpty()) {
                    return;
                }

                Shell shell = styledText.getShell();
                boolean isPopup = shell != null && !shell.isDisposed() && GlobalHoverListener.isPopupShell(shell);
                String scopeId = isPopup ? resolveHoverScope(styledText.getText()) : ScopeRegistry.SCOPE_GIT_HISTORY;

                ActivationMode mode = link.getRule().getScopeMode(scopeId);
                if (mode == ActivationMode.CTRL_CLICK) {
                    boolean ctrlPressed = (e.stateMask & SWT.MOD1) != 0 || (e.stateMask & SWT.CTRL) != 0;
                    if (!ctrlPressed) {
                        return;
                    }
                }

                final Display display = styledText.getDisplay();
                final String targetUrl = link.getUrl();
                final Shell popupShell = isPopup ? shell : null;

                display.asyncExec(() -> {
                    try {
                        if (popupShell != null && !popupShell.isDisposed()) {
                            popupShell.setVisible(false);
                            popupShell.dispose();
                        }
                        Program.launch(targetUrl);
                    } catch (Exception ex) {
                        System.err.println("[EDT-Linkifier] Failed to launch URL: " + targetUrl + " (" + ex.getMessage() + ")");
                    }
                });
            }
        });

        styledText.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                activeWidgets.remove(styledText);
            }
        });
    }

    public static void applyLinks(final StyledText styledText) {
        if (styledText == null || styledText.isDisposed()) {
            return;
        }

        String text = styledText.getText();
        if (text == null || text.isEmpty()) {
            styledText.setData(KEY_RANGES, Collections.emptyList());
            return;
        }

        Shell shell = styledText.getShell();
        boolean isPopup = shell != null && !shell.isDisposed() && GlobalHoverListener.isPopupShell(shell);
        String scopeId = isPopup ? resolveHoverScope(text) : ScopeRegistry.SCOPE_GIT_HISTORY;

        List<ActiveRule> activeRules = RuleManager.getRulesForHover(scopeId);
        if (activeRules.isEmpty()) {
            styledText.setData(KEY_RANGES, Collections.emptyList());
            return;
        }

        // Detect links regardless of Ctrl state so underline styling can be presented
        List<LinkRange> ranges = RuleManager.findLinks(text, activeRules, true);
        styledText.setData(KEY_RANGES, ranges);

        if (ranges.isEmpty()) {
            return;
        }

        Color linkColor = JFaceColors.getHyperlinkText(styledText.getDisplay());

        for (LinkRange range : ranges) {
            try {
                int start = range.getStart();
                int length = range.getLength();

                if (start + length > text.length()) {
                    continue;
                }

                StyleRange[] existing = styledText.getStyleRanges(start, length);
                if (existing != null && existing.length > 0) {
                    for (StyleRange sr : existing) {
                        sr.underline = true;
                        sr.underlineStyle = SWT.UNDERLINE_LINK;
                        sr.foreground = linkColor;
                        styledText.setStyleRange(sr);
                    }
                } else {
                    StyleRange sr = new StyleRange();
                    sr.start = start;
                    sr.length = length;
                    sr.underline = true;
                    sr.underlineStyle = SWT.UNDERLINE_LINK;
                    sr.foreground = linkColor;
                    styledText.setStyleRange(sr);
                }
            } catch (Exception ex) {
                // Ignore any out-of-bounds style range racing conditions
            }
        }
    }

    private static String resolveHoverScope(String text) {
        if (text != null && (text.contains("diff --git") || text.contains("@@ -") || text.startsWith("+++") || text.startsWith("---"))) {
            return ScopeRegistry.SCOPE_HOVER_DIFF;
        }
        return ScopeRegistry.SCOPE_HOVER_BLAME;
    }

    private static LinkRange findLinkAtPoint(StyledText styledText, int x, int y) {
        try {
            int offset = styledText.getOffsetAtLocation(new Point(x, y));
            if (offset < 0) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<LinkRange> ranges = (List<LinkRange>) styledText.getData(KEY_RANGES);
            if (ranges == null || ranges.isEmpty()) {
                return null;
            }

            for (LinkRange r : ranges) {
                if (r.containsOffset(offset)) {
                    return r;
                }
            }
        } catch (IllegalArgumentException e) {
            // Mouse is not over any character glyph
        }
        return null;
    }

    public static void refreshAll() {
        synchronized (activeWidgets) {
            for (StyledText st : activeWidgets.keySet()) {
                if (st != null && !st.isDisposed()) {
                    st.getDisplay().asyncExec(() -> applyLinks(st));
                }
            }
        }
    }
}
