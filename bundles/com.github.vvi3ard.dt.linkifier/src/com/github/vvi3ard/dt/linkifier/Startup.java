package com.github.vvi3ard.dt.linkifier;

import org.eclipse.ui.IStartup;

/**
 * Ensures early activation of the plugin on workbench startup.
 */
public class Startup implements IStartup {

    @Override
    public void earlyStartup() {
        // Accessing the default plugin instance triggers lazy bundle activation
        LinkifierPlugin plugin = LinkifierPlugin.getDefault();
        if (plugin != null) {
            LinkifierPlugin.logInfo("Early startup completed.");
        }
    }
}
