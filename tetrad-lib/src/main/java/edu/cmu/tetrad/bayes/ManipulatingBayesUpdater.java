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

import edu.cmu.tetrad.graph.Graph;

/**
 * Interface for a Bayes updating algorithm that's capable of doing manipulation. In general, manipulating a variable X
 * will eliminate edges into X, so the updating operation on the manipulated model will produce different results.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ManipulatingBayesUpdater extends BayesUpdater {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Returns the manipulated Bayes IM. This is the Bayes IM in which the variables in the manipulation have been
     * removed from the graph.
     *
     * @return the Bayes instantiated model after manipulations have been applied.
     */
    BayesIm getManipulatedBayesIm();

    /**
     * Returns the manipulated graph. This is the graph in which the variables in the manipulation have been removed
     * from the graph.
     *
     * @return the graph for the manipulated BayesIm.
     */
    Graph getManipulatedGraph();

    /**
     * Returns the manipulation that was used to manipulate the Bayes IM.
     *
     * @return a defensive copy of the evidence.
     */
    Evidence getEvidence();

    /**
     * {@inheritDoc}
     * <p>
     * Sets new evidence for the updater. Once this is called, old updating results should not longer be available.
     */
    void setEvidence(Evidence evidence);

    /**
     * Returns the updated Bayes IM. This is the Bayes IM in which all probabilities of variables conditional on their
     * parents have been updated.
     *
     * @return the updated Bayes IM--that is, the Bayes IM in which all probabilities of variables conditional on their
     * parents have been updated.
     */
    BayesIm getUpdatedBayesIm();

    /**
     * {@inheritDoc}
     * <p>
     * Returns the updated graph. This is the graph in which all probabilities of variables conditional on their parents
     * have been updated.
     */
    double getMarginal(int variable, int category);

}






