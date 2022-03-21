package com.vborovin.lspExtractor;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class GeneratorModule {
    public ArrayList<String> generatePatterns(ArrayList<ExtendedParse> phrases) {
        ArrayList<String> patterns = new ArrayList<>();

        int idx = 0;
        for (ExtendedParse phrase : phrases) {
            StringBuffer buffer = new StringBuffer("");
            buffer.append(phrase.getText()).append("\n");
            phrase.parseToString(buffer, true);
            buffer.append("\n");
            generatePhrase(phrase, buffer, idx);
            patterns.add(buffer.toString());

            PrintWriter out = null;
            try {
                out = new PrintWriter(".\\patterns_info\\pattern" + idx + ".txt");
                out.println(buffer.toString());
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            idx++;
        }

        PrintWriter out = null;
        try {
            out = new PrintWriter("patterns.txt");
            for (String pattern : patterns) {
                out.println(pattern);
            }
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return patterns;
    }

    private void generatePhrase(ExtendedParse phrase, StringBuffer buffer, int idx) {
        generateMeta(buffer, idx);
        generatePattern(phrase, buffer, idx);
    }

    private void generateMeta(StringBuffer buffer, int idx) {
        buffer.append("Phase: generated").append(idx).append("\n");
        buffer.append("Input: Token\n");
        buffer.append("Rule: generated").append(idx).append("\n");
    }

    private void generatePattern(ExtendedParse phrase, StringBuffer buffer, int idx) {
        buffer.append("(\n");
        parsePhrase(phrase, buffer);
        buffer.append("):pttrn").append(idx).append(" --> :pttrn").append(idx).append(".extracted").append("={}\n");
    }

    private void parsePhrase(ExtendedParse phrase, StringBuffer buffer) {
        buffer.append("(");

        ExtendedParse[] children = phrase.getChildren();
        if (children == null || children.length == 0) {
            buffer.append("{ Token.category == ");
            buffer.append(phrase.getType());
            buffer.append(" }");
        } else {
            for (ExtendedParse c : children) {
                parsePhrase(c, buffer);
            }
        }

        buffer.append(")");

        if (phrase.getMinRepeat() == 0 && phrase.getMaxRepeat() == 1) {
            buffer.append("?");
        } else if (phrase.getMaxRepeat() > 1) {
            buffer.append("+");
        }
    }
}
