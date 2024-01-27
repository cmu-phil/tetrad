package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Stores a single conditional independence result, e.g., whether X _||_ Y | Z1,..,Zn holds or does not, and the p-value
 * of the test.
 *
 * @author josephramsey
 */
public final class IndependenceResult implements TetradSerializable {
    private static final long serialVersionUID = 23L;

    private final IndependenceFact fact;
    private final boolean indep;
    private final double pValue;
    private final double score;
    private final boolean isValid;

    /**
     * Constructor. For this constructor, is it assumed that the test is valid.
     *
     * @param fact   The fact itself.
     * @param indep  The conditional independence result, true if the fact holds, false if not.
     * @param pValue The p-values of the independence result, under the null (independence) hypothesis.
     * @param score  The score of the test, which is alpha - p if the test returns a p-value or else a bump if the test
     *               is based on a score.
     * @see IndependenceFact
     */
    public IndependenceResult(IndependenceFact fact, boolean indep, double pValue, double score) {
        this(fact, indep, pValue, score, true);
    }

    /**
     * Constructor. For this constructor, the validity of the test is specified.
     *
     * @param fact    The fact itself.
     * @param indep   The conditional independence result, true if the fact holds, false if not.
     * @param pValue  The p-values of the independence result, under the null (independence) hypothesis.
     * @param score   The score of the test, which is alpha - p if the test returns a p-value or else a bump if the test
     *                is based on a score.
     * @param isValid Whether the result is valid or not. A test is not valid if the test is not able to determine
     *                whether the fact holds or not.
     * @see IndependenceFact
     */
    public IndependenceResult(IndependenceFact fact, boolean indep, double pValue, double score, boolean isValid) {
        this.fact = fact;
        this.indep = indep;
        this.pValue = pValue;
        this.score = score;
        this.isValid = isValid;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static IndependenceResult serializableInstance() {
        return new IndependenceResult(new IndependenceFact(
                new ContinuousVariable("X"), new ContinuousVariable("Y")),
                true, 0.0001, 1.0);
    }

    /**
     * Returns the independence fact being stored.
     *
     * @return This fact
     * @see IndependenceFact
     */
    public IndependenceFact getFact() {
        return this.fact;
    }

    /**
     * Returns whether the fact holds--i.e., if the judgment is for independence.
     *
     * @return True if the fact holds, false if ot.
     * @see #isDependent()
     */
    public boolean isIndependent() {
        return this.indep;
    }

    /**
     * Returns whether the fact fails to hold--i.e., if the judgment is for dependence. This is the negation of
     * isIndependent.
     *
     * @return True if the fact does not, false if it does.
     * @see #isIndependent()
     */
    public boolean isDependent() {
        return !isIndependent();
    }

    /**
     * Returns the p-value of the fact under the null hypothesis of independence. A special case obtains if this fact is
     * being used to store a d-separation fact, in which case the "p-value" is deemed to be 0 if the fact holds and 1 if
     * it does not.
     *
     * @return The p-value of the result under the null hypothesis.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns a string representation of this independence fact.
     *
     * @return This string.
     */
    public String toString() {
        return "Result: " + getFact() + "\t" + isIndependent() + "\t" +
                NumberFormatUtil.getInstance().getNumberFormat().format(getPValue());
    }

    /**
     * Returns the score of the test, which is alpha - p if the test returns a p-value or else a bump if the test is
     * based on a score.
     *
     * @return The score of the test.
     */
    public double getScore() {
        return score;
    }

    /**
     * Returns whether the result is valid or not. A test is not valid if the test is not able to determine whether the
     * fact holds or not.
     *
     * @return True if the result is valid, false if not.
     */
    public boolean isValid() {
        return isValid;
    }
}
