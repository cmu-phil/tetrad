package edu.cmu.tetradapp.ui.model;

import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.util.IndependenceTestDescriptions;

import java.io.Serial;
import java.io.Serializable;

/**
 * Dec 1, 2017 11:46:06 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class IndependenceTestModel implements Serializable, Comparable<IndependenceTestModel> {

    @Serial
    private static final long serialVersionUID = -6802442235695595011L;

    /**
     * The annotated class of the independence test.
     */
    private final AnnotatedClass<TestOfIndependence> independenceTest;

    /**
     * The name and description of the independence test.
     */
    private final String name;

    /**
     * The description of the independence test.
     */
    private final String description;

    /**
     * <p>Constructor for IndependenceTestModel.</p>
     *
     * @param independenceTest a {@link edu.cmu.tetrad.annotation.AnnotatedClass} object
     */
    public IndependenceTestModel(AnnotatedClass<TestOfIndependence> independenceTest) {
        this.independenceTest = independenceTest;
        this.name = independenceTest.annotation().name();
        this.description = IndependenceTestDescriptions.getInstance().get(independenceTest.annotation().command());
    }

    /**
     * Compares this IndependenceTestModel object with the specified IndependenceTestModel object based on the name of
     * the tests.
     *
     * @param other the IndependenceTestModel object to be compared
     * @return a negative integer, zero, or a positive integer as this IndependenceTestModel is less than, equal to, or
     * greater than the specified IndependenceTestModel
     */
    @Override
    public int compareTo(IndependenceTestModel other) {
        return this.independenceTest.annotation().name().compareTo(other.independenceTest.annotation().name());
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
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.annotation.AnnotatedClass} object
     */
    public AnnotatedClass<TestOfIndependence> getIndependenceTest() {
        return this.independenceTest;
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
}
