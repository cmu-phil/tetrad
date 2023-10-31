package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * <p>Stores a single conditional independence result, e.g., whether
 * X _||_ Y | Z1,..,Zn holds or does not, and the p-value of the test.</p>
 *
 * @author josephramsey
 */
public final class IndependenceResult implements TetradSerializable {
    private static final long serialVersionUID = 23L;

    private final IndependenceFact fact;
    private final boolean indep;
    private final double pValue;
    private final double score;

    /**
     * Constructor.
     *
     * @param fact   The fact itself.
     * @param indep  The conditional independence result, true if the fact holds, false if not.
     * @param pValue The p-values of the independence result, under the null (independence) hypothesis.
     * @see IndependenceFact
     */
    public IndependenceResult(IndependenceFact fact, boolean indep, double pValue, double score) {
        this.fact = fact;
        this.indep = indep;
        this.pValue = pValue;
        this.score = score;
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
     * Returns a string represnetation of this independence fact.
     *
     * @return This string.
     */
    public String toString() {
        return "Result: " + getFact() + "\t" + isIndependent() + "\t" +
                NumberFormatUtil.getInstance().getNumberFormat().format(getPValue());
    }

    public double getScore() {
        return score;
    }
}
