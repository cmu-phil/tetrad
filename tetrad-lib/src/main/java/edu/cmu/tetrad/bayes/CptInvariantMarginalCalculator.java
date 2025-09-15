///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Calculates marginals of the form P(V=v') for an updated Bayes net for purposes of the CPT Invariant Updater.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class CptInvariantMarginalCalculator
        implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Bayes net.
     */
    private final BayesIm bayesIm;
    /**
     * The evidence.
     */
    private final Evidence evidence;
    /**
     * The updated Bayes net.
     */
    private final UpdatedBayesIm updatedBayesIm;
    /**
     * The stored marginals.
     */
    private double[][] storedMarginals;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new marginal calculator for the given updated Bayes IM. It is assumed that the first BayesIm
     * encountered on calling the getParentIm() method recursively is the Bayes IM with respect to which conjunctions of
     * the form P(V1=v1' and V2=v2' and ... and Vn=vn') should be calculated.
     *
     * @param bayesIm  a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param evidence a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public CptInvariantMarginalCalculator(BayesIm bayesIm, Evidence evidence) {
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
     *
     * @return a {@link edu.cmu.tetrad.bayes.CptInvariantMarginalCalculator} object
     */
    public static CptInvariantMarginalCalculator serializableInstance() {
        MlBayesIm bayesIm = MlBayesIm.serializableInstance();
        Evidence evidence = Evidence.tautology(bayesIm);
        return new CptInvariantMarginalCalculator(bayesIm, evidence);
    }

    //=============================PUBLIC METHODS========================//

    /**
     * <p>getMarginal.</p>
     *
     * @param variable a int
     * @param category a int
     * @return P(variable = category).
     */
    public double getMarginal(int variable, int category) {
        if (this.storedMarginals[variable][category] != -99.0) {
            return this.storedMarginals[variable][category];
        }

        double marginal = 0.0;
        boolean foundANumber = false;

        for (int row = 0; row < this.bayesIm.getNumRows(variable); row++) {
            double probability =
                    this.updatedBayesIm.getProbability(variable, row, category);

            if (Double.isNaN(probability)) {
                continue;
            }

            double probabilityOfRow = getProbabilityOfRow(variable, row);

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

    /**
     * <p>Getter for the field <code>updatedBayesIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.UpdatedBayesIm} object
     */
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


    private double getProbabilityOfRow(int variable, int row) {
        int[] parents = this.bayesIm.getParents(variable);
        int[] parentValues = this.bayesIm.getParentValues(variable, row);

        double probabilityOfRow = 1.0;

        for (int index = 0; index < parents.length; index++) {

            if (noModifiedCpts(parents, index)) {
                double marginal =
                        getMarginal(parents[index], parentValues[index]);

                if (!Double.isNaN(marginal)) {
                    probabilityOfRow *= marginal;
                }
            } else {
                Evidence evidence = new Evidence(this.evidence);
                CptInvariantMarginalCalculator marginals =
                        new CptInvariantMarginalCalculator(this.bayesIm, evidence);
                double marginal = marginals.getMarginal(parents[index],
                        parentValues[index]);

                if (!Double.isNaN(marginal)) {
                    probabilityOfRow *= marginal;
                }
            }
        }

        return probabilityOfRow;
    }

    /**
     * @return true iff conditioning on parents 0 through i - 1 would change any of the conditional probability tables
     * relevant to calculating P(node i = value i).
     */
    private boolean noModifiedCpts(int[] parents, int i) {
        List<Node> target =
                Collections.singletonList(this.bayesIm.getNode(parents[i]));
        List<Node> conditioners = new LinkedList<>();

        for (int j = 0; j < i; j++) {
            conditioners.add(this.bayesIm.getNode(parents[j]));
        }

        List<Node> condAncestors = this.bayesIm.getDag().paths().getAncestors(conditioners);
        List<Node> targetAncestor = this.bayesIm.getDag().paths().getAncestors(target);
        Set<Node> intersection = new HashSet<>(condAncestors);
        intersection.retainAll(targetAncestor);

        return intersection.isEmpty();
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }


}






