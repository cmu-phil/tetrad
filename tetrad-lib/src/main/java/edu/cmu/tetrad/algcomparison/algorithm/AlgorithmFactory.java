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

import edu.cmu.tetrad.algcomparison.algorithm.description.AlgorithmDescriptionClass;
import edu.cmu.tetrad.algcomparison.algorithm.description.AlgorithmDescriptions;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
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

    public static Algorithm create(String name, IndependenceWrapper test, ScoreWrapper score)
            throws IllegalAccessException, InstantiationException {
        AlgorithmDescriptions algoDescs = AlgorithmDescriptions.getInstance();
        AlgorithmDescriptionClass algoDescClass = algoDescs.get(name);
        if (algoDescClass == null) {
            throw new IllegalArgumentException("No such algorithm found.");
        }
        if (algoDescClass.isRequireIndependceTest() && test == null) {
            throw new IllegalArgumentException("Test of independence is required.");
        }
        if (algoDescClass.isRequireScore() && score == null) {
            throw new IllegalArgumentException("Score is required.");
        }

        Algorithm algorithm = (Algorithm) algoDescClass.getClazz().newInstance();
        if (algoDescClass.isRequireIndependceTest()) {
            ((TakesIndependenceWrapper) algorithm).setIndependenceWrapper(test);
        }
        if (algoDescClass.isRequireScore() && score == null) {
            ((UsesScoreWrapper) algorithm).setScoreWrapper(score);
        }

        return algorithm;
    }

    public static Algorithm create(String name, IndependenceWrapper test, ScoreWrapper score, Graph initialGraph)
            throws IllegalAccessException, InstantiationException {
        Algorithm algorithm = create(name, test, score);
        if (initialGraph != null && algorithm instanceof TakesIndependenceWrapper) {
            ((TakesIndependenceWrapper) algorithm).setIndependenceWrapper(test);
        }

        return algorithm;
    }

}
