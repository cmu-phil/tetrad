///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Updates a gene given a history using the formula Gi.0 = max(Gi.1 -
 * decayRate * -Gi.1 + booleanInfluenceRate * F(Parents(Gi) in the graph \
 * Gi.1), lowerBound), as described in Edwards and Glass, (2000), "Combinatorial
 * explosion in model gene networks", American Institute of Physics. F is a
 * function from R^n to R, where each input to the function is sent to -1.0 if
 * it is < 0.0 and +1.0 if is it >= 0.0 and the combination of -1.0's and +1.0's
 * is then used to look up a value in a boolean table.  The output of the
 * function is -1.0 or 1.0. A random boolean Glass fuction is a boolean Glass
 * function in which the boolean lookup table is chosen randomly.  The procedure
 * used here is as follows.  For each factor fi, the lag graph supplied in the
 * constructor specifies a set of causal parents among the lagged factors.  If
 * fi:1 appears as a causal parent, it is removed from the set.  The result is a
 * set of n causal parents. A random boolean function is constructed for these n
 * causal parents (with 2^n rows) for which each causal parent is
 * "effective"--that is, for which there is some combination of the other causal
 * parents for which the lookup table maps either to true or to false depending
 * on the value of the given causal parent.</p> </p> <p>The basal expression
 * level is used in these functions as a threshold, above which a lookup
 * value of <code>true</code> is used for the boolean tables and below which
 * a lookup value of <code>false</code> is used. A return value of
 * <code>true</code> from the boolean lookup is then mapped to some double
 * value, which we call the the "true value," and a return value of
 * <code>false</code> is mapped to some other (lesser) double value, which we
 * call the "false value." The authors allow for the possibility of setting
 * the basal expression to 0.5 and using 0.0 as the false value and 1.0 as
 * the true value. Generalizing, we include a constructor to allow the
 * basalExpression, true value and false value to be set by the user, with
 * the only condition being that the false value must be less than the true
 * value.</p>
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BooleanGlassFunction implements UpdateFunction {
    static final long serialVersionUID = 23L;

    /**
     * The indexed connectivity "snapshot" of the lag graph.
     *
     * @serial
     */
    private IndexedLagGraph connectivity;

    /**
     * Stores a boolean function for each factor from a preselected set of
     * lagged factors to the given factor.
     *
     * @serial
     */
    private BooleanFunction[] booleanFunctions;

    /**
     * Error distributions from which errors are drawn for each of the factors.
     *
     * @serial
     */
    private Distribution[] errorDistributions;

    /**
     * The lower bound for expression levels. Expression levels that wander
     * below this bound will be set to this bound.
     *
     * @serial
     */
    private double lowerBound;

    /**
     * The basalExpression for determining whether history expression levels
     * should be mapped to "true" or "false" for purposes of looking up output
     * values in the Boolean function table.
     *
     * @serial
     */
    private double basalExpression;

    /**
     * The real number that is returned if the value from the Boolean lookup
     * table is "true". Must be > basalExpression.
     *
     * @serial
     */
    private double trueValue = +1.0;

    /**
     * The real number that is returned if the value from the Boolean lookup
     * table is "false". Must be < basalExpression.
     *
     * @serial
     */
    private double falseValue = -1.0;

    /**
     * The rate at which expression levels for a gene tend to return to basal
     * level.
     *
     * @serial
     */
    private double decayRate;

    /**
     * The rate at which the F function (with outputs -1 and +1) affects the
     * update for a gene.
     *
     * @serial
     */
    private double booleanInfluenceRate;

    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a new random boolean Glass function using the given lag graph,
     * with a basalExpression of 0.0, a true value of +1.0 and a false
     * value of -1.0. This is the function described in Edwards and Glass's
     * original paper.
     *
     * @param lagGraph the lag graph, specifying for each factor in the graph
     *                 which lagged factors are causal factors of it in the
     *                 updating.
     */
    public BooleanGlassFunction(LagGraph lagGraph) {
        this(lagGraph, Double.NEGATIVE_INFINITY, 0.0);
    }

    /**
     * Constructs a new random boolean Glass function using the given lag graph,
     * lower bound, and basalExpression.
     *
     * @param lagGraph        The lag graph, specifying for each factor in the
     *                        graph which lagged factors are causal factors of
     *                        it in the updating.
     * @param lowerBound      the lower bound cutoff for expression levels.
     * @param basalExpression The basalExpression for determining whether
     *                        history expression levels should be mapped to
     *                        "true" or "false" for purposes of looking up
     *                        output values in the Boolean function table.
     */
    public BooleanGlassFunction(LagGraph lagGraph, double lowerBound,
                                double basalExpression) {

        if (lagGraph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (lowerBound >= basalExpression) {
            throw new IllegalArgumentException("Lower bound must be " +
                    "less than basal " + "expression.");
        }

        this.lowerBound = lowerBound;
        this.basalExpression = basalExpression;

        // Set up connectivity with the lag graph, excluding from the
        // connectivity of each gene the same gene one time step back.
        this.connectivity = new IndexedLagGraph(lagGraph, true);

        // Set up error distributions.
        this.errorDistributions =
                new Distribution[this.connectivity.getNumFactors()];

        for (int i = 0; i < errorDistributions.length; i++) {
            errorDistributions[i] = new Normal(0.0, 0.05);
        }

        // Construct a new random Boolean function for each factor
        // from the parents of that factor (factors at time lag > 0)
        // to the factor at time lag 0. The function for each factor
        // is chosen to be effective and canalyzing.
        this.booleanFunctions =
                new BooleanFunction[this.connectivity.getNumFactors()];

        for (int i = 0; i < booleanFunctions.length; i++) {
            if (this.connectivity.getNumParents(i) > 0) {

                // The parents of the boolean function have to be
                // IndexedParent's and cannot include the factor
                // itself one time step back.
                List parentList = new ArrayList();
                for (int j = 0; j < this.connectivity.getNumParents(i); j++) {
                    IndexedParent parent = this.connectivity.getParent(i, j);
                    parentList.add(parent);
                }

                IndexedParent[] parents = (IndexedParent[]) parentList.toArray(
                        new IndexedParent[0]);
                booleanFunctions[i] = new BooleanFunction(parents);

                do {
                    booleanFunctions[i].randomize();
                } while (!booleanFunctions[i].isEffective());
            } else {
                booleanFunctions[i] = null;
            }
        }

        // Set default rate constants.
        setDecayRate(0.1);
        setBooleanInfluenceRate(0.5);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static BooleanGlassFunction serializableInstance() {
        return new BooleanGlassFunction(BasicLagGraph.serializableInstance());
    }

    //===============================PUBLIC METHODS========================//

    /**
     * Returns the indexed connectivity.
     */
    public IndexedLagGraph getIndexedLagGraph() {
        return this.connectivity;
    }

    /**
     * Returns the basalExpression.
     */
    public double getBasalExpression() {
        return this.basalExpression;
    }

    /**
     * Sets the basalExpression.
     */
    public void setBasalExpression(double basalExpression) {
        this.basalExpression = basalExpression;
    }

    /**
     * Returns the value of the function.
     */
    public double getValue(int factor, double[][] history) {
        // 2/15/02: Cutuff expression levels at the low
        // end. Note that the old simulation can be recovered
        // by initializing using a Normal(0, 1) and setting
        // the lower bound to Double.NEGATIVE_INFINITY. J
        // Ramsey 2/22/02
        double v0 = history[1][factor];
        double v1 = -decayRate * (v0 - this.basalExpression);
        double v2 = booleanInfluenceRate * getFValue(factor, history);
        double v3 = errorDistributions[factor].nextRandom();
        double v4 = v0 + v1 + v2 + v3;
        double v5 = Math.max(this.lowerBound, v4);

        return v5;
    }

    /**
     * Calculates the new value for the F function, for the given factor, in
     * light of the given history. We return a 1.0 or a -1.0 depending on
     * whether the boolean value from the lookup
     * table is <pre>true</pre> or <pre>false</pre>. We choose these
     * return values because we are using a basalExpression value of 0.0. (If
     * a basalExpression of 0.5 were used, the values 0.0 and 1.0 would be more
     * appropriate.)
     *
     * @param factor  the index of the factor to calculate a new value for.
     * @param history the history using which the new value is to be
     *                calculated.
     * @return 1.0 or -1.0 depending on whether the boolean value
     *         looked up in the table is <pre>true</pre> or <pre>false</pre>.
     */
    public double getFValue(int factor, double[][] history) {
        if (booleanFunctions[factor] == null) {
            return 0.0;
        } else {

            // Lookup the boolean function value for that combination
            // of boolean parent values in the boolean function for
            // that factor.
            BooleanFunction booleanFunction = booleanFunctions[factor];
            Object[] parents = booleanFunction.getParents();
            boolean[] parentValues = new boolean[parents.length];
            for (int i = 0; i < parentValues.length; i++) {
                IndexedParent parent = (IndexedParent) parents[i];
                double histVal = history[parent.getLag()][parent.getIndex()];
                parentValues[i] = histVal > this.basalExpression ? true : false;
            }

            int row = booleanFunction.getRow(parentValues);
            boolean functionValue = booleanFunction.getValue(row);

            // We return 1.0 and -1.0 because we use a basalExpression of
            // 0.0. (If the basalExpression were 1/2, we would use 1 and 0.)
            return functionValue ? this.trueValue : this.falseValue;
        }
    }

    /**
     * Returns the boolean function for the given factor.
     */
    public BooleanFunction getSubFunction(int factor) {
        return booleanFunctions[factor];
    }

    /**
     * Returns the rate at which expression levels tend to return to
     * equilibrium.
     */
    public double getDecayRate() {
        return decayRate;
    }

    /**
     * Returns the rate at which Boolean Glass subfunctions tend to affect the
     * update.
     */
    public double getBooleanInfluenceRate() {
        return booleanInfluenceRate;
    }

    /**
     * Sets the rate at which expression levels tend to return to equilibrium.
     * Must be > 0.0 and <= 1.0.
     */
    public void setDecayRate(double decayRate) {

        if ((decayRate <= 0.0) || (decayRate > 1.0)) {
            throw new IllegalArgumentException(
                    "Suggested rate out of bounds (0.0 <= decayRate < 1.0): " +
                            decayRate);
        }

        this.decayRate = decayRate;
    }

    /**
     * Sets the rate at which the output of the Glass function influences the
     * change in expression level of a gene. Must be > 0.0.
     */
    public void setBooleanInfluenceRate(double booleanInfluenceRate) {

        if (booleanInfluenceRate <= 0.0) {
            throw new IllegalArgumentException(
                    "Suggested rate out of bounds (0.0 <= " +
                            "booleanInfluenceRate): " + booleanInfluenceRate);
        }

        this.booleanInfluenceRate = booleanInfluenceRate;
    }

    /**
     * Sets the lower bound for expression levels.
     */
    public void setLowerBound(double lowerBound) {
        this.lowerBound = lowerBound;
    }

    /**
     * Method setIntenalNoiseModel
     *
     * @param factor
     * @param distribution
     */
    public void setErrorDistribution(int factor, Distribution distribution) {

        if (distribution != null) {
            errorDistributions[factor] = distribution;
        } else {
            throw new NullPointerException();
        }
    }

    /**
     * Returns the error distribution for the <code>factor</code>'th factor.
     *
     * @param factor the factor in question.
     * @return the error distribution for <code>factor</code>.
     */
    public Distribution getErrorDistribution(int factor) {
        return errorDistributions[factor];
    }

    /**
     * Returns the number of factors in the history. This is used to set up the
     * initial history array.
     */
    public int getNumFactors() {
        return this.connectivity.getNumFactors();
    }

    /**
     * Returns the max lag of the history. This is used to set up the initial
     * history array.
     */
    public int getMaxLag() {
        int maxLag = 0;
        for (int i = 0; i < connectivity.getNumFactors(); i++) {
            for (int j = 0; j < connectivity.getNumParents(i); j++) {
                IndexedParent parent = connectivity.getParent(i, j);
                if (parent.getLag() > maxLag) {
                    maxLag = parent.getLag();
                }
            }
        }
        return maxLag;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (connectivity == null) {
            throw new NullPointerException();
        }

        if (booleanFunctions == null) {
            throw new NullPointerException();
        }

        if (errorDistributions == null) {
            throw new NullPointerException();
        }

        if (lowerBound >= basalExpression) {
            throw new IllegalStateException();
        }

        if ((decayRate <= 0.0) || (decayRate > 1.0)) {
            throw new IllegalStateException();
        }

        if (booleanInfluenceRate <= 0.0) {
            throw new IllegalStateException();
        }
    }
}





