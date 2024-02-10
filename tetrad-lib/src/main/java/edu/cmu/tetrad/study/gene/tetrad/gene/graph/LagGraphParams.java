package edu.cmu.tetrad.study.gene.tetrad.gene.graph;

import edu.cmu.tetrad.util.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * <p>LagGraphParams class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LagGraphParams {
    /**
     * Constant <code>CONSTANT=0</code>
     */
    public static final int CONSTANT = 0;
    /**
     * Constant <code>MAX=1</code>
     */
    public static final int MAX = 1;
    /**
     * Constant <code>MEAN=2</code>
     */
    public static final int MEAN = 2;
    private static final long serialVersionUID = 23L;
    private final Parameters parameters;
    private int indegreeType;
    private int varsPerInd = 5;
    private int mlag = 1;
    private int indegree = 2;
    private double percentUnregulated = 10;

    /**
     * <p>Constructor for LagGraphParams.</p>
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public LagGraphParams(Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.graph.LagGraphParams} object
     */
    public static LagGraphParams serializableInstance() {
        return new LagGraphParams(new Parameters());
    }

    /**
     * <p>Getter for the field <code>varsPerInd</code>.</p>
     *
     * @return a int
     */
    public int getVarsPerInd() {
        return this.parameters.getInt("lagGraphVarsPerInd", this.varsPerInd);
    }

    /**
     * <p>Setter for the field <code>varsPerInd</code>.</p>
     *
     * @param varsPerInd a int
     */
    public void setVarsPerInd(int varsPerInd) {
        if (varsPerInd > 0) {
            this.parameters.set("lagGraphVarsPerInd", varsPerInd);
            this.varsPerInd = varsPerInd;
        }

    }

    /**
     * <p>Getter for the field <code>mlag</code>.</p>
     *
     * @return a int
     */
    public int getMlag() {
        return this.parameters.getInt("lagGraphMlag", this.mlag);
    }

    /**
     * <p>Setter for the field <code>mlag</code>.</p>
     *
     * @param mlag a int
     */
    public void setMlag(int mlag) {
        if (mlag > 0) {
            this.parameters.set("lagGraphMLag", mlag);
            this.mlag = mlag;
        }

    }

    /**
     * <p>Getter for the field <code>indegree</code>.</p>
     *
     * @return a int
     */
    public int getIndegree() {
        return this.parameters.getInt("lagGraphIndegree", this.indegree);
    }

    /**
     * <p>Setter for the field <code>indegree</code>.</p>
     *
     * @param indegree a int
     */
    public void setIndegree(int indegree) {
        if (indegree > 1) {
            this.indegree = indegree;
            this.parameters.set("lagGraphIndegree", indegree);
        }

    }

    /**
     * <p>Getter for the field <code>indegreeType</code>.</p>
     *
     * @return a int
     */
    public int getIndegreeType() {
        return this.indegreeType;
    }

    /**
     * <p>Setter for the field <code>indegreeType</code>.</p>
     *
     * @param indegreeType a int
     */
    public void setIndegreeType(int indegreeType) {
        switch (indegreeType) {
            case 0:
            case 1:
            case 2:
                this.indegreeType = indegreeType;
                return;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * <p>Getter for the field <code>percentUnregulated</code>.</p>
     *
     * @return a double
     */
    public double getPercentUnregulated() {
        return this.percentUnregulated;
    }

    /**
     * <p>Setter for the field <code>percentUnregulated</code>.</p>
     *
     * @param percentUnregulated a double
     */
    public void setPercentUnregulated(double percentUnregulated) {
        if (percentUnregulated >= 0.0D && percentUnregulated <= 100.0D) {
            this.percentUnregulated = percentUnregulated;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        switch (this.indegreeType) {
            case 0:
            case 1:
            case 2:
                if (this.varsPerInd < 1) {
                    throw new IllegalStateException("VarsPerInd out of range: " + this.varsPerInd);
                } else if (this.mlag <= 0) {
                    throw new IllegalStateException("Mlag out of range: " + this.mlag);
                } else if (this.varsPerInd <= 1) {
                    throw new IllegalStateException("VarsPerInd out of range: " + this.varsPerInd);
                } else {
                    if (this.percentUnregulated > 0.0D && this.percentUnregulated < 100.0D) {
                        return;
                    }

                    throw new IllegalStateException("PercentUnregulated out of range: " + this.percentUnregulated);
                }
            default:
                throw new IllegalStateException("Illegal indegree type: " + this.indegreeType);
        }
    }
}
