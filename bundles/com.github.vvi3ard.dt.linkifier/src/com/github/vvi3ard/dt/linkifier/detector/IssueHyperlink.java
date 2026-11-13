package com.github.vvi3ard.dt.linkifier.detector;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;

/**
 * Hyperlink implementation for jumping to an issue tracker URL from an editor.
 */
public class IssueHyperlink implements IHyperlink {

    private final String url;
    private final IRegion region;
    private final String label;

    public IssueHyperlink(String url, IRegion region, String label) {
        this.url = url;
        this.region = region;
        this.label = label;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "Issue Tracker";
    }

    @Override
    public String getHyperlinkText() {
        return "Открыть задачу: " + label + " (" + url + ")";
    }

    @Override
    public void open() {
        if (url != null && !url.isEmpty()) {
            Display.getDefault().asyncExec(() -> {
                try {
                    Program.launch(url);
                } catch (Exception e) {
                    System.err.println("[EDT-Linkifier] Failed to launch URL: " + url + " (" + e.getMessage() + ")");
                }
            });
        }
    }
}
