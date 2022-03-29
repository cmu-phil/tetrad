package edu.cmu.tetrad.gene.tetrad.gene.graph;

import edu.cmu.tetrad.util.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;

public class LagGraphParams {
    static final long serialVersionUID = 23L;
    public static final int CONSTANT = 0;
    public static final int MAX = 1;
    public static final int MEAN = 2;
    private int indegreeType;
    private int varsPerInd = 5;
    private int mlag = 1;
    private int indegree = 2;
    private double percentUnregulated = 10;
    private final Parameters parameters;

    public LagGraphParams(final Parameters parameters) {
        this.parameters = parameters;
    }

    public static LagGraphParams serializableInstance() {
        return new LagGraphParams(new Parameters());
    }

    public int getVarsPerInd() {
        return this.parameters.getInt("lagGraphVarsPerInd", this.varsPerInd);
    }

    public void setVarsPerInd(final int varsPerInd) {
        if (varsPerInd > 0) {
            this.parameters.set("lagGraphVarsPerInd", varsPerInd);
            this.varsPerInd = varsPerInd;
        }

    }

    public int getMlag() {
        return this.parameters.getInt("lagGraphMlag", this.mlag);
    }

    public void setMlag(final int mlag) {
        if (mlag > 0) {
            this.parameters.set("lagGraphMLag", mlag);
            this.mlag = mlag;
        }

    }

    public int getIndegree() {
        return this.parameters.getInt("lagGraphIndegree", this.indegree);
    }

    public void setIndegree(final int indegree) {
        if (indegree > 1) {
            this.indegree = indegree;
            this.parameters.set("lagGraphIndegree", indegree);
        }

    }

    public int getIndegreeType() {
        return this.indegreeType;
    }

    public void setIndegreeType(final int indegreeType) {
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

    public double getPercentUnregulated() {
        return this.percentUnregulated;
    }

    public void setPercentUnregulated(final double percentUnregulated) {
        if (percentUnregulated >= 0.0D && percentUnregulated <= 100.0D) {
            this.percentUnregulated = percentUnregulated;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
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
