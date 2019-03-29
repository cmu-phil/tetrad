/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetrad.util;


import edu.cmu.tetrad.annotation.Algorithm;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class AlgorithmDescriptions {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AlgorithmDescriptions.class);
    
    private static final AlgorithmDescriptions INSTANCE = new AlgorithmDescriptions();

    private final Map<String, String> algoDescMap = new HashMap<>();
    
    private AlgorithmDescriptions() {
        List<AnnotatedClass<Algorithm>> annotatedClasses = AlgorithmAnnotations.getInstance().getAnnotatedClasses();
        
        Document doc = null;
        
        // Read the copied maunal/index.html from within the jar
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("manual/index.html")) {
            if (inputStream != null) {
                doc = Jsoup.parse(inputStream, "UTF-8", "");
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to read tetrad HTML manual 'maunal/index.html' file from within the jar.", ex);
        }

        // Get the description of each algorithm, use empty string if not found
        for (AnnotatedClass<Algorithm> clazz: annotatedClasses) {
            String algoShortName = clazz.getAnnotation().command();
            String desc = "";

            if (doc != null) {
                Element algoDescription = doc.getElementById(algoShortName);
                if (algoDescription != null) {
                    Elements paragraphs = algoDescription.children();
                    desc = paragraphs.stream().map((p) -> p.text() + "\n\n").reduce(desc, String::concat);
                }
            }
            
            algoDescMap.put(algoShortName, desc);
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
}
