package com.naradius.lspExtractor;

import com.github.pemistahl.lingua.api.*;
import static com.github.pemistahl.lingua.api.Language.*;

import gate.*;
import gate.corpora.DocumentContentImpl;
import gate.corpora.DocumentImpl;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;
import gate.util.persistence.PersistenceManager;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.lang.en.HeadRules;
import opennlp.tools.util.Span;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
    private static ArrayList<Parse> parseSentences;
    private static HashMap<Parse, Integer> patternCount;

    public static void main(String[] args) throws Exception {
        parseCommandLine(args);

        Gate.init();
        CorpusController application = (CorpusController) PersistenceManager.loadObjectFromFile(gappFile);

        parseSentences = new ArrayList<>();
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

        calculateDistribution();
    }

    private static void parseToString(StringBuffer sb, Parse parse) {
        sb.append("(");
        sb.append(parse.getType()).append(" ");

        Parse[] children = parse.getChildren();
        if (children != null) {
            for (Parse c : children) {
                parseToString(sb, c);
            }
        }

        sb.append(")");
    }

    private static void parseToString(Parse parse) {
        StringBuffer sb = new StringBuffer();
        parseToString(sb, parse);
        System.out.println(sb.toString());
    }

    private static void generifyTree(Parse node) {
        for (Parse child : node.getChildren()) {
            generifyTree(child);
        }
    }

    private static void calculateDistribution() {
        for (Parse sentence : parseSentences) {
            //Debug print
            parseToString(sentence);

            generifyTree(sentence);
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

            Parse node = new Parse(text, new Span(0, 1000), type, 0, null);

            if (children != null) {
                for (int childId : children) {
                    rebuildParseTree(annots, childId, node);
                }
            }

            parseSentences.add(node);
        }
    }

    private static void rebuildParseTree(AnnotationSet annots, int child, Parse head) {
        Optional<Annotation> childAnnot = annots.stream().filter(annot -> annot.getId() == child).findFirst();
        if (childAnnot.isPresent()) {
            FeatureMap features = childAnnot.get().getFeatures();
            List<Integer> children = (List<Integer>)features.get("consists");
            String type = (String)features.get("cat");
            String text = (String)features.get("text");

            Parse node = new Parse(text, new Span(0, 1000), type, 0, head);

            if (children != null) {
                for (int childId : children) {
                    rebuildParseTree(annots, childId, node);
                }
            }

            head.insert(node);
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
