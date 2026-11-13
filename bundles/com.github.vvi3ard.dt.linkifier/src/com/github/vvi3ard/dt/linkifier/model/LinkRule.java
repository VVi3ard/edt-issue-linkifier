package com.github.vvi3ard.dt.linkifier.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Model representing a single linkification rule with fine-grained scoping.
 */
public class LinkRule {

    private String id;
    private String name;
    private String pattern;
    private String urlTemplate;
    private boolean enabled;
    private final Map<String, ActivationMode> scopes = new LinkedHashMap<>();

    public LinkRule() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
    }

    public LinkRule(String name, String pattern, String urlTemplate, boolean enabled) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.pattern = pattern;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
    }

    public LinkRule(String id, String name, String pattern, String urlTemplate, boolean enabled) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.pattern = pattern;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the activation mode for the given scope ID.
     * If an editor extension is not explicitly specified, falls back to the residual '*' scope.
     * If still not found, returns ActivationMode.DISABLED.
     */
    public ActivationMode getScopeMode(String scopeId) {
        if (scopeId == null) {
            return ActivationMode.DISABLED;
        }

        ActivationMode mode = scopes.get(scopeId);
        if (mode != null) {
            return mode;
        }

        // Fallback for editor extensions
        if (scopeId.startsWith(ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX) && !scopeId.equals(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK)) {
            ActivationMode fallbackMode = scopes.get(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK);
            if (fallbackMode != null) {
                return fallbackMode;
            }
        }

        return ActivationMode.DISABLED;
    }

    public void setScopeMode(String scopeId, ActivationMode mode) {
        if (scopeId != null) {
            if (mode == null) {
                scopes.remove(scopeId);
            } else {
                scopes.put(scopeId, mode);
            }
        }
    }

    public Map<String, ActivationMode> getScopes() {
        return Collections.unmodifiableMap(scopes);
    }

    public void setScopes(Map<String, ActivationMode> newScopes) {
        scopes.clear();
        if (newScopes != null) {
            scopes.putAll(newScopes);
        }
    }

    public LinkRule copy() {
        LinkRule copy = new LinkRule(this.id, this.name, this.pattern, this.urlTemplate, this.enabled);
        copy.scopes.putAll(this.scopes);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkRule linkRule = (LinkRule) o;
        return enabled == linkRule.enabled &&
                Objects.equals(id, linkRule.id) &&
                Objects.equals(name, linkRule.name) &&
                Objects.equals(pattern, linkRule.pattern) &&
                Objects.equals(urlTemplate, linkRule.urlTemplate) &&
                Objects.equals(scopes, linkRule.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, pattern, urlTemplate, enabled, scopes);
    }

    @Override
    public String toString() {
        return "LinkRule{" +
                "name='" + name + '\'' +
                ", pattern='" + pattern + '\'' +
                ", urlTemplate='" + urlTemplate + '\'' +
                ", enabled=" + enabled +
                ", scopes=" + scopes +
                '}';
    }
}
