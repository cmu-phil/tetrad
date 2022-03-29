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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Calculates marginals of the form P(V=v') for an updated Bayes net for
 * purposes of the CPT Invariant Updater.
 *
 * @author Joseph Ramsey
 */
public final class CptInvariantMarginalCalculator
        implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private final BayesIm bayesIm;

    /**
     * @serial Cannot be null.
     */
    private double[][] storedMarginals;

    /**
     * @serial Cannot be null.
     */
    private final Evidence evidence;

    /**
     * @serial Cannot be null.
     */
    private final UpdatedBayesIm updatedBayesIm;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new marginal calculator for the given updated Bayes IM. It
     * is assumed that the first BayesIm encountered on calling the
     * getParentIm() method recursively is the Bayes IM with respect to which
     * conjunctions of the form P(V1=v1' & V2=v2' & ... & Vn=vn') should be
     * calculated.
     */
    public CptInvariantMarginalCalculator(final BayesIm bayesIm, final Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(bayesIm)) {
            throw new IllegalArgumentException("The variables for the given " +
                    "Bayes IM and evidence must be compatible.");
        }

        this.bayesIm = bayesIm;
        this.evidence = evidence;
        this.updatedBayesIm = new UpdatedBayesIm(bayesIm, evidence);
        this.storedMarginals = initStoredMarginals();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static CptInvariantMarginalCalculator serializableInstance() {
        final MlBayesIm bayesIm = MlBayesIm.serializableInstance();
        final Evidence evidence = Evidence.tautology(bayesIm);
        return new CptInvariantMarginalCalculator(bayesIm, evidence);
    }

    //=============================PUBLIC METHODS========================//

    /**
     * @return P&lpar;variable &equals; category&rpar;.
     */
    public double getMarginal(final int variable, final int category) {
        if (this.storedMarginals[variable][category] != -99.0) {
            return this.storedMarginals[variable][category];
        }

        double marginal = 0.0;
        boolean foundANumber = false;

        for (int row = 0; row < this.bayesIm.getNumRows(variable); row++) {
            final double probability =
                    this.updatedBayesIm.getProbability(variable, row, category);

            if (Double.isNaN(probability)) {
                continue;
            }

            final double probabilityOfRow = getProbabilityOfRow(variable, row);

            if (Double.isNaN(probabilityOfRow)) {
                continue;
            }

            marginal += probability * probabilityOfRow;
            foundANumber = true;
        }

        if (!foundANumber) {
            marginal = Double.NaN;
        }

        this.storedMarginals[variable][category] = marginal;
        return marginal;
    }

    public UpdatedBayesIm getUpdatedBayesIm() {
        return this.updatedBayesIm;
    }

    private double[][] initStoredMarginals() {
        this.storedMarginals = new double[this.bayesIm.getNumNodes()][];

        for (int i = 0; i < this.bayesIm.getNumNodes(); i++) {
            this.storedMarginals[i] = new double[this.bayesIm.getNumColumns(i)];
            Arrays.fill(this.storedMarginals[i], -99.0);
        }
        return this.storedMarginals;
    }


    private double getProbabilityOfRow(final int variable, final int row) {
        final int[] parents = this.bayesIm.getParents(variable);
        final int[] parentValues = this.bayesIm.getParentValues(variable, row);

        double probabilityOfRow = 1.0;

        for (int index = 0; index < parents.length; index++) {

            if (noModifiedCpts(parents, index)) {
                final double marginal =
                        getMarginal(parents[index], parentValues[index]);

                if (!Double.isNaN(marginal)) {
                    probabilityOfRow *= marginal;
                }
            } else {
                final Evidence evidence = new Evidence(this.evidence);
                final CptInvariantMarginalCalculator marginals =
                        new CptInvariantMarginalCalculator(this.bayesIm, evidence);
                final double marginal = marginals.getMarginal(parents[index],
                        parentValues[index]);

                if (!Double.isNaN(marginal)) {
                    probabilityOfRow *= marginal;
                }
            }
        }

        return probabilityOfRow;
    }

    /**
     * @return true iff conditioning on parents 0 through i - 1 would change any
     * of the conditional probability tables relevant to calculating P(node i =
     * value i).
     */
    private boolean noModifiedCpts(final int[] parents, final int i) {
        final List<Node> target =
                Collections.singletonList(this.bayesIm.getNode(parents[i]));
        final List<Node> conditioners = new LinkedList<>();

        for (int j = 0; j < i; j++) {
            conditioners.add(this.bayesIm.getNode(parents[j]));
        }

        final List<Node> condAncestors = this.bayesIm.getDag().getAncestors(conditioners);
        final List<Node> targetAncestor = this.bayesIm.getDag().getAncestors(target);
        final Set<Node> intersection = new HashSet<>(condAncestors);
        intersection.retainAll(targetAncestor);

        return intersection.isEmpty();
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
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesIm == null) {
            throw new NullPointerException();
        }

        if (this.evidence == null) {
            throw new NullPointerException();
        }

        if (this.storedMarginals == null) {
            throw new NullPointerException();
        }

        if (this.updatedBayesIm == null) {
            throw new NullPointerException();
        }
    }
}





