package com.vborovin.lspExtractor;

import opennlp.tools.util.Span;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ExtendedParse {
    private String text;
    private String type;
    private int minRepeat;
    private int maxRepeat;
    private List<ExtendedParse> children;
    private ExtendedParse parent;
    private static List<String> phraseLevelTypes = Arrays.asList("ADJP", "ADVP", "CONJP", "NP", "PP", "QP", "VP", "UCP", "WHADJP", "WHAVP", "WHNP", "WHPP");
    private static List<String> serviceLevelTypes = Arrays.asList(".", "TOP", "S", ":", ",", "SBAR");

    public ExtendedParse(String text, Span span, String type, double p, int index) {
        this.text = text;
        this.type = type;
        this.children = new LinkedList();
        this.parent = null;
        this.minRepeat = 1;
        this.maxRepeat = 1;
    }

    public ExtendedParse(String text, Span span, String type, double p, ExtendedParse h) {
        this(text, span, type, p, 0);
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

    public String getText() {
        return this.text;
    }

    public ExtendedParse[] getChildren() {
        return (ExtendedParse[])this.children.toArray(new ExtendedParse[this.children.size()]);
    }

    public int getDepth() {
        int maxChildDepth = 0;

        for (ExtendedParse child : children) {
            int childDepth = child.getDepth();
            if (childDepth > maxChildDepth) {
                maxChildDepth = childDepth;
            }
        }

        return maxChildDepth + 1;
    }

    public void add(ExtendedParse child) {
        this.children.add(child);
    }

    public void remove(int index) {
        this.children.remove(index);
    }

    public int getChildCount() {
        return this.children.size();
    }

    public int indexOf(ExtendedParse child) {
        return this.children.indexOf(child);
    }

    public boolean isServiceLevel() {
        return serviceLevelTypes.contains(type);
    }

    public boolean isPhraseLevel() {
        return phraseLevelTypes.contains(type);
    }

    public ExtendedParse getParent() {
        return this.parent;
    }

    public void setParent(ExtendedParse parent) {
        this.parent = parent;
    }

    public String parseToString(boolean showRepeats) {
        StringBuffer sb = new StringBuffer();
        parseToString(sb, showRepeats);
        return sb.toString();
    }

    public void parseToString(@NotNull StringBuffer sb, boolean showRepeats) {
        sb.append("(");
        sb.append(type).append(" ");

        if (children != null) {
            for (ExtendedParse c : children) {
                c.parseToString(sb, showRepeats);
            }
        }

        sb.append(")");

        if (showRepeats && maxRepeat > 1) {
            sb.append("{").append(minRepeat).append(",").append(maxRepeat).append("}");
        }
    }

    public int getMinRepeat() {
        return minRepeat;
    }

    public void setMinRepeat(int minRepeat) {
        this.minRepeat = minRepeat;
    }

    public int getMaxRepeat() {
        return maxRepeat;
    }

    public void setMaxRepeat(int maxRepeat) {
        this.maxRepeat = maxRepeat;
    }
}