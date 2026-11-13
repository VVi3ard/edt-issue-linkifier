package com.github.vvi3ard.dt.linkifier.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.github.vvi3ard.dt.linkifier.LinkifierPlugin;
import com.github.vvi3ard.dt.linkifier.model.RuleManager;

/**
 * Initializes default preference values for the linkifier plugin.
 */
public class LinkifierPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        LinkifierPlugin plugin = LinkifierPlugin.getDefault();
        if (plugin == null) {
            return;
        }

        IPreferenceStore store = plugin.getPreferenceStore();
        store.setDefault(RuleManager.PREF_RULES_JSON, RuleManager.serializeRulesToJson(RuleManager.getDefaultRules()));
    }
}
