package com.vborovin.lspExtractor;

import gate.*;
import gate.corpora.DocumentContentImpl;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.persist.PersistenceException;
import gate.util.persistence.PersistenceManager;
import opennlp.tools.util.Span;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Main implements Runnable {
    private Path path;
    private CorpusController application;

    private static File gappFile = new File(".\\resources\\app.gapp");
    private static File documentDirectory;
    private static ArrayList<ExtendedParse> parsePhrases;
    private static Stack<CorpusController> appPool;

    public static void main(String[] args) throws Exception {
        parseCommandLine(args);
        Gate.init();

        int procCount = Runtime.getRuntime().availableProcessors() - 1;
        appPool = new Stack<>();
        for (int i = 0; i < procCount; i++) {
            CorpusController app = (CorpusController) PersistenceManager.loadObjectFromFile(gappFile);
            Corpus corpus = Factory.newCorpus("LSP Extraction Corpus");
            app.setCorpus(corpus);
            appPool.push(app);
        }

        parsePhrases = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(procCount);

        long startTime = System.currentTimeMillis();
        try (Stream<Path> paths = Files.walk(Paths.get(documentDirectory.getPath()))) {
            paths
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    while (true) {
                        if (!appPool.empty()) {
                            CorpusController application = appPool.pop();
                            Runnable worker = new Main(path, application);
                            executor.execute(worker);
                            break;
                        } else {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
        } catch (RuntimeException | IOException e) {
            throw new ExecutionException(e);
        }

        executor.shutdown();
        while (!executor.isTerminated()) {   }

        long endTime = System.currentTimeMillis();
        System.out.println("Time required to load: " + (endTime - startTime) / 1000);
        ExtractionModule extractionModule = new ExtractionModule();
        GeneratorModule generatorModule = new GeneratorModule();
        ArrayList<String> patterns = generatorModule.generatePatterns(parsePhrases);

        HashMap<String, ArrayList<ExtendedParse>> distribution = extractionModule.calculateDistribution(parsePhrases);
        extractionModule.debugPrintDistribution(parsePhrases, distribution);
        endTime = System.currentTimeMillis();
        System.out.println("Time required: " + (endTime - startTime) / 1000);
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

    Main() {

    }

    Main(Path path, CorpusController application) {
        this.path = path;
        this.application = application;
    }

    @Override
    public void run() {
        Corpus corpus = application.getCorpus();
        Document document = null;
        try {
            IOModule ioModule = new IOModule();
            document = ioModule.loadDocumentAndPreprocess(path);
            corpus.add(document);
            application.execute();

            ExtractionModule extractor = new ExtractionModule();
            ArrayList<ExtendedParse> phrases = extractor.extractPhrases(document);
            parsePhrases.addAll(phrases);
        } catch (ResourceInstantiationException | MalformedURLException | ExecutionException e) {
            e.printStackTrace();
        }

        corpus.clear();
        if (document != null) {
            Factory.deleteResource(document);
        }
        appPool.push(application);
    }
}
