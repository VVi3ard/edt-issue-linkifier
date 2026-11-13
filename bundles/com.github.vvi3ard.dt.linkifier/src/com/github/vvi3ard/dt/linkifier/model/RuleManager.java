package com.github.vvi3ard.dt.linkifier.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * High-performance router and manager of active linkification rules.
 * Uses pre-indexed O(1) routing tables per scope and file extension.
 */
public class RuleManager {

    public static final String PREF_RULES_JSON = "linkifier.rules.json";
    public static final String PREF_CTRL_REQUIRED = "linkifier.ctrl.required";

    public static final String DEFAULT_RULE_URL = "https://support.youjira.ru/browse/$1";

    private static final List<CompiledRule> compiledRules = new CopyOnWriteArrayList<>();
    private static final Map<String, List<ActiveRule>> extensionCache = new ConcurrentHashMap<>();
    private static final Map<String, List<ActiveRule>> hoverCache = new ConcurrentHashMap<>();

    private static volatile boolean ctrlRequired = false;

    public static class CompiledRule {
        private final LinkRule rule;
        private final Pattern pattern;
        private final int targetGroup;

        public CompiledRule(LinkRule rule, Pattern pattern) {
            this.rule = rule;
            this.pattern = pattern;
            int count = pattern.matcher("").groupCount();
            this.targetGroup = (count >= 1) ? 1 : 0;
        }

        public LinkRule getRule() {
            return rule;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public int getTargetGroup() {
            return targetGroup;
        }
    }

    public static class ActiveRule {
        private final LinkRule rule;
        private final Pattern pattern;
        private final int targetGroup;
        private final ActivationMode mode;

        public ActiveRule(LinkRule rule, Pattern pattern, int targetGroup, ActivationMode mode) {
            this.rule = rule;
            this.pattern = pattern;
            this.targetGroup = targetGroup;
            this.mode = mode;
        }

        public LinkRule getRule() {
            return rule;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public int getTargetGroup() {
            return targetGroup;
        }

        public ActivationMode getMode() {
            return mode;
        }
    }

    static {
        // Initialize with default demonstration rules
        setRules(getDefaultRules());
    }

    public static boolean isCtrlRequired() {
        return ctrlRequired;
    }

    public static void setCtrlRequired(boolean required) {
        ctrlRequired = required;
    }

    public static synchronized void setRules(List<LinkRule> rules) {
        List<CompiledRule> compiled = new ArrayList<>();
        if (rules != null) {
            for (LinkRule r : rules) {
                if (r == null || !r.isEnabled()) {
                    continue;
                }
                String patStr = r.getPattern();
                if (patStr == null || patStr.trim().isEmpty()) {
                    continue;
                }
                try {
                    Pattern p = Pattern.compile(patStr);
                    compiled.add(new CompiledRule(r, p));
                } catch (PatternSyntaxException e) {
                    System.err.println("[EDT-Linkifier] Invalid regex pattern: " + patStr + " (" + e.getMessage() + ")");
                }
            }
        }

        compiledRules.clear();
        compiledRules.addAll(compiled);

        // Invalidate pre-indexed caches
        extensionCache.clear();
        hoverCache.clear();
    }

    /**
     * O(1) Fast lookup of active rules for any scope (editor extension, hover, git panels).
     */
    public static List<ActiveRule> getRulesForScope(String scopeId) {
        if (scopeId == null) {
            return Collections.emptyList();
        }
        if (scopeId.startsWith(ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX)) {
            String ext = scopeId.substring(ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX.length());
            return getRulesForExtension(ext);
        }
        return getRulesForHover(scopeId);
    }

    /**
     * O(1) Fast lookup of active rules for a specific file extension (e.g. "bsl", "xml").
     */
    public static List<ActiveRule> getRulesForExtension(String rawExt) {
        if (rawExt == null) {
            return Collections.emptyList();
        }

        String ext = rawExt.toLowerCase().trim();
        if (ext.startsWith("*.")) {
            ext = ext.substring(2);
        } else if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }

        List<ActiveRule> cached = extensionCache.get(ext);
        if (cached != null) {
            return cached;
        }

        String scopeId = ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX + ext;
        List<ActiveRule> active = new ArrayList<>();
        for (CompiledRule cr : compiledRules) {
            ActivationMode mode = cr.getRule().getScopeMode(scopeId);
            if (mode != null && mode != ActivationMode.DISABLED) {
                active.add(new ActiveRule(cr.getRule(), cr.getPattern(), cr.getTargetGroup(), mode));
            }
        }

        List<ActiveRule> result = active.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(active);
        extensionCache.put(ext, result);
        return result;
    }

    /**
     * O(1) Fast lookup of active rules for hover / git panels (e.g. "hover.blame", "git.history").
     */
    public static List<ActiveRule> getRulesForHover(String hoverScopeId) {
        if (hoverScopeId == null) {
            return Collections.emptyList();
        }

        List<ActiveRule> cached = hoverCache.get(hoverScopeId);
        if (cached != null) {
            return cached;
        }

        List<ActiveRule> active = new ArrayList<>();
        for (CompiledRule cr : compiledRules) {
            ActivationMode mode = cr.getRule().getScopeMode(hoverScopeId);
            if (mode != null && mode != ActivationMode.DISABLED) {
                active.add(new ActiveRule(cr.getRule(), cr.getPattern(), cr.getTargetGroup(), mode));
            }
        }

        List<ActiveRule> result = active.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(active);
        hoverCache.put(hoverScopeId, result);
        return result;
    }

    /**
     * High-performance link search for a single line or text block against filtered rules.
     * Respects targetGroup to isolate issue keys inside contextual patterns (such as comments ^//).
     */
    public static List<LinkRange> findLinks(String text, List<ActiveRule> activeRules, boolean isCtrlPressed) {
        if (text == null || text.isEmpty() || activeRules == null || activeRules.isEmpty()) {
            return Collections.emptyList();
        }

        List<LinkRange> results = null;

        for (ActiveRule ar : activeRules) {
            ActivationMode mode = ar.getMode();
            // If rule requires Ctrl and Ctrl is not currently pressed, skip
            if (mode == ActivationMode.CTRL_CLICK && !isCtrlPressed) {
                continue;
            }

            Matcher m = ar.getPattern().matcher(text);
            while (m.find()) {
                int tg = ar.getTargetGroup();
                int start = m.start(tg);
                int end = m.end(tg);
                if (start < 0 || end <= start) {
                    continue;
                }

                String url = ar.getRule().getUrlTemplate();
                if (url == null) {
                    continue;
                }

                // Replace placeholders $0, $1, etc.
                url = url.replace("$0", m.group(0));
                int groupCount = m.groupCount();
                for (int g = 1; g <= groupCount; g++) {
                    String grpVal = m.group(g);
                    if (grpVal != null) {
                        url = url.replace("$" + g, grpVal);
                    }
                }

                String matchedText = text.substring(start, end);
                if (results == null) {
                    results = new ArrayList<>(4);
                }
                results.add(new LinkRange(start, end - start, url, matchedText, ar.getRule()));
            }
        }

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        if (results.size() > 1) {
            Collections.sort(results);
            List<LinkRange> nonOverlapping = new ArrayList<>(results.size());
            int lastEnd = -1;
            for (LinkRange lr : results) {
                if (lr.getStart() >= lastEnd) {
                    nonOverlapping.add(lr);
                    lastEnd = lr.getEnd();
                }
            }
            return nonOverlapping;
        }

        return results;
    }

    /**
     * General link search across all compiled rules without scope filtering (used for live preview/testing).
     */
    public static List<LinkRange> findLinks(String text) {
        if (text == null || text.isEmpty() || compiledRules.isEmpty()) {
            return Collections.emptyList();
        }

        List<LinkRange> results = null;

        for (CompiledRule cr : compiledRules) {
            Matcher m = cr.getPattern().matcher(text);
            while (m.find()) {
                int tg = cr.getTargetGroup();
                int start = m.start(tg);
                int end = m.end(tg);
                if (start < 0 || end <= start) {
                    continue;
                }

                String url = cr.getRule().getUrlTemplate();
                if (url == null) {
                    continue;
                }

                url = url.replace("$0", m.group(0));
                int groupCount = m.groupCount();
                for (int g = 1; g <= groupCount; g++) {
                    String grpVal = m.group(g);
                    if (grpVal != null) {
                        url = url.replace("$" + g, grpVal);
                    }
                }

                String matchedText = text.substring(start, end);
                if (results == null) {
                    results = new ArrayList<>(4);
                }
                results.add(new LinkRange(start, end - start, url, matchedText, cr.getRule()));
            }
        }

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        if (results.size() > 1) {
            Collections.sort(results);
            List<LinkRange> nonOverlapping = new ArrayList<>(results.size());
            int lastEnd = -1;
            for (LinkRange lr : results) {
                if (lr.getStart() >= lastEnd) {
                    nonOverlapping.add(lr);
                    lastEnd = lr.getEnd();
                }
            }
            return nonOverlapping;
        }

        return results;
    }

    /**
     * Default rules showcasing two distinct routing behaviors:
     * 1. BSL comments only on Ctrl+Click with ^//.
     * 2. Everywhere except BSL on ALWAYS with clean token matching.
     */
    public static List<LinkRule> getDefaultRules() {
        List<LinkRule> list = new ArrayList<>();

        // Rule 1: BSL comments only, on Ctrl+Click
        LinkRule bslRule = new LinkRule(
                "Jira TSK (BSL комментарии)",
                "^\\s*//.*?\\b(TSK-\\d{6})\\b",
                DEFAULT_RULE_URL,
                true);
        bslRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_BSL, ActivationMode.CTRL_CLICK);
        bslRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK, ActivationMode.DISABLED);
        bslRule.setScopeMode(ScopeRegistry.SCOPE_HOVER_BLAME, ActivationMode.DISABLED);
        bslRule.setScopeMode(ScopeRegistry.SCOPE_HOVER_DIFF, ActivationMode.DISABLED);
        bslRule.setScopeMode(ScopeRegistry.SCOPE_GIT_HISTORY, ActivationMode.DISABLED);
        bslRule.setScopeMode(ScopeRegistry.SCOPE_GIT_STAGING, ActivationMode.DISABLED);
        list.add(bslRule);

        // Rule 2: Everywhere except BSL, ALWAYS
        LinkRule globalRule = new LinkRule(
                "Jira TSK (Везде, кроме BSL)",
                "\\b(TSK-\\d{6})\\b",
                DEFAULT_RULE_URL,
                true);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_BSL, ActivationMode.DISABLED);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_XML, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_MD, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_FEATURE, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_HOVER_BLAME, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_HOVER_DIFF, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_GIT_HISTORY, ActivationMode.ALWAYS);
        globalRule.setScopeMode(ScopeRegistry.SCOPE_GIT_STAGING, ActivationMode.ALWAYS);
        list.add(globalRule);

        return list;
    }

    public static String serializeRulesToJson(List<LinkRule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                LinkRule r = rules.get(i);
                sb.append("  {\n");
                sb.append("    \"id\": \"").append(escapeJson(r.getId())).append("\",\n");
                sb.append("    \"name\": \"").append(escapeJson(r.getName())).append("\",\n");
                sb.append("    \"pattern\": \"").append(escapeJson(r.getPattern())).append("\",\n");
                sb.append("    \"urlTemplate\": \"").append(escapeJson(r.getUrlTemplate())).append("\",\n");
                sb.append("    \"enabled\": ").append(r.isEnabled()).append(",\n");

                sb.append("    \"scopes\": {\n");
                Map<String, ActivationMode> scopes = r.getScopes();
                int sIdx = 0;
                for (Map.Entry<String, ActivationMode> entry : scopes.entrySet()) {
                    sb.append("      \"").append(escapeJson(entry.getKey())).append("\": \"")
                      .append(entry.getValue().name()).append("\"");
                    if (sIdx < scopes.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                    sIdx++;
                }
                sb.append("    }\n");

                sb.append("  }");
                if (i < rules.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<LinkRule> deserializeRulesFromJson(String json) {
        List<LinkRule> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }

        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return list;
        }

        int depth = 0;
        int objStart = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        String objJson = trimmed.substring(objStart, i + 1);
                        LinkRule rule = parseRuleObject(objJson);
                        if (rule != null) {
                            list.add(rule);
                        }
                        objStart = -1;
                    }
                }
            }
        }
        return list;
    }

    private static LinkRule parseRuleObject(String objJson) {
        String id = extractJsonField(objJson, "id");
        String name = extractJsonField(objJson, "name");
        String pattern = extractJsonField(objJson, "pattern");
        String urlTemplate = extractJsonField(objJson, "urlTemplate");
        String enabledStr = extractJsonField(objJson, "enabled");

        boolean enabled = enabledStr == null || !enabledStr.equalsIgnoreCase("false");
        if (name == null && pattern == null) {
            return null;
        }

        LinkRule rule = new LinkRule(id, name != null ? name : "", pattern != null ? pattern : "", urlTemplate != null ? urlTemplate : "", enabled);

        // Parse scopes object if present
        Pattern pScopes = Pattern.compile("\"scopes\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher mScopes = pScopes.matcher(objJson);
        if (mScopes.find()) {
            String scopesContent = mScopes.group(1);
            Pattern pEntry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
            Matcher mEntry = pEntry.matcher(scopesContent);
            while (mEntry.find()) {
                String scopeId = unescapeJson(mEntry.group(1));
                String modeStr = unescapeJson(mEntry.group(2));
                ActivationMode mode = ActivationMode.fromString(modeStr);
                rule.setScopeMode(scopeId, mode);

                // Auto-register custom file extensions if any
                if (scopeId.startsWith(ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX) && !scopeId.equals(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK)) {
                    String ext = scopeId.substring(ScopeRegistry.SCOPE_EDITOR_EXT_PREFIX.length());
                    ScopeRegistry.getInstance().registerExtension(ext);
                }
            }
        } else {
            // Backward compatibility for rules serialized without scopes (v1.0.x)
            rule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK, ActivationMode.CTRL_CLICK);
            rule.setScopeMode(ScopeRegistry.SCOPE_HOVER_BLAME, ActivationMode.ALWAYS);
            rule.setScopeMode(ScopeRegistry.SCOPE_HOVER_DIFF, ActivationMode.ALWAYS);
            rule.setScopeMode(ScopeRegistry.SCOPE_GIT_HISTORY, ActivationMode.ALWAYS);
            rule.setScopeMode(ScopeRegistry.SCOPE_GIT_STAGING, ActivationMode.ALWAYS);
        }

        return rule;
    }

    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|(true|false|[0-9]+))");
        Matcher m = p.matcher(json);
        if (m.find()) {
            if (m.group(1) != null) {
                return unescapeJson(m.group(1));
            } else if (m.group(2) != null) {
                return m.group(2).trim();
            }
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
