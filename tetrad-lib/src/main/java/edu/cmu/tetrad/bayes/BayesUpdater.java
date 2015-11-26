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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface for a discrete Bayes updating algorithm. The main task of such and
 * algorithm is to calculate P(X = x' | evidence), where evidence takes the form
 * of a Proposition over the variables in the Bayes net, possibly with
 * additional information about which variables in the Bayes net have been
 * manipulated. Some updaters may be able to calculate joint marginals as
 * well--that is, P(AND_i{Xi = xi'} | evidence). Also, not all updaters can take
 * manipulation information into account. See implementations for details.
 *
 * @author Joseph Ramsey
 * @see Evidence
 * @see Proposition
 * @see Manipulation
 */
public interface BayesUpdater extends TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @return P(variable=value | evidence), where evidence is
     * getEvidence().
     */
    double getMarginal(int variable, int category);

    /**
     * @return true if the getJointMarginal() method is supported.
     */
    boolean isJointMarginalSupported();

    /**
     * @return P(variables[i] == values[i] | evidence), where evidence is
     * getEvidence().
     */
    double getJointMarginal(int[] variables, int[] values);

    /**
     * Sets new evidence for the updater. Once this is called, old updating
     * results should not longer be available.
     */
    void setEvidence(Evidence evidence);

    /**
     * @return the Bayes instantiated model that is being updated.
     */
    BayesIm getBayesIm();

    double[] calculatePriorMarginals(int nodeIndex);

    double[] calculateUpdatedMarginals(int nodeIndex);
}





