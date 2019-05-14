/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetrad.util;

import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmDescriptions {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlgorithmDescriptions.class);

    private static final AlgorithmDescriptions INSTANCE = new AlgorithmDescriptions();

    private final Map<String, String> algoDescMap = new HashMap<>();

    private AlgorithmDescriptions() {
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("manual/index.html")) {
            final Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
            getAlgorithms().forEach(algoShortName -> {
                Element algoDesc = doc.getElementById(algoShortName);
                if (algoDesc != null) {
                    Elements paragraphs = algoDesc.children();
                    String desc = paragraphs.stream()
                            .map(p -> p.text().trim())
                            .collect(Collectors.joining("\n"));
                    algoDescMap.put(algoShortName, desc);
                }
            });
        } catch (IOException ex) {
            LOGGER.error("Failed to read tetrad HTML manual 'maunal/index.html' file from within the jar.", ex);
        }
    }

    public static AlgorithmDescriptions getInstance() {
        return INSTANCE;
    }

    public String get(String algoShortName) {
        String algoDesc = algoDescMap.get(algoShortName);

        return (algoDesc == null)
                ? String.format("Please add algorithm description for %s.", algoShortName)
                : algoDesc;
    }

    private List<String> getAlgorithms() {
        // get algorithm from annotations
        List<String> algorithms = AlgorithmAnnotations.getInstance().getAnnotatedClasses().stream()
                .map(e -> e.getAnnotation().command())
                .collect(Collectors.toList());

        // add additional algorithms not annotated
        algorithms.add("cpc"); // conservative PC

        return algorithms;
    }

}
