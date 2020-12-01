package com.naradius.lspExtractor;

import gate.*;
import gate.corpora.DocumentContentImpl;
import gate.corpora.DocumentImpl;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;
import gate.util.persistence.PersistenceManager;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

public class Main {

    private static File gappFile;
    private static URL documentDirectory;
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

                            LanguageDetector detector = LanguageDetector.getDefaultLanguageDetector();
                            LanguageResult detect = detector.detect(document.getContent().toString());
                            if (detect.isReasonablyCertain()) {
                                //document.getContent().
                                corpus.add(document);
                                application.execute();

                                rebuildParseTree(document);

                                corpus.clear();
                                Factory.deleteResource(document);
                            }
                        } catch (MalformedURLException | ResourceInstantiationException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException | IOException e) {
            throw new ExecutionException(e);
        }

        //Factory.deleteResource(corpus); - delete all documents
    }

    private static void rebuildParseTree(Document document) {
        DocumentContentImpl dc = new DocumentContentImpl("eeee");

        AnnotationSet annots = document.getAnnotations();
        AnnotationSet annotsOfThisType = annots.get("SyntaxTreeNode");
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

        documentDirectory = new URL(args[i]);

        // sanity check other arguments
        if (gappFile == null) {
            System.err.println("No .gapp file specified");
        }
    }
}
