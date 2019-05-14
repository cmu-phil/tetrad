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

import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.util.IndependenceTestDescriptions;
import java.io.Serializable;

/**
 *
 * Dec 1, 2017 11:46:06 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class IndependenceTestModel implements Serializable, Comparable<IndependenceTestModel> {

    private static final long serialVersionUID = -6802442235695595011L;

    private final AnnotatedClass<TestOfIndependence> independenceTest;
    private final String name;
    private final String description;

    public IndependenceTestModel(AnnotatedClass<TestOfIndependence> independenceTest) {
        this.independenceTest = independenceTest;
        this.name = independenceTest.getAnnotation().name();
        this.description = IndependenceTestDescriptions.getInstance().get(independenceTest.getAnnotation().command());
    }

    @Override
    public int compareTo(IndependenceTestModel other) {
        return independenceTest.getAnnotation().name().compareTo(other.independenceTest.getAnnotation().name());
    }

    @Override
    public String toString() {
        return this.name;
    }

    public AnnotatedClass<TestOfIndependence> getIndependenceTest() {
        return independenceTest;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}
