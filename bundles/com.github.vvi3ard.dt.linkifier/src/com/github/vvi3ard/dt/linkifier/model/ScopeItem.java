package com.github.vvi3ard.dt.linkifier.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Node in the tree hierarchy of scopes (categories, file extensions, UI panels).
 */
public class ScopeItem {

    private final String id;
    private final String label;
    private final ScopeItem parent;
    private final List<ScopeItem> children = new ArrayList<>();
    private final boolean custom;
    private final boolean fallback;

    public ScopeItem(String id, String label, ScopeItem parent) {
        this(id, label, parent, false, false);
    }

    public ScopeItem(String id, String label, ScopeItem parent, boolean custom, boolean fallback) {
        this.id = id;
        this.label = label;
        this.parent = parent;
        this.custom = custom;
        this.fallback = fallback;
        if (parent != null) {
            parent.addChild(this);
        }
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public ScopeItem getParent() {
        return parent;
    }

    public List<ScopeItem> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isGroup() {
        return !children.isEmpty();
    }

    public boolean isCustom() {
        return custom;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void addChild(ScopeItem child) {
        if (!children.contains(child)) {
            children.add(child);
        }
    }

    public void insertChildBefore(ScopeItem child, ScopeItem beforeItem) {
        children.remove(child);
        int idx = children.indexOf(beforeItem);
        if (idx >= 0) {
            children.add(idx, child);
        } else {
            children.add(child);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScopeItem scopeItem = (ScopeItem) o;
        return Objects.equals(id, scopeItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return label;
    }
}
