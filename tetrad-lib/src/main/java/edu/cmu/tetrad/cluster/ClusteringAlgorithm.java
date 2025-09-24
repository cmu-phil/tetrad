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

package edu.cmu.tetrad.cluster;

import edu.cmu.tetrad.util.Matrix;

import java.util.List;

/**
 * Represents a clustering algorithm to cluster some data. The data is a TetradMatrix matrix with rows as cases and
 * columns as variables. The purpose of this interface is to allow a clustering algorithm to have parameters set so that
 * it can be passed to another class to do clustering on data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ClusteringAlgorithm {

    /**
     * Clusters the given data set.
     *
     * @param data An n x m double matrix with n cases (rows) and m variables (columns). Makes an int array c such that
     *             c[i] is the cluster that case i is placed into, or -1 if case i is not placed into a cluster (as a
     *             result of its being eliminated from consideration, for instance).
     */
    void cluster(Matrix data);

    /**
     * <p>getClusters.</p>
     *
     * @return a list of clusters, each consisting of a list of indices in the dataset provided as an argument to
     * <code>cluster</code>, or null if the data has not yet been clustered.
     */
    List<List<Integer>> getClusters();

    /**
     * True iff verbose output should be printed.
     *
     * @param verbose True iff verbose output should be printed.
     */
    void setVerbose(boolean verbose);
}




