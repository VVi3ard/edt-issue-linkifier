package com.github.vvi3ard.dt.linkifier.detector;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetectorExtension2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.github.vvi3ard.dt.linkifier.core.KeyboardTracker;
import com.github.vvi3ard.dt.linkifier.model.ActivationMode;
import com.github.vvi3ard.dt.linkifier.model.LinkRange;
import com.github.vvi3ard.dt.linkifier.model.RuleManager;
import com.github.vvi3ard.dt.linkifier.model.RuleManager.ActiveRule;
import com.github.vvi3ard.dt.linkifier.model.ScopeRegistry;

/**
 * High-performance Hyperlink detector matching configured patterns in text editors and Git viewers.
 * Routes each viewer to its appropriate scope (e.g. editor.ext.bsl, git.history, git.staging).
 * Implements IHyperlinkDetectorExtension2 for dynamic modifier-key state filtering.
 */
public class IssueHyperlinkDetector extends AbstractHyperlinkDetector implements IHyperlinkDetectorExtension2 {

    private static final String DATA_CACHED_EXT = "LINKIFIER_FILE_EXT";

    @Override
    public int getStateMask() {
        String scopeId = resolveCurrentScope();

        List<ActiveRule> rules = RuleManager.getRulesForScope(scopeId);
        if (rules.isEmpty()) {
            return -2; // Never matches active state mask -> skips detector execution completely
        }

        boolean hasAlways = false;
        boolean hasCtrl = false;
        for (ActiveRule ar : rules) {
            if (ar.getMode() == ActivationMode.ALWAYS) {
                hasAlways = true;
            } else if (ar.getMode() == ActivationMode.CTRL_CLICK) {
                hasCtrl = true;
            }
        }

        if (hasAlways) {
            // Allows activation on both normal hover and Ctrl+Hover
            return KeyboardTracker.getCurrentStateMask();
        } else if (hasCtrl) {
            return SWT.MOD1;
        }

        return -2;
    }

    @Override
    public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
        if (textViewer == null || region == null) {
            return null;
        }

        String scopeId = resolveScope(textViewer);
        List<ActiveRule> activeRules = RuleManager.getRulesForScope(scopeId);
        if (activeRules.isEmpty()) {
            return null;
        }

        IDocument doc = textViewer.getDocument();
        if (doc == null) {
            return null;
        }

        int offset = region.getOffset();
        try {
            IRegion lineRegion = doc.getLineInformationOfOffset(offset);
            String lineText = doc.get(lineRegion.getOffset(), lineRegion.getLength());
            if (lineText == null || lineText.isEmpty()) {
                return null;
            }

            int lineOffset = lineRegion.getOffset();
            int offsetInLine = offset - lineOffset;

            boolean isCtrl = KeyboardTracker.isCtrlPressed();
            List<LinkRange> matches = RuleManager.findLinks(lineText, activeRules, isCtrl);
            if (matches.isEmpty()) {
                return null;
            }

            List<IHyperlink> links = null;
            for (LinkRange lr : matches) {
                if (offsetInLine >= lr.getStart() && offsetInLine <= lr.getEnd()) {
                    IRegion targetRegion = new Region(lineOffset + lr.getStart(), lr.getLength());
                    if (links == null) {
                        links = new ArrayList<>(matches.size());
                    }
                    links.add(new IssueHyperlink(lr.getUrl(), targetRegion, lr.getMatchedText()));
                    if (!canShowMultipleHyperlinks) {
                        break;
                    }
                }
            }

            if (links != null && !links.isEmpty()) {
                return links.toArray(new IHyperlink[0]);
            }
        } catch (BadLocationException e) {
            // Ignore offset out of bounds
        }

        return null;
    }

    private String resolveScope(ITextViewer textViewer) {
        if (textViewer != null) {
            String className = textViewer.getClass().getName();
            if (className.contains("CommitMessage") || className.contains("history") || className.contains("History")) {
                if (className.contains("staging") || className.contains("Staging")) {
                    return ScopeRegistry.SCOPE_GIT_STAGING;
                }
                return ScopeRegistry.SCOPE_GIT_HISTORY;
            }
            if (className.contains("staging") || className.contains("Staging")) {
                return ScopeRegistry.SCOPE_GIT_STAGING;
            }
            if (textViewer.getTextWidget() != null && !textViewer.getTextWidget().isDisposed()) {
                String scopeFromControl = detectScopeFromControl(textViewer.getTextWidget());
                if (scopeFromControl != null) {
                    return scopeFromControl;
                }
            }
        }

        String scopeFromPart = detectScopeFromPart();
        if (scopeFromPart != null) {
            return scopeFromPart;
        }

        String ext = getFileExtension(textViewer);
        return ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX + (ext != null ? ext : "");
    }

    private String resolveCurrentScope() {
        try {
            Display display = Display.getCurrent();
            if (display != null) {
                Control cursorControl = display.getCursorControl();
                if (cursorControl != null && !cursorControl.isDisposed()) {
                    String scopeFromControl = detectScopeFromControl(cursorControl);
                    if (scopeFromControl != null) {
                        return scopeFromControl;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        String scopeFromPart = detectScopeFromPart();
        if (scopeFromPart != null) {
            return scopeFromPart;
        }

        String ext = resolveCurrentFileExtension();
        return ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX + (ext != null ? ext : "");
    }

    private static String detectScopeFromControl(Control control) {
        Control cur = control;
        int depth = 0;
        while (cur != null && depth < 8) {
            String className = cur.getClass().getName();
            if (className.contains("CommitMessage") || className.contains("history") || className.contains("History")) {
                if (className.contains("staging") || className.contains("Staging")) {
                    return ScopeRegistry.SCOPE_GIT_STAGING;
                }
                return ScopeRegistry.SCOPE_GIT_HISTORY;
            }
            if (className.contains("staging") || className.contains("Staging")) {
                return ScopeRegistry.SCOPE_GIT_STAGING;
            }
            cur = cur.getParent();
            depth++;
        }
        return null;
    }

    private static String detectScopeFromPart() {
        try {
            IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (win != null && win.getActivePage() != null) {
                IWorkbenchPart part = win.getActivePage().getActivePart();
                if (part != null && part.getSite() != null) {
                    String partId = part.getSite().getId();
                    if (partId != null) {
                        String lower = partId.toLowerCase();
                        if (lower.contains("history")) {
                            return ScopeRegistry.SCOPE_GIT_HISTORY;
                        }
                        if (lower.contains("staging")) {
                            return ScopeRegistry.SCOPE_GIT_STAGING;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getFileExtension(ITextViewer textViewer) {
        if (textViewer != null && textViewer.getTextWidget() != null && !textViewer.getTextWidget().isDisposed()) {
            Object cached = textViewer.getTextWidget().getData(DATA_CACHED_EXT);
            if (cached instanceof String) {
                return (String) cached;
            }
        }

        String ext = resolveCurrentFileExtension();
        if (textViewer != null && textViewer.getTextWidget() != null && !textViewer.getTextWidget().isDisposed()) {
            textViewer.getTextWidget().setData(DATA_CACHED_EXT, ext != null ? ext : "");
        }
        return ext;
    }

    private String resolveCurrentFileExtension() {
        ITextEditor editor = getAdapter(ITextEditor.class);
        if (editor == null) {
            try {
                IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (win != null && win.getActivePage() != null) {
                    IEditorPart part = win.getActivePage().getActiveEditor();
                    if (part instanceof ITextEditor) {
                        editor = (ITextEditor) part;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (editor != null && editor.getEditorInput() != null) {
            IEditorInput input = editor.getEditorInput();
            if (input instanceof IAdaptable) {
                IFile file = ((IAdaptable) input).getAdapter(IFile.class);
                if (file != null && file.getFileExtension() != null) {
                    return file.getFileExtension();
                }
            }
            String name = input.getName();
            if (name != null) {
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    return name.substring(dot + 1);
                }
            }
        }
        return "";
    }
}
