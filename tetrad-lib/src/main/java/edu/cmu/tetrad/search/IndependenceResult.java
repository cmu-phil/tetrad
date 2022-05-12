package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

public final class IndependenceResult implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final String fact;
    private final boolean indep;
    private final double pValue;

    public IndependenceResult(String fact, boolean indep, double pValue) {
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
        return new IndependenceResult("X _||_ Y", true, 0.0001);
    }

    public String getFact() {
        return this.fact;
    }

    public boolean independent() {
        return this.indep;
    }

    public double getpValue() {
        return this.pValue;
    }

    public String toString() {
        return "Result: " + getFact() + "\t" + independent() + "\t" +
                NumberFormatUtil.getInstance().getNumberFormat().format(getpValue());
    }
}
