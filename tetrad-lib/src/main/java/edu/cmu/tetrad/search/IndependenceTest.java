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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

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
     * @return an Independence test for a subset of the variables.
     */
    IndependenceTest indTestSubset(List<Node> vars);

    /**
     * @return true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    boolean isIndependent(Node x, Node y, List<Node> z);

    /**
     * @return true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    boolean isIndependent(Node x, Node y, Node... z);

    /**
     * @return true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    boolean isDependent(Node x, Node y, List<Node> z);

    /**
     * @return true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    boolean isDependent(Node x, Node y, Node... z);

    /**
     * @return the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    double getPValue();

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
     * @return true if y is determined the variable in z.
     */
    boolean determines(List<Node> z, Node y);

    /**
     * @return the significance level of the independence test.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    double getAlpha();

    /**
     * Sets the significance level.
     */
    void setAlpha(double alpha);

    /**
     * '
     *
     * @return The data model for the independence test.
     */
    DataModel getData();


    ICovarianceMatrix getCov();

    List<DataSet> getDataSets();

    int getSampleSize();

    List<TetradMatrix> getCovMatrices();

    /**
     * A score that is higher with more likely models.
     */
    double getScore();
}





