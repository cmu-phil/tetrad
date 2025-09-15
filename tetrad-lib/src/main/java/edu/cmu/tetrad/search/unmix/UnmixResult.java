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

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.Collections;
import java.util.List;

/**
 * Container for unmixing output (labels, per-cluster datasets, optional graphs).
 */
public class UnmixResult {
    public final int[] labels;            // length n
    public final int K;
    public final List<DataSet> clusterData;
    public final List<Graph> clusterGraphs;  // may be null or empty
    public final GaussianMixtureEM.Model gmmModel;

    /**
     * Full constructor (graphs may be null).
     */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData,
                       List<Graph> clusterGraphs, GaussianMixtureEM.Model gmmModel) {
        this.labels = labels;
        this.K = K;
        this.clusterData = clusterData;
        this.clusterGraphs = clusterGraphs;
        this.gmmModel = gmmModel;
    }

    /**
     * Convenience: no graphs.
     */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData,
                       GaussianMixtureEM.Model gmmModel) {
        this(labels, K, clusterData, Collections.emptyList(), gmmModel);
    }
}
