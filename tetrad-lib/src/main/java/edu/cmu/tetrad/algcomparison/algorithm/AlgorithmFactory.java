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
package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.graph.Graph;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Aug 30, 2017 3:14:40 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmFactory {

    private AlgorithmFactory() {
    }

    /**
     * Creates an algorithm.
     *
     * @param algoClass algorithm class
     * @param test independence test
     * @param score score
     * @return algorithm
     * @throws IllegalAccessException Reflection exception
     * @throws InstantiationException Reflection exception
     * @throws InvocationTargetException Reflection exception
     */
    public static Algorithm create(Class<? extends Algorithm> algoClass, IndependenceWrapper test, ScoreWrapper score)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        if (algoClass == null) {
            throw new IllegalArgumentException("Algorithm class cannot be null.");
        }

        AlgorithmAnnotations algoAnno = AlgorithmAnnotations.getInstance();
        boolean testRequired = algoAnno.requiresIndependenceTest(algoClass);
        if (testRequired && test == null) {
            throw new IllegalArgumentException("Test of independence is required.");
        }

        boolean scoreRequired = algoAnno.requiresScore(algoClass);
        if (scoreRequired && score == null) {
            throw new IllegalArgumentException("Score is required.");
        }

        Algorithm algorithm = createAlgorithmNewInstance(algoClass);
        if (testRequired) {
            ((TakesIndependenceWrapper) algorithm).setIndependenceWrapper(test);
        }
        if (scoreRequired) {
            ((UsesScoreWrapper) algorithm).setScoreWrapper(score);
        }

        return algorithm;
    }

    /**
     * Creates an algorithm.
     *
     * @param algoClass algorithm class
     * @param test independence test
     * @param score score
     * @param externalGraph external graph
     * @return algorithm
     * @throws IllegalAccessException Reflection exception
     * @throws InstantiationException Reflection exception
     * @throws InvocationTargetException Reflection exception
     */
    public static Algorithm create(
            Class<? extends Algorithm> algoClass, IndependenceWrapper test, ScoreWrapper score, Graph externalGraph)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Algorithm algorithm = AlgorithmFactory.create(algoClass, test, score);
        if (externalGraph != null && algorithm instanceof TakesExternalGraph) {
            ((TakesExternalGraph) algorithm).setExternalGraph(new SingleGraphAlg(externalGraph));
        }

        return algorithm;
    }

    /**
     * Creates an algorithm.
     *
     * @param algoClass algorithm class
     * @param indTestClass independence test class
     * @param scoreClass score class
     * @return algorithm
     * @throws IllegalAccessException Reflection exception
     * @throws InstantiationException Reflection exception
     * @throws InvocationTargetException Reflection exception
     */
    public static Algorithm create(
            Class<? extends Algorithm> algoClass,
            Class<? extends IndependenceWrapper> indTestClass,
            Class<? extends ScoreWrapper> scoreClass)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        if (algoClass == null) {
            throw new IllegalArgumentException("Algorithm class cannot be null.");
        }

        IndependenceWrapper test = createTestOfIndependenceNewInstance(indTestClass);
        ScoreWrapper score = createScoreNewInstance(scoreClass);

        return AlgorithmFactory.create(algoClass, test, score);
    }

    /**
     * Creates an algorithm.
     *
     * @param algoClass algorithm class
     * @param indTestClass independence test class
     * @param scoreClass score class
     * @param externalGraph external graph
     * @return algorithm
     * @throws IllegalAccessException Reflection exception
     * @throws InstantiationException Reflection exception
     * @throws InvocationTargetException Reflection exception
     */
    public static Algorithm create(
            Class<? extends Algorithm> algoClass,
            Class<? extends IndependenceWrapper> indTestClass,
            Class<? extends ScoreWrapper> scoreClass, Graph externalGraph)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Algorithm algorithm = AlgorithmFactory.create(algoClass, indTestClass, scoreClass);
        if (externalGraph != null && algorithm instanceof TakesExternalGraph) {
            ((TakesExternalGraph) algorithm).setExternalGraph(new SingleGraphAlg(externalGraph));
        }

        return algorithm;
    }

    private static ScoreWrapper createScoreNewInstance(Class<? extends ScoreWrapper> scoreClass)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Constructor<? extends ScoreWrapper> constructor = getScoreNoArgConstructor(scoreClass);

        return (constructor == null) ? null : constructor.newInstance();
    }

    private static IndependenceWrapper createTestOfIndependenceNewInstance(Class<? extends IndependenceWrapper> indTestClass)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Constructor<? extends IndependenceWrapper> constructor = getTestOfIndependenceNoArgConstructor(indTestClass);

        return (constructor == null) ? null : constructor.newInstance();
    }

    private static Algorithm createAlgorithmNewInstance(Class<? extends Algorithm> algoClass) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Constructor<? extends Algorithm> constructor = getAlgorithmNoArgConstructor(algoClass);

        return (constructor == null) ? null : constructor.newInstance();
    }

    private static Constructor<? extends ScoreWrapper> getScoreNoArgConstructor(Class<? extends ScoreWrapper> scoreClass) {
        if (scoreClass != null) {
            for (Constructor constructor : scoreClass.getDeclaredConstructors()) {
                if (constructor.getGenericParameterTypes().length == 0) {
                    return constructor;
                }
            }
        }

        return null;
    }

    private static Constructor<? extends IndependenceWrapper> getTestOfIndependenceNoArgConstructor(Class<? extends IndependenceWrapper> indTestClass) {
        if (indTestClass != null) {
            for (Constructor constructor : indTestClass.getDeclaredConstructors()) {
                if (constructor.getGenericParameterTypes().length == 0) {
                    return constructor;
                }
            }
        }

        return null;
    }

    private static Constructor<? extends Algorithm> getAlgorithmNoArgConstructor(Class<? extends Algorithm> algoClass) {
        if (algoClass != null) {
            for (Constructor constructor : algoClass.getDeclaredConstructors()) {
                if (constructor.getGenericParameterTypes().length == 0) {
                    return constructor;
                }
            }
        }

        return null;
    }

}
