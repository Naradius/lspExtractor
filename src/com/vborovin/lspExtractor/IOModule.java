package com.vborovin.lspExtractor;

import gate.Document;
import gate.Factory;
import gate.corpora.DocumentContentImpl;
import gate.creole.ResourceInstantiationException;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.text.Normalizer;

public class IOModule {
    public Document loadDocumentAndPreprocess(Path path) throws MalformedURLException, ResourceInstantiationException {
        Document document = null;
        document = Factory.newDocument(path.toUri().toURL());
        //Document preprocess
        String content = Normalizer
                .normalize(document.getContent().toString(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("(.*)\\n", "$1")
                .replaceAll("(\\[[^\\]\\[]*])|(\\([^\\)\\(]*\\))|(\\{[^\\}\\{]*\\})", "");
        document.setContent(new DocumentContentImpl(content));

        return document;
    }


}
