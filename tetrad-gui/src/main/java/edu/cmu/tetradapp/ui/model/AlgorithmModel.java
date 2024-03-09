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
package edu.cmu.tetradapp.ui.model;

import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.Algorithm;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.util.AlgorithmDescriptions;

import java.io.Serializable;

/**
 * Nov 30, 2017 4:41:37 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class AlgorithmModel implements Serializable, Comparable<AlgorithmModel> {

    private static final long serialVersionUID = 8599854464475682558L;

    /**
     * The annotated class of the algorithm.
     */
    private final AnnotatedClass<Algorithm> algorithm;

    /**
     * The name and description of the algorithm.
     */
    private final String name;

    /**
     * The description of the algorithm.
     */
    private final String description;

    /**
     * Whether the algorithm requires a score.
     */
    private final boolean requiredScore;

    /**
     * Whether the algorithm requires an independence test.
     */
    private final boolean requiredTest;

    /**
     * <p>Constructor for AlgorithmModel.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.annotation.AnnotatedClass} object
     */
    public AlgorithmModel(AnnotatedClass<Algorithm> algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm annotation cannot be null.");
        }

        this.algorithm = algorithm;
        this.name = algorithm.annotation().name();
        this.description = AlgorithmDescriptions.getInstance().get(algorithm.annotation().command());
        this.requiredScore = UsesScoreWrapper.class.isAssignableFrom(algorithm.clazz());
        this.requiredTest = TakesIndependenceWrapper.class.isAssignableFrom(algorithm.clazz());
    }

    /**
     * Compares this AlgorithmModel object with the specified AlgorithmModel object for order. The objects are compared based on their name.
     *
     * @param other the object to be compared
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(AlgorithmModel other) {
        return this.name.compareTo(other.name);
    }

    /**
     * Returns a string representation of the object.
     *
     * @return the name of the object
     */
    @Override
    public String toString() {
        return this.name;
    }

    /**
     * <p>Getter for the field <code>algorithm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.annotation.AnnotatedClass} object
     */
    public AnnotatedClass<Algorithm> getAlgorithm() {
        return this.algorithm;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * <p>isRequiredScore.</p>
     *
     * @return a boolean
     */
    public boolean isRequiredScore() {
        return this.requiredScore;
    }

    /**
     * <p>isRequiredTest.</p>
     *
     * @return a boolean
     */
    public boolean isRequiredTest() {
        return this.requiredTest;
    }

}
