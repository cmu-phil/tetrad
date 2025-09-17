/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.Collections;
import java.util.List;

/**
 * Container for unmixing output (labels, per-cluster datasets, optional graphs).
 */
public class UnmixResult {
    /**
     * An array representing the cluster assignments (labels) for each data point. Each entry corresponds to the cluster
     * index assigned to a specific data point. The array length, n, matches the number of input data points.
     */
    public final int[] labels;
    /**
     * The number of clusters (K) determined or specified for a clustering algorithm. Represents the total number of
     * groups into which a dataset is partitioned.
     */
    public final int K;
    /**
     * A list of datasets corresponding to individual clusters obtained from a clustering algorithm. Each element in the
     * list represents the data points assigned to a specific cluster. The list size typically matches the number of
     * clusters (K).
     */
    public final List<DataSet> clusterData;
    /**
     * A list of optional graphical representations of clusters, where each graph corresponds to a specific cluster.
     * This list may be null or empty if no graphical information is available or provided for the clusters.
     */
    public final List<Graph> clusterGraphs;
    /**
     * The Gaussian Mixture Model (GMM) representation obtained from the Expectation-Maximization (EM) algorithm. This
     * model encapsulates the parameters of the Gaussian distribution for each cluster, including means, covariances,
     * and mixing coefficients.
     * <p>
     * The GMM model provides essential details for understanding the structure of the data clusters and can be used for
     * probabilistic reasoning, classification, or further analysis of the clustered data.
     */
    public final GaussianMixtureEM.Model gmmModel;

    /**
     * Constructs an instance of UnmixResult containing information about the results of a clustering algorithm.
     *
     * @param labels An array of integers representing the cluster assignments for each data point. Each element indicates the cluster index assigned to each data point.
     * @param K The number of clusters determined or pre-specified. Represents the total number of groups in the clustering result.
     * @param clusterData A list of datasets where each corresponds to the data points assigned to a specific cluster.
     * @param clusterGraphs A list of graphical representations of clusters, with each graph corresponding to a cluster. This may be null or empty if no graphical representations
     *  are provided.
     * @param gmmModel The Gaussian Mixture Model (GMM) containing the parameters obtained from the clustering process, including Gaussian components for each cluster.
     */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData, List<Graph> clusterGraphs, GaussianMixtureEM.Model gmmModel) {
        this.labels = labels;
        this.K = K;
        this.clusterData = clusterData;
        this.clusterGraphs = clusterGraphs;
        this.gmmModel = gmmModel;
    }

    /**
     * Constructs an instance of UnmixResult containing essential clustering outcome information
     * without graphical representations.
     *
     * @param labels An array of integers representing the cluster assignments for each data point.
     *               Each element indicates the cluster index assigned to each data point.
     * @param K The number of clusters determined or pre-specified. Represents the total number of
     *          groups in the clustering result.
     * @param clusterData A list of datasets where each corresponds to the data points assigned
     *                    to a specific cluster.
     * @param gmmModel The Gaussian Mixture Model (GMM) containing the parameters obtained from
     *                 the clustering process, including Gaussian components for each cluster.
     */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData, GaussianMixtureEM.Model gmmModel) {
        this(labels, K, clusterData, Collections.emptyList(), gmmModel);
    }
}
