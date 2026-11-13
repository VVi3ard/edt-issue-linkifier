package com.github.vvi3ard.dt.linkifier;

import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.github.vvi3ard.dt.linkifier.core.GlobalHoverListener;
import com.github.vvi3ard.dt.linkifier.core.KeyboardTracker;
import com.github.vvi3ard.dt.linkifier.core.StyledTextLinkifier;
import com.github.vvi3ard.dt.linkifier.model.LinkRule;
import com.github.vvi3ard.dt.linkifier.model.RuleManager;

/**
 * Activator and lifecycle coordinator for the EDT Universal Linkifier plugin.
 */
public class LinkifierPlugin extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.github.vvi3ard.dt.linkifier";

    private static LinkifierPlugin plugin;

    private IPropertyChangeListener preferenceListener;

    public LinkifierPlugin() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        // Load rules from preferences
        reloadPreferences();

        // Listen for preference changes
        preferenceListener = new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (RuleManager.PREF_RULES_JSON.equals(event.getProperty()) ||
                    RuleManager.PREF_CTRL_REQUIRED.equals(event.getProperty())) {
                    reloadPreferences();
                    StyledTextLinkifier.refreshAll();
                }
            }
        };
        getPreferenceStore().addPropertyChangeListener(preferenceListener);

        // Install global UI hover listener and keyboard tracker
        Display.getDefault().asyncExec(() -> {
            try {
                KeyboardTracker.init();
                GlobalHoverListener.install(Display.getDefault());
                logInfo("EDT Issue Linkifier successfully started and hooked.");
            } catch (Exception e) {
                logError("Failed to install GlobalHoverListener or KeyboardTracker", e);
            }
        });
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (preferenceListener != null) {
            getPreferenceStore().removePropertyChangeListener(preferenceListener);
            preferenceListener = null;
        }

        GlobalHoverListener.uninstall();
        plugin = null;
        super.stop(context);
    }

    public static LinkifierPlugin getDefault() {
        return plugin;
    }

    public void reloadPreferences() {
        IPreferenceStore store = getPreferenceStore();
        String json = store.getString(RuleManager.PREF_RULES_JSON);
        if (json != null && !json.trim().isEmpty()) {
            List<LinkRule> rules = RuleManager.deserializeRulesFromJson(json);
            if (!rules.isEmpty()) {
                RuleManager.setRules(rules);
            } else {
                RuleManager.setRules(RuleManager.getDefaultRules());
            }
        } else {
            RuleManager.setRules(RuleManager.getDefaultRules());
        }

        RuleManager.setCtrlRequired(store.getBoolean(RuleManager.PREF_CTRL_REQUIRED));
    }

    public static void logInfo(String message) {
        if (plugin != null) {
            ILog log = plugin.getLog();
            log.log(new Status(IStatus.INFO, PLUGIN_ID, message));
        } else {
            System.out.println("[EDT-Linkifier] " + message);
        }
    }

    public static void logError(String message, Throwable t) {
        if (plugin != null) {
            ILog log = plugin.getLog();
            log.log(new Status(IStatus.ERROR, PLUGIN_ID, message, t));
        } else {
            System.err.println("[EDT-Linkifier] " + message);
            if (t != null) t.printStackTrace();
        }
    }
}
