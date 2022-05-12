package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

public final class IndependenceResult implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final IndependenceFact fact;
    private final boolean indep;
    private final double pValue;

    public IndependenceResult(IndependenceFact fact, boolean indep, double pValue) {
        this.fact = fact;
        this.indep = indep;
        this.pValue = pValue;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static IndependenceResult serializableInstance() {
        return new IndependenceResult(new IndependenceFact(
                new ContinuousVariable("X"), new ContinuousVariable("Y")),
                true, 0.0001);
    }

    public IndependenceFact getFact() {
        return this.fact;
    }

    public boolean independent() {
        return this.indep;
    }

    public double getPValue() {
        return this.pValue;
    }

    public String toString() {
        return "Result: " + getFact() + "\t" + independent() + "\t" +
                NumberFormatUtil.getInstance().getNumberFormat().format(getPValue());
    }

    public boolean dependent() {
        return !independent();
    }
}
