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
package edu.cmu.tetrad.algcomparison.algorithm.description;

import edu.cmu.tetrad.annotation.AlgorithmDescription;

/**
 *
 * Aug 28, 2017 2:38:02 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @author Jeremy Espino MD Created 6/30/17 4:15 PM
 */
public class AlgorithmDescriptionClass {

    private final Class clazz;
    private final AlgorithmDescription algorithmDescription;
    private final boolean acceptMultipleDataset;
    private final boolean acceptKnowledge;
    private final boolean acceptInitalGraph;
    private final boolean requireIndependceTest;
    private final boolean requireScore;

    public AlgorithmDescriptionClass(Class clazz, AlgorithmDescription algorithmDescription, boolean acceptMultipleDataset, boolean acceptKnowledge, boolean acceptInitalGraph, boolean requireIndependceTest, boolean requireScore) {
        this.clazz = clazz;
        this.algorithmDescription = algorithmDescription;
        this.acceptMultipleDataset = acceptMultipleDataset;
        this.acceptKnowledge = acceptKnowledge;
        this.acceptInitalGraph = acceptInitalGraph;
        this.requireIndependceTest = requireIndependceTest;
        this.requireScore = requireScore;
    }

    public Class getClazz() {
        return clazz;
    }

    public AlgorithmDescription getAlgorithmDescription() {
        return algorithmDescription;
    }

    public boolean isAcceptMultipleDataset() {
        return acceptMultipleDataset;
    }

    public boolean isAcceptKnowledge() {
        return acceptKnowledge;
    }

    public boolean isAcceptInitalGraph() {
        return acceptInitalGraph;
    }

    public boolean isRequireIndependceTest() {
        return requireIndependceTest;
    }

    public boolean isRequireScore() {
        return requireScore;
    }

}
