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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.stream.Stream;

public class Main {

    private static File gappFile = new File(".\\resources\\app.gapp");
    private static File documentDirectory;
    private static Corpus corpus;

    public static void main(String[] args) throws Exception {
        parseCommandLine(args);

        Gate.init();
        CorpusController application = (CorpusController) PersistenceManager.loadObjectFromFile(gappFile);

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
    }

    private static void rebuildParseTree(Document document) {
        AnnotationSet annots = document.getAnnotations().get("SyntaxTreeNode");
        Annotation[] sentences = annots.stream().filter(annot -> annot.getFeatures().get("cat").equals("TOP")).toArray(Annotation[]::new);
        for (Annotation sentence : sentences) {
            FeatureMap features = sentence.getFeatures();
            //Annotation[] children = features.get("consists");
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
