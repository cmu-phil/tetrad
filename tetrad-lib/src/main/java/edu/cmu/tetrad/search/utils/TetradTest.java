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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Provides an interface for classes that test tetrad constraints. For the continuous case, we have a variety of tests,
 * including a distribution-free one (which may not be currently practical when the number of variables is too large).
 *
 * @author Ricardo Silva
 * @version $Id: $Id
 */
public interface TetradTest {
    /**
     * <p>getDataSet.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    DataSet getDataSet();

    /**
     * <p>tetradScore.</p>
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param q a int
     * @return a int
     */
    int tetradScore(int i, int j, int k, int q);

    /**
     * <p>tetradScore3.</p>
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param q a int
     * @return a boolean
     */
    boolean tetradScore3(int i, int j, int k, int q);

    /**
     * <p>tetradScore1.</p>
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param q a int
     * @return a boolean
     */
    boolean tetradScore1(int i, int j, int k, int q);

    /**
     * <p>tetradHolds.</p>
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param q a int
     * @return a boolean
     */
    boolean tetradHolds(int i, int j, int k, int q);

    /**
     * <p>tetradPValue.</p>
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param q a int
     * @return a double
     */
    double tetradPValue(int i, int j, int k, int q);

    /**
     * <p>oneFactorTest.</p>
     *
     * @param a a int
     * @param b a int
     * @param c a int
     * @param d a int
     * @return a boolean
     */
    boolean oneFactorTest(int a, int b, int c, int d);

    /**
     * <p>oneFactorTest.</p>
     *
     * @param a a int
     * @param b a int
     * @param c a int
     * @param d a int
     * @param e a int
     * @return a boolean
     */
    boolean oneFactorTest(int a, int b, int c, int d, int e);

    /**
     * <p>twoFactorTest.</p>
     *
     * @param a a int
     * @param b a int
     * @param c a int
     * @param d a int
     * @return a boolean
     */
    boolean twoFactorTest(int a, int b, int c, int d);

    /**
     * <p>twoFactorTest.</p>
     *
     * @param a a int
     * @param b a int
     * @param c a int
     * @param d a int
     * @param e a int
     * @return a boolean
     */
    boolean twoFactorTest(int a, int b, int c, int d, int e);

    /**
     * <p>twoFactorTest.</p>
     *
     * @param a a int
     * @param b a int
     * @param c a int
     * @param d a int
     * @param e a int
     * @param f a int
     * @return a boolean
     */
    boolean twoFactorTest(int a, int b, int c, int d, int e, int f);

    /**
     * <p>getSignificance.</p>
     *
     * @return a double
     */
    double getSignificance();

    /**
     * <p>setSignificance.</p>
     *
     * @param sig a double
     */
    void setSignificance(double sig);

    /**
     * <p>getVarNames.</p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    String[] getVarNames();

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getVariables();

    /**
     * <p>getCovMatrix.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    ICovarianceMatrix getCovMatrix();
}





