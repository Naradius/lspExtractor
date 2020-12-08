package com.naradius.lspExtractor;

import gate.*;
import gate.corpora.DocumentContentImpl;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.util.persistence.PersistenceManager;
import opennlp.tools.util.Span;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Stream;

public class Main {

    private static File gappFile = new File(".\\resources\\app.gapp");
    private static File documentDirectory;
    private static Corpus corpus;
    private static ArrayList<ExtendedParse> parsePhrases;
    private static HashMap<String, ArrayList<ExtendedParse>> phraseDistribution;

    public static void main(String[] args) throws Exception {
        parseCommandLine(args);

        Gate.init();
        CorpusController application = (CorpusController) PersistenceManager.loadObjectFromFile(gappFile);

        parsePhrases = new ArrayList<>();
        phraseDistribution = new HashMap<>();
        corpus = Factory.newCorpus("LSP Extraction Corpus");
        application.setCorpus(corpus);
        try (Stream<Path> paths = Files.walk(Paths.get(documentDirectory.getPath()))) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Document document = Factory.newDocument(path.toUri().toURL());

                            //Document preprocess
                            String content = Normalizer
                                            .normalize(document.getContent().toString(), Normalizer.Form.NFD)
                                            .replaceAll("[^\\p{ASCII}]", "")
                                            .replaceAll("(.*)\\n", "$1")
                                            .replaceAll("(\\[[^\\]\\[]*])|(\\([^\\)\\(]*\\))|(\\{[^\\}\\{]*\\})", "");
                            document.setContent(new DocumentContentImpl(content));

                            //Language detection
                            /*File modelFile = new File(".\\resources\\langdetect-183.bin");
                            LanguageDetectorModel trainedModel = new LanguageDetectorModel(modelFile);
                            LanguageDetectorME detector = new LanguageDetectorME(trainedModel);
                            Language detect = detector.predictLanguage(content);*/

                            /*final LanguageDetector detector = LanguageDetectorBuilder.fromAllLanguages().build();
                            final Language detectedLanguage = detector.detectLanguageOf(content);*/

                            corpus.add(document);
                            application.execute();
                            rebuildParseTree(document);
                            corpus.clear();
                            Factory.deleteResource(document);
                        } catch (ResourceInstantiationException | ExecutionException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException | IOException e) {
            throw new ExecutionException(e);
        }

        //debugPrintPhrases();
        calculateDistribution();
    }

    private static void debugPrintParse(@NotNull ExtendedParse parse) {
        StringBuffer sb = new StringBuffer();
        sb.append(parse.getText()).append("; ");
        parse.parseToString(sb);
        System.out.println(sb.toString());
    }

    private static void pruneTree(@NotNull ExtendedParse node) {
        ExtendedParse[] childrenArr = node.getChildren();
        if (childrenArr != null) {
            List<ExtendedParse> children = Arrays.asList(childrenArr);
            //boolean onlyWordLevelChildren = true;
            for (ExtendedParse child : children) {
                pruneTree(child);
                String childType = child.getType();

                // Word level: NNP-sub prunning
                /*if (node.getType().equals("NNP") && child.getType().equals("NNP")) {
                    node.remove(children.indexOf(child));
                }*/

                // Phrase level: word level children prunning
                /*if (phraseLevelTypes.contains(childType)) {
                    onlyWordLevelChildren = false;
                }*/
            }

            // Word level: word level children prunning
            /*if (!node.isServiceLevel() && !node.isPhraseLevel() && children.size() == 1) {
                node.remove(0);
            }*/

            // Phrase level: word level children prunning
            /*if (phraseLevelTypes.contains(nodeType) && onlyWordLevelChildren) {
                for (int i = node.getChildCount() - 1; i >= 0 ; i--) {
                    node.remove(i);
                }
            }*/
        }
    }

    /*private static void pruneSentences() {
        for (ExtendedParse sentence : parsePhrases) {
            pruneTree(sentence);
        }
    }*/

    private static void calculateDistribution() {
        for (ExtendedParse phrase : parsePhrases) {
            StringBuffer sb = new StringBuffer();
            phrase.parseToString(sb);
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

        int soloCount = 0;
        for (String pattern : phraseDistribution.keySet()) {
            ArrayList<ExtendedParse> phrases = phraseDistribution.get(pattern);
            if (phrases.size() > 1) {
                //System.out.println(pattern + ": " + phrases.size());
            } else {
                System.out.println(pattern);
                //soloCount++;
            }
        }

        System.out.println("Solo: " + soloCount);
    }

    private static void debugPrintPhrases() {
        for (ExtendedParse phrase : parsePhrases) {
            debugPrintParse(phrase);
        }
    }

    private static void rebuildParseTree(Document document) {
        AnnotationSet annots = document.getAnnotations().get("SyntaxTreeNode");
        Annotation[] sentences = annots.stream().filter(annot -> annot.getFeatures().get("cat").equals("TOP")).toArray(Annotation[]::new);
        for (Annotation sentence : sentences) {
            FeatureMap features = sentence.getFeatures();
            List<Integer> children = (List<Integer>)features.get("consists");
            String type = (String)features.get("cat");
            String text = (String)features.get("text");

            ExtendedParse node = new ExtendedParse(text, new Span(0, 1000), type, 0, null);

            if (children != null) {
                for (int childId : children) {
                    rebuildParseTree(annots, childId, node);
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
                        }
                        node.remove(i);
                    }
                }

                nodeChildren = node.getChildren();
            }

            if (!node.isServiceLevel()) {
                if (node.getDepth() >= 3) {
                    parsePhrases.add(node);
                }
            }
        }
    }

    private static void rebuildParseTree(AnnotationSet annots, int child, ExtendedParse head) {
        Optional<Annotation> childAnnot = annots.stream().filter(annot -> annot.getId() == child).findFirst();
        if (childAnnot.isPresent()) {
            FeatureMap features = childAnnot.get().getFeatures();
            List<Integer> children = (List<Integer>)features.get("consists");
            String type = (String)features.get("cat");
            String text = (String)features.get("text");

            ExtendedParse node = new ExtendedParse(text, new Span(0, 1000), type, 0, head);

            if (children != null) {
                for (int childId : children) {
                    rebuildParseTree(annots, childId, node);
                }
            }

            // Phrase level: service level children squashing
            if (node.isServiceLevel()) {
                ExtendedParse[] nodeChildren = node.getChildren();
                if (nodeChildren != null && nodeChildren.length == 1) {
                    head.add(nodeChildren[0]);
                } else if (nodeChildren != null && nodeChildren.length > 1) {
                    for (ExtendedParse childChild : nodeChildren) {
                        if (childChild.getDepth() >= 3) {
                            parsePhrases.add(childChild);
                        }
                    }
                }
            } else if (node.isPhraseLevel()) {
                ExtendedParse[] nodeChildren = node.getChildren();
                if (nodeChildren != null && nodeChildren.length > 1) {
                    boolean onlyPhraseLevelChildren = true;
                    for (ExtendedParse childChild : nodeChildren) {
                        if (childChild.isPhraseLevel() || childChild.isServiceLevel()) {
                            onlyPhraseLevelChildren = false;
                            break;
                        }
                    }

                    if (onlyPhraseLevelChildren) {

                    }
                }
            } else {
                head.add(node);
            }
        }
    }

    /**
     * Parse command line options.
     */
    private static void parseCommandLine(String[] args) throws Exception {
        int i;
        // iterate over all options (arguments starting with '-')
        for (i = 0; i < args.length && args[i].charAt(0) == '-'; i++) {
            switch (args[i].charAt(1)) {
                // -g gappFile = path to the saved application
                case 'g':
                    gappFile = new File(args[++i]);
                    break;
            }
        }

        if (args.length > 0) {
            documentDirectory = new File(args[i]);
        } else {
            documentDirectory = new File(".\\resources\\documents");
        }
    }
}
