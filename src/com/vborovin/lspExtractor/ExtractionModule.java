package com.vborovin.lspExtractor;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.FeatureMap;
import gate.corpora.DocumentContentImpl;
import opennlp.tools.util.Span;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.*;

public class ExtractionModule {
    public ArrayList<ExtendedParse> extractPhrases(Document document) {
        HashMap<ExtendedParse, ArrayList<ExtendedParse>> generalized = new HashMap<>();
        ArrayList<ExtendedParse> parsePhrases = new ArrayList<>();
        AnnotationSet annots = document.getAnnotations().get("SyntaxTreeNode");
        Annotation[] sentences = annots.stream().filter(annot -> annot.getFeatures().get("cat").equals("TOP")).toArray(Annotation[]::new);
        StringBuffer buffer = new StringBuffer("");
        for (Annotation sentence : sentences) {
            FeatureMap features = sentence.getFeatures();
            List<Integer> children = (List<Integer>)features.get("consists");
            String type = (String)features.get("cat");
            String text = (String)features.get("text");

            ExtendedParse node = new ExtendedParse(text, new Span(0, 1000), type, 0, null);
            ExtendedParse nNode = new ExtendedParse(text, new Span(0, 1000), type, 0, null);
            generalized.put(nNode, new ArrayList<>());
            generalized.get(nNode).add(node);

            if (children != null) {
                for (int childId : children) {
                    rebuildParseTree(annots, childId, node, parsePhrases, nNode, nNode, generalized);
                }
            }

            ExtendedParse[] nodeChildren = node.getChildren();
            // Service level: root removal
            while (node.isServiceLevel() && nodeChildren != null && nodeChildren.length == 1) {
                node = nodeChildren[0];
                nodeChildren = node.getChildren();

                if (node.isServiceLevel() && nodeChildren != null && nodeChildren.length > 1) {
                    for (int i = nodeChildren.length - 1; i >= 1; i--) {
                        ExtendedParse child = nodeChildren[i];
                        if (child.getDepth() >= 3) {
                            parsePhrases.add(child);
                            generalized.get(nNode).add(child);
                        }
                        node.remove(i);
                    }
                }

                nodeChildren = node.getChildren();
            }

            // Phrase level: node repetition
            int idx = 0;
            ExtendedParse prev = null;
            List<Integer> toRemove = new ArrayList<>();
            for (ExtendedParse parse : nodeChildren) {
                if (prev != null && prev.getType().equals(parse.getType()) && prev.parseToString(false).equals(parse.parseToString(false))) {
                    prev.setMaxRepeat(prev.getMaxRepeat() + 1);
                    toRemove.add(idx);
                }

                prev = parse;
                idx++;
            }

            Integer[] toRemoveArr = toRemove.toArray(new Integer[toRemove.size()]);
            Arrays.sort(toRemoveArr, Collections.reverseOrder());
            toRemove = Arrays.asList(toRemoveArr);

            for (Integer val : toRemoveArr) {
                node.remove(val);
            }

            if (!node.isServiceLevel()) {
                if (node.getDepth() >= 3) {
                    parsePhrases.add(node);
                    generalized.get(nNode).add(node);
                }
            }

            buffer.append(text).append("; ");
            node.parseToString(buffer, true);
            buffer.append("\n");
        }

        PrintWriter out = null;
        try {
            out = new PrintWriter("generalization.txt");
            StringBuffer buffer1 = new StringBuffer("");
            for (Map.Entry<ExtendedParse, ArrayList<ExtendedParse>> entry : generalized.entrySet()) {
                for (ExtendedParse ep : entry.getValue()) {
                    buffer1.append(entry.getKey()
                            .parseToString(true)).append("\t")
                            .append(entry.getKey().getDepth()).append("\t")
                            .append(ep.parseToString(true)).append("\t")
                            .append(ep.getDepth()).append("\n");
                }
            }
            out.println(buffer1.toString());
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return parsePhrases;
    }

    private static void rebuildParseTree(AnnotationSet annots, int child,
                                         ExtendedParse head, ArrayList<ExtendedParse> parsePhrases,
                                         ExtendedParse nHead, ExtendedParse nFirst, HashMap<ExtendedParse, ArrayList<ExtendedParse>> generalized) {
        Optional<Annotation> childAnnot = annots.stream().filter(annot -> annot.getId() == child).findFirst();
        if (childAnnot.isPresent()) {
            FeatureMap features = childAnnot.get().getFeatures();
            List<Integer> children = (List<Integer>)features.get("consists");
            String type = (String)features.get("cat");
            String text = (String)features.get("text");

            ExtendedParse node = new ExtendedParse(text, new Span(0, 1000), type, 0, head);
            ExtendedParse nNode = new ExtendedParse(text, new Span(0, 1000), type, 0, nHead);

            nHead.add(nNode);

            if (children != null) {
                for (int childId : children) {
                    rebuildParseTree(annots, childId, node, parsePhrases, nNode, nFirst, generalized);
                }
            }

            // Phrase level: node repetition
            ExtendedParse[] nodeChildren = node.getChildren();
            int idx = 0;
            ExtendedParse prev = null;
            List<Integer> toRemove = new ArrayList<>();
            for (ExtendedParse parse : nodeChildren) {
                if (prev != null && prev.getType().equals(parse.getType()) && prev.parseToString(false).equals(parse.parseToString(false))) {
                    prev.setMaxRepeat(prev.getMaxRepeat() + 1);
                    toRemove.add(idx);
                }

                prev = parse;
                idx++;
            }

            Integer[] toRemoveArr = toRemove.toArray(new Integer[toRemove.size()]);
            Arrays.sort(toRemoveArr, Collections.reverseOrder());
            toRemove = Arrays.asList(toRemoveArr);

            for (Integer val : toRemoveArr) {
                node.remove(val);
            }

            // Phrase level: service level children squashing
            if (node.isServiceLevel()) {
                nodeChildren = node.getChildren();
                if (nodeChildren != null && nodeChildren.length == 1) {
                    head.add(nodeChildren[0]);
                } else if (nodeChildren != null && nodeChildren.length > 1) {
                    for (ExtendedParse childChild : nodeChildren) {
                        if (childChild.getDepth() >= 3) {
                            parsePhrases.add(childChild);
                            generalized.get(nFirst).add(childChild);
                        }
                    }
                }
            } else if (node.isPhraseLevel()) { // Phrase level: phrase level children split
                nodeChildren = node.getChildren();
                if (nodeChildren != null && nodeChildren.length > 1) {
                    //Split all phrase/service children
                    for (ExtendedParse childChild : nodeChildren) {
                        if ((childChild.isPhraseLevel() || childChild.isServiceLevel()) && childChild.getDepth() >= 2) {
                            parsePhrases.add(childChild);
                            generalized.get(nFirst).add(childChild);
                        }
                    }

                    //Keep original node
                    head.add(node);
                }
            } else {
                head.add(node);
            }
        }
    }

    public HashMap<String, ArrayList<ExtendedParse>> calculateDistribution(ArrayList<ExtendedParse> parsePhrases) {
        HashMap<String, ArrayList<ExtendedParse>> phraseDistribution = new HashMap<>();

        for (ExtendedParse phrase : parsePhrases) {
            StringBuffer sb = new StringBuffer();
            phrase.parseToString(sb, true);
            String pattern = sb.toString();
            if (phraseDistribution.containsKey(pattern)) {
                ArrayList<ExtendedParse> phraseList = phraseDistribution.get(pattern);
                phraseList.add(phrase);
            } else {
                ArrayList<ExtendedParse> phraseList = new ArrayList<>();
                phraseList.add(phrase);
                phraseDistribution.put(pattern, phraseList);
            }
        }

        return phraseDistribution;
    }

    public void debugPrintDistribution(ArrayList<ExtendedParse> parsePhrases, HashMap<String, ArrayList<ExtendedParse>> phraseDistribution) {
        try {
            PrintWriter out = new PrintWriter("distribution.txt");
            long soloCount = 0;
            for (String pattern : phraseDistribution.keySet()) {
                ArrayList<ExtendedParse> phrases = phraseDistribution.get(pattern);
                out.println(pattern + "\t" + phrases.get(0).getDepth() + "\t" + phrases.size());
                if (phrases.size() <= 1) {
                    soloCount++;
                }
            }

            out.close();

            System.out.println("Total count: " + parsePhrases.size());
            System.out.println("Total group count: " + phraseDistribution.size());
            System.out.println("Solo: " + soloCount);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
