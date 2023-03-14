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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.Arrays;
import java.util.List;

/**
 * Interface implemented by classes that do conditional independence testing. These classes are capable of serving as
 * conditional independence "oracles" for constraint-based searches.
 *
 * @author Don Crimbchin (djc2@andrew.cmu.edu)
 * @author Joseph Ramsey
 */
public interface IndependenceTest {

    /**
     * @return an IndependenceResult (see).
     * @see IndependenceResult
     */
    IndependenceResult checkIndependence(Node x, Node y, List<Node> z);

    /**
     * @return the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    List<Node> getVariables();

    /**
     * @return the variable by the given name.
     */
    Node getVariable(String name);

    /**
     * @return the list of names for the variables in getNodesInEvidence.
     */
    List<String> getVariableNames();

    /**
     * @return The data model for the independence test.
     */
    DataModel getData();

    int getSampleSize();

    /**
     * A score that is higher with more likely models.
     */
    double getScore();

    void setVerbose(boolean verbose);

    boolean isVerbose();

    String toString();

    //==============================DEFAULT METHODS=========================//

    /**
     * @return an Independence test for a subset of the variables.
     */
    default IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("Independence subset feature is not implemented.");
    }

    /**
     * @return and IndependenceResult (see).
     * @see IndependenceResult
     */
    default IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return checkIndependence(x, y, zList);
    }

    /**
     * @return true if y is determined the variable in z.
     */
    default boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Determines method is not implmeented.");
    }

    /**
     * @return the significance level of the independence test.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    default double getAlpha() {
        throw new UnsupportedOperationException("The getAlpha() method is not implemented for this test.");
    }

    /**
     * Sets the significance level.
     */
    default void setAlpha(double alpha) {
        throw new UnsupportedOperationException("The setAlpha() method is not implemented for this test.");
    }

    default ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException("The getCov() method is not implemented for this test.");
    }

    default List<DataSet> getDataSets() {
        throw new UnsupportedOperationException("The getDataSets() method is not implemented for this test.");
    }
}




