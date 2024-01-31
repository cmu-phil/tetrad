///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface for a discrete Bayes updating algorithm. The main task of such and algorithm is to calculate P(X = x' |
 * evidence), where evidence takes the form of a Proposition over the variables in the Bayes net, possibly with
 * additional information about which variables in the Bayes net have been manipulated. Some updaters may be able to
 * calculate joint marginals as well--that is, P(AND_i{Xi = xi'} | evidence). Also, not all updaters can take
 * manipulation information into account. See implementations for details.)
 *
 * @author josephramsey
 * @see Evidence
 * @see Proposition
 * @see Manipulation
 */
public interface BayesUpdater extends TetradSerializable {
    long serialVersionUID = 23L;

    /**
     * Returns the marginal probability of the given variable taking the given value, given the evidence.
     *
     * @param variable variable index
     * @param category category index
     * @return P(variable = value | evidence), where evidence is getEvidence().
     */
    double getMarginal(int variable, int category);

    /**
     * Returns the joint marginal probability of the given variables taking the given values, given the evidence.
     *
     * @return true if the getJointMarginal() method is supported.
     */
    boolean isJointMarginalSupported();

    /**
     * Returns the joint marginal probability of the given variables taking the given values, given the evidence.
     *
     * @param variables variable indices
     * @param values    category indices
     * @return P(variables[i] = values[i] | evidence), where evidence is getEvidence().
     */
    double getJointMarginal(int[] variables, int[] values);

    /**
     * Sets new evidence for the updater. Once this is called, old updating results should not longer be available.
     *
     * @param evidence evidence
     */
    void setEvidence(Evidence evidence);

    /**
     * Returns the evidence for the updater.
     *
     * @return the Bayes instantiated model that is being updated.
     */
    BayesIm getBayesIm();

    /**
     * Calculates the prior marginal probabilities of the given node.
     *
     * @param nodeIndex node index
     * @return P(node = value), where value is the value of the node in the Bayes net.
     */
    double[] calculatePriorMarginals(int nodeIndex);

    /**
     * Calculates the updated marginal probabilities of the given node, given the evidence.
     *
     * @param nodeIndex node index
     * @return P(node = value | evidence), where value is the value of the node in the Bayes net.
     */
    double[] calculateUpdatedMarginals(int nodeIndex);
}





