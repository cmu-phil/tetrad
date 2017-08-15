package edu.cmu.tetradapp.editor.factory;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgorithmDescription;
import edu.cmu.tetradapp.editor.AlgorithmDescriptionClass;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.reflections.Reflections;

/**
 * Author : Jeremy Espino MD Created 6/30/17 11:20 AM
 */
public class AlgorithmDescriptionFactory {

    private static AlgorithmDescriptionFactory ourInstance = new AlgorithmDescriptionFactory();

    private Map<String, Class> algorithmMap = new LinkedHashMap<>();

    private ArrayList<AlgorithmDescriptionClass> algorithmDescriptions = new ArrayList<>();

    public static AlgorithmDescriptionFactory getInstance() {
        return ourInstance;
    }

    private AlgorithmDescriptionFactory() {
        init();
    }

    private void init() {

        algorithmDescriptions = new ArrayList<>();

        // find all classes that implement algorithm
        Reflections reflections = new Reflections("edu.cmu.tetrad.algcomparison");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(edu.cmu.tetrad.annotation.AlgorithmDescription.class);

        for (Class clazz : classes) {
            AlgorithmDescriptionClass algorithmDescription;
            Annotation annotation = clazz.getAnnotation(AlgorithmDescription.class);

            if (annotation instanceof AlgorithmDescription) {
                AlgorithmDescription myAnnotation = (AlgorithmDescription) annotation;

                algorithmDescription = new AlgorithmDescriptionClass(myAnnotation.name(), myAnnotation.algType(), myAnnotation.oracleType(), myAnnotation.description());
                algorithmDescriptions.add(algorithmDescription);

                algorithmMap.put(myAnnotation.name(), clazz);
            }
        }
    }

    public List<AlgorithmDescriptionClass> getAlgorithmDescriptions() {
        return algorithmDescriptions;
    }

    public Algorithm getAlgorithmByName(String name) {

        Class clazz = algorithmMap.get(name);
        Algorithm algorithm = null;
        try {
            algorithm = (Algorithm) clazz.newInstance();
        } catch (InstantiationException e) {
            // todo : use logger
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // todo : use logger
            e.printStackTrace();
        }

        return algorithm;

    }

    public static void main(String[] args) {
        AlgorithmDescriptionFactory.getInstance().getAlgorithmDescriptions();
    }
}
