/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.annotation;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;

import java.util.List;

/**
 * Annotations for algorithms.
 * <p>
 * Sep 26, 2017 12:19:41 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class AlgorithmAnnotations extends AbstractAnnotations<Algorithm> {

    private static final AlgorithmAnnotations INSTANCE = new AlgorithmAnnotations();

    private AlgorithmAnnotations() {
        super("edu.cmu.tetrad.algcomparison.algorithm", Algorithm.class);
    }

    /**
     * Gets the instance of this class.
     *
     * @return instance of this class
     */
    public static AlgorithmAnnotations getInstance() {
        return AlgorithmAnnotations.INSTANCE;
    }

    /**
     * Filters out algorithms that are not for the given data type.
     *
     * @param list list of algorithms
     * @return list of algorithms that are not for the given data type
     */
    public List<AnnotatedClass<Algorithm>> filterOutExperimental(List<AnnotatedClass<Algorithm>> list) {
        return filterOutByAnnotation(list, Experimental.class);
    }

    /**
     * Checks if the algorithm takes multiple data sets.
     *
     * @param clazz algorithm class
     * @return true if the algorithm takes multiple data sets
     */
    public boolean takesMultipleDataset(Class clazz) {
        return clazz != null && MultiDataSetAlgorithm.class.isAssignableFrom(clazz);
    }

    /**
     * Checks if the algorithm takes knowledge.
     *
     * @param clazz algorithm class
     * @return true if the algorithm takes knowledge
     */
    public boolean takesKnowledge(Class clazz) {
        return clazz != null && HasKnowledge.class.isAssignableFrom(clazz);
    }

    /**
     * Checks if the algorithm takes an external graph.
     *
     * @param clazz algorithm class
     * @return true if the algorithm takes an external graph
     */
    public boolean takesExternalGraph(Class clazz) {
        return clazz != null && TakesExternalGraph.class.isAssignableFrom(clazz);
    }

    /**
     * Checks if the algorithm requires independence test.
     *
     * @param clazz algorithm class
     * @return true if the algorithm requires independence test
     */
    public boolean requiresIndependenceTest(Class clazz) {
        return clazz != null && TakesIndependenceWrapper.class.isAssignableFrom(clazz);
    }

    /**
     * Checks if the algorithm requires a score.
     *
     * @param clazz algorithm class
     * @return true if the algorithm requires a score
     */
    public boolean requiresScore(Class clazz) {
        return clazz != null && TakesScoreWrapper.class.isAssignableFrom(clazz);
    }

    /**
     * Checks if the algorithm handles unmeasured confounders.
     *
     * @param clazz algorithm class
     * @return true if the algorithm handles unmeasured confounders
     */
    public boolean handlesUnmeasuredConfounder(Class clazz) {
        return clazz != null && clazz.isAnnotationPresent(UnmeasuredConfounder.class);
    }

}
