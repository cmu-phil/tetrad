package edu.cmu.tetradapp.editor.factory;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgorithmDescription;
import edu.cmu.tetradapp.editor.AlgorithmDescriptionClass;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.reflections.Reflections;

/**
 * Author : Jeremy Espino MD Created 6/30/17 11:20 AM
 */
public class AlgorithmDescriptionFactory {

    private static AlgorithmDescriptionFactory ourInstance = new AlgorithmDescriptionFactory();

    private Map<String, Class> algorithmMap = new LinkedHashMap<>();

    private TreeMap<String, AlgorithmDescriptionClass> algorithmDescriptions = new TreeMap<>();

    private List<String> algorithmsCanHaveKnowledge = new ArrayList();

    public static AlgorithmDescriptionFactory getInstance() {
        return ourInstance;
    }

    private AlgorithmDescriptionFactory() {
        init();
    }

    private void init() {

        algorithmDescriptions = new TreeMap<>();

        // find all classes that implement algorithm
        Reflections reflections = new Reflections("edu.cmu.tetrad.algcomparison");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(edu.cmu.tetrad.annotation.AlgorithmDescription.class);

        for (Class clazz : classes) {
            AlgorithmDescriptionClass algorithmDescription;
            Annotation annotation = clazz.getAnnotation(AlgorithmDescription.class);

            if (annotation instanceof AlgorithmDescription) {
                AlgorithmDescription myAnnotation = (AlgorithmDescription) annotation;

                algorithmDescription = new AlgorithmDescriptionClass(myAnnotation.name(), myAnnotation.algType(), myAnnotation.oracleType(), myAnnotation.description());

                algorithmDescriptions.put(myAnnotation.name(), algorithmDescription);

                algorithmMap.put(myAnnotation.name(), clazz);

                // In additionl, we want to know if this class implements the HadKnowledge interface - Zhou
                if (HasKnowledge.class.isAssignableFrom(clazz)) {
                    algorithmsCanHaveKnowledge.add(myAnnotation.name());
                }
            }
        }
    }

    /**
     * The tree map is sorted according to the natural ordering of its keys
     * (algo names)
     *
     * @return
     */
    public TreeMap<String, AlgorithmDescriptionClass> getAlgorithmDescriptions() {
        return algorithmDescriptions;
    }

    /**
     * Get the list of algorithms that implement the HasKnowledge interface
     *
     * @return
     */
    public List<String> getAlgorithmsCanHaveKnowledge() {
        return algorithmsCanHaveKnowledge;
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
