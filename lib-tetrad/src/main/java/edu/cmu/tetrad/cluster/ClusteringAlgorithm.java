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

package edu.cmu.tetrad.cluster;

import edu.cmu.tetrad.util.TetradMatrix;

import java.util.List;

/**
 * Represents a clusting algorithm to cluster some data. The data is a
 * TetradMatrix matrix with rows as cases and columns as variables. The
 * purpose of this interface is to allow a clustering algorithm to have
 * parameters set so thet it can be passed to another class to do clustering on
 * data.
 *
 * @author Joseph Ramsey
 */
public interface ClusteringAlgorithm {

    /**
     * Clusters the given data set.
     *
     * @param data An n x m double matrix with n cases (rows) and m variables
     *             (columns).
     * @return an int array c such that c[i] is the cluster that case i is
     *         placed into, or -1 if case i is not placed into a cluster (as a
     *         result of its being eliminated from consideration, for
     *         instance).
     */
    void cluster(TetradMatrix data);

    /**
     * @return a list of clusters, each consisting of a list of indices in the
     * dataset provided as an argument to <code>cluster</code>, or null if the
     * data has not yet been clustered.
     *
     * @see #cluster(cern.colt.matrix.TetradMatrix)
     */
    List<List<Integer>> getClusters();

    /**
     * @return the list of prototypes for clusters as a 2D array, or null if
     * there are not prototypes or the data has not yet been clustered. The
     * array at (k, j) is the jth element of the kth prototype. The number of
     * columns in this array is equal to the number of columns in the dataset
     * provided as an argument to <code>cluster</code>.
     *
     * @see #cluster(cern.colt.matrix.TetradMatrix)
     */
    TetradMatrix getPrototypes();

    /**
     * True iff verbose output should be printed.
     */
    void setVerbose(boolean verbose);
}



