package com.github.vvi3ard.dt.linkifier.model;

/**
 * Represents a matched link span within a piece of text.
 */
public class LinkRange implements Comparable<LinkRange> {

    private final int start;
    private final int length;
    private final String url;
    private final String matchedText;
    private final LinkRule rule;

    public LinkRange(int start, int length, String url, String matchedText, LinkRule rule) {
        this.start = start;
        this.length = length;
        this.url = url;
        this.matchedText = matchedText;
        this.rule = rule;
    }

    public int getStart() {
        return start;
    }

    public int getLength() {
        return length;
    }

    public int getEnd() {
        return start + length;
    }

    public String getUrl() {
        return url;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public LinkRule getRule() {
        return rule;
    }

    public boolean containsOffset(int offset) {
        return offset >= start && offset <= (start + length);
    }

    @Override
    public int compareTo(LinkRange o) {
        return Integer.compare(this.start, o.start);
    }

    @Override
    public String toString() {
        return "LinkRange{" +
                "[" + start + ".." + (start + length) + "]=" +
                "'" + matchedText + "' -> '" + url + '\'' +
                '}';
    }
}
