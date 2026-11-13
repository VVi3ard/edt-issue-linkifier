package com.github.vvi3ard.dt.linkifier.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and hierarchy of all supported linkification scopes.
 */
public class ScopeRegistry {

    public static final String SCOPE_EDITOR_GROUP = "editor";
    public static final String SCOPE_EDITOR_EXT_PREFIX = "editor.ext.";
    public static final String SCOPE_EDITOR_EXT_BSL = "editor.ext.bsl";
    public static final String SCOPE_EDITOR_EXT_XML = "editor.ext.xml";
    public static final String SCOPE_EDITOR_EXT_MD = "editor.ext.md";
    public static final String SCOPE_EDITOR_EXT_FEATURE = "editor.ext.feature";
    public static final String SCOPE_EDITOR_EXT_FALLBACK = "editor.ext.*";

    public static final String SCOPE_HOVER_GROUP = "hover";
    public static final String SCOPE_HOVER_BLAME = "hover.blame";
    public static final String SCOPE_HOVER_DIFF = "hover.diff";

    public static final String SCOPE_GIT_GROUP = "git";
    public static final String SCOPE_GIT_HISTORY = "git.history";
    public static final String SCOPE_GIT_STAGING = "git.staging";

    private static final ScopeRegistry INSTANCE = new ScopeRegistry();

    private final List<ScopeItem> rootScopes = new ArrayList<>();
    private final Map<String, ScopeItem> scopeIndex = new LinkedHashMap<>();
    private final ScopeItem editorGroup;
    private final ScopeItem fallbackItem;

    private ScopeRegistry() {
        // 1. Editors
        editorGroup = new ScopeItem(SCOPE_EDITOR_GROUP, "Редакторы файлов", null);
        rootScopes.add(editorGroup);
        scopeIndex.put(editorGroup.getId(), editorGroup);

        addPreset(new ScopeItem(SCOPE_EDITOR_EXT_BSL, "*.bsl (Модули 1C:Enterprise)", editorGroup));
        addPreset(new ScopeItem(SCOPE_EDITOR_EXT_XML, "*.xml (XML файлы / Метаданные)", editorGroup));
        addPreset(new ScopeItem(SCOPE_EDITOR_EXT_MD, "*.md (Markdown документация)", editorGroup));
        addPreset(new ScopeItem(SCOPE_EDITOR_EXT_FEATURE, "*.feature (Gherkin сценарии)", editorGroup));

        fallbackItem = new ScopeItem(SCOPE_EDITOR_EXT_FALLBACK, "* (Все остальные файлы - fallback)", editorGroup, false, true);
        addPreset(fallbackItem);

        // 2. Hovers
        ScopeItem hoverGroup = new ScopeItem(SCOPE_HOVER_GROUP, "Всплывающие окна", null);
        rootScopes.add(hoverGroup);
        scopeIndex.put(hoverGroup.getId(), hoverGroup);

        addPreset(new ScopeItem(SCOPE_HOVER_BLAME, "Git Blame (Авторы аннотаций)", hoverGroup));
        addPreset(new ScopeItem(SCOPE_HOVER_DIFF, "Diff / Сравнение ревизий", hoverGroup));

        // 3. Git panels
        ScopeItem gitGroup = new ScopeItem(SCOPE_GIT_GROUP, "Панели Git", null);
        rootScopes.add(gitGroup);
        scopeIndex.put(gitGroup.getId(), gitGroup);

        addPreset(new ScopeItem(SCOPE_GIT_HISTORY, "История коммитов (Git History)", gitGroup));
        addPreset(new ScopeItem(SCOPE_GIT_STAGING, "Индексация изменений (Git Staging)", gitGroup));
    }

    private void addPreset(ScopeItem item) {
        scopeIndex.put(item.getId(), item);
    }

    public static ScopeRegistry getInstance() {
        return INSTANCE;
    }

    public List<ScopeItem> getRootScopes() {
        return Collections.unmodifiableList(rootScopes);
    }

    public ScopeItem findScope(String id) {
        if (id == null) return null;
        return scopeIndex.get(id);
    }

    /**
     * Registers a custom file extension scope if it doesn't already exist.
     * Inserts it right before the fallback '*' item.
     */
    public synchronized ScopeItem registerExtension(String rawExt) {
        if (rawExt == null) return null;
        String ext = rawExt.trim().toLowerCase();
        if (ext.startsWith("*.")) {
            ext = ext.substring(2);
        } else if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (ext.isEmpty()) return null;

        String scopeId = SCOPE_EDITOR_EXT_PREFIX + ext;
        ScopeItem existing = scopeIndex.get(scopeId);
        if (existing != null) {
            return existing;
        }

        String label = "*." + ext + " (Пользовательское расширение)";
        ScopeItem newItem = new ScopeItem(scopeId, label, editorGroup, true, false);
        // Ensure proper positioning before fallback item
        editorGroup.insertChildBefore(newItem, fallbackItem);
        scopeIndex.put(scopeId, newItem);
        return newItem;
    }

    public List<ScopeItem> getAllLeafScopes() {
        List<ScopeItem> leaves = new ArrayList<>();
        collectLeaves(rootScopes, leaves);
        return leaves;
    }

    private void collectLeaves(List<ScopeItem> items, List<ScopeItem> result) {
        for (ScopeItem item : items) {
            if (item.hasChildren()) {
                collectLeaves(item.getChildren(), result);
            } else {
                result.add(item);
            }
        }
    }
}
