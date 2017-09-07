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

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.graph.Graph;

/**
 *
 * Aug 30, 2017 3:14:40 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmFactory {

    private AlgorithmFactory() {
    }

    public static Algorithm create(Class<? extends Algorithm> algoClass, IndependenceWrapper test, ScoreWrapper score)
            throws IllegalAccessException, InstantiationException {
        if (algoClass == null) {
            throw new IllegalArgumentException("Algorithm class cannot be null.");
        }

        AlgorithmAnnotations algoAnno = AlgorithmAnnotations.getInstance();
        boolean testRequired = algoAnno.requireIndependenceTest(algoClass);
        if (testRequired && test == null) {
            throw new IllegalArgumentException("Test of independence is required.");
        }

        boolean scoreRequired = algoAnno.requireScore(algoClass);
        if (scoreRequired && score == null) {
            throw new IllegalArgumentException("Score is required.");
        }

        Algorithm algorithm = algoClass.newInstance();
        if (testRequired) {
            ((TakesIndependenceWrapper) algorithm).setIndependenceWrapper(test);
        }
        if (scoreRequired) {
            ((UsesScoreWrapper) algorithm).setScoreWrapper(score);
        }

        return algorithm;
    }

    public static Algorithm create(Class<? extends Algorithm> algoClass, IndependenceWrapper test, ScoreWrapper score, Graph initialGraph)
            throws IllegalAccessException, InstantiationException {
        Algorithm algorithm = create(algoClass, test, score);
        if (initialGraph != null && algorithm instanceof TakesInitialGraph) {
            ((TakesInitialGraph) algorithm).setInitialGraph(initialGraph);
        }

        return algorithm;
    }

    public static Algorithm create(Class<? extends Algorithm> algoClass, Class<? extends IndependenceWrapper> indTestClass, Class<? extends ScoreWrapper> scoreClass)
            throws IllegalAccessException, InstantiationException {
        if (algoClass == null) {
            throw new IllegalArgumentException("Algorithm class cannot be null.");
        }

        IndependenceWrapper test = (indTestClass == null) ? null : indTestClass.newInstance();
        ScoreWrapper score = (scoreClass == null) ? null : scoreClass.newInstance();

        return create(algoClass, test, score);
    }

    public static Algorithm create(Class<? extends Algorithm> algoClass, Class<? extends IndependenceWrapper> indTestClass, Class<? extends ScoreWrapper> scoreClass, Graph initialGraph)
            throws IllegalAccessException, InstantiationException {
        Algorithm algorithm = create(algoClass, indTestClass, scoreClass);
        if (initialGraph != null && algorithm instanceof TakesInitialGraph) {
            ((TakesInitialGraph) algorithm).setInitialGraph(initialGraph);
        }

        return algorithm;
    }

}
