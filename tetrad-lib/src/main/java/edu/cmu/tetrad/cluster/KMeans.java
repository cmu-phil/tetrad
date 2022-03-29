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

import edu.cmu.tetrad.cluster.metrics.Dissimilarity;
import edu.cmu.tetrad.cluster.metrics.SquaredErrorLoss;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.Vector;

import java.text.NumberFormat;
import java.util.*;

/**
 * Implements the "batch" version of the K Means clustering algorithm-- that is,
 * in one sweep, assign each point to its nearest center, and then in a second
 * sweep, reset each center to the mean of the cluster for that center,
 * repeating until convergence.
 * <p>
 * Note that this algorithm is guaranteed to converge, since the total squared
 * error is guaranteed to be reduced at each step.
 *
 * @author Joseph Ramsey
 */
public class KMeans implements ClusteringAlgorithm {

    /**
     * The type of initialization in which random points are selected from
     * the data to serve as initial centers.
     */
    private static final int RANDOM_POINTS = 0;

    /**
     * The type of initialization in which points are assigned randomly to
     * clusters.
     */
    private static final int RANDOM_CLUSTERS = 1;

    /**
     * The type of initialiation in which explicit points are provided to
     * serve as clusters.
     */
    private static final int EXPLICIT_POINTS = 2;

    /**
     * The data, columns as features, rows as cases.
     */
    private Matrix data;

    /**
     * The centers.
     */
    private Matrix centers;

    /**
     * The maximum number of interations.
     */
    private int maxIterations = 50;

    /**
     * Current clusters.
     */
    private List<Integer> clusters;

    /**
     * Number of iterations of algorithm.
     */
    private int iterations;

    /**
     * The dissimilarity metric being used. For K means, the metric must be
     * squared Euclidean. It's an assumption of the algorithm.
     */
    private final Dissimilarity metric = new SquaredErrorLoss();

    /**
     * The number of centers (i.e. the number clusters) that the algorithm
     * will find.
     */
    private int numCenters;

    /**
     * The type of initialization, one of RANDOM_POINTS,
     */
    private int initializationType = KMeans.RANDOM_POINTS;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;

    //============================CONSTRUCTOR==========================//

    /**
     * Private constructor. (Please keep it that way.)
     */
    private KMeans() {
    }

    /**
     * Constructs a new KMeansBatch, initializing the algorithm by picking
     * <code>numCeneters</code> centers randomly from the data itself.
     *
     * @param numCenters The number of centers (clusters).
     * @return The parametrized algorithm.
     */
    public static KMeans randomPoints(final int numCenters) {
        final KMeans algorithm = new KMeans();
        algorithm.numCenters = numCenters;
        algorithm.initializationType = KMeans.RANDOM_POINTS;

        return algorithm;
    }

    /**
     * Constructs a new KMeansBatch, initializing the algorithm by randomly
     * assigning each point in the data to one of the <numCenters> clusters,
     * then calculating the centroid of each cluster.
     *
     * @param numCenters The number of centers (clusters).
     * @return The constructed algorithm.
     */
    public static KMeans randomClusters(final int numCenters) {
        final KMeans algorithm = new KMeans();
        algorithm.numCenters = numCenters;
        algorithm.initializationType = KMeans.RANDOM_CLUSTERS;

        return algorithm;
    }

    /**
     * Constructs a new KMeansBatch, initializing the algorithm by specifying a
     * fixed number of centers.
     *
     * @param centers The recommended centers to start the algorithm with. This
     *                is an array, with the number of rows equal to the number
     *                of centers and the number of columns equal to the number
     *                of columns in the data (that is, features).
     * @return The constructed algorithm.
     */
    public static KMeans explicitPoints(final Matrix centers) {
        final KMeans algorithm = new KMeans();
        algorithm.centers = centers;

        return algorithm;
    }

    //===========================PUBLIC METHODS=======================//

    /**
     * Runs the batch K-means clustering algorithm on the data, returning a
     * result.
     */
    public void cluster(final Matrix data) {
        this.data = data;

        if (this.initializationType == KMeans.RANDOM_POINTS) {
            this.centers = pickCenters(this.numCenters, data);
            this.clusters = new ArrayList<>();

            for (int i = 0; i < data.rows(); i++) {
                this.clusters.add(-1);
            }
        } else if (this.initializationType == KMeans.RANDOM_CLUSTERS) {
            this.centers = new Matrix(this.numCenters, data.columns());

            // Randomly assign points to clusters and get the initial centers of
            // mass from that assignment.
            this.clusters = new ArrayList<>();

            for (int i = 0; i < data.rows(); i++) {
                this.clusters.add(RandomUtil.getInstance()
                        .nextInt(this.centers.rows()));
            }

            moveCentersToMeans();
        } else if (this.initializationType == KMeans.EXPLICIT_POINTS) {
            this.clusters = new ArrayList<>();

            for (int i = 0; i < data.rows(); i++) {
                this.clusters.add(-1);
            }
        }

        boolean changed = true;
        this.iterations = 0;

//        System.out.println("Original centers: " + centers);

        while (changed && (this.maxIterations == -1 || this.iterations < this.maxIterations)) {
            this.iterations++;
//            System.out.println("Iteration = " + iterations);

            // Step #1: Assign each point to its closest center, forming a cluster for
            // each center.
            final int numChanged = reassignPoints();
            changed = numChanged > 0;

            // Step #2: Replace each center by the center of mass of its cluster.
            moveCentersToMeans();

//            System.out.println("New centers: " + centers);
//            System.out.println("Cluster counts: " + countClusterSizes());
        }

    }

    public List<List<Integer>> getClusters() {
        return KMeans.convertClusterIndicesToLists(this.clusters);
    }

    private static List<List<Integer>> convertClusterIndicesToLists(final List<Integer> clusterIndices) {
        int max = 0;

        for (final Integer clusterIndice : clusterIndices) {
            if (clusterIndice > max) max = clusterIndice;
        }

        final List<List<Integer>> clusters = new ArrayList<>();

        for (int i = 0; i <= max; i++) {
            clusters.add(new LinkedList<Integer>());
        }

        for (int i = 0; i < clusterIndices.size(); i++) {
            final Integer index = clusterIndices.get(i);

            if (index == -1) continue;

            clusters.get(index).add(i);
        }

        return clusters;
    }

    public Matrix getPrototypes() {
        return this.centers.copy();
    }

    /**
     * Return the maximum number of iterations, or -1 if the algorithm is
     * allowed to run unconstrainted.
     *
     * @return This value.
     */
    public int getMaxIterations() {
        return this.maxIterations;
    }

    /**
     * Sets the maximum number of iterations, or -1 if the algorithm is allowed
     * to run unconstrainted.
     *
     * @param maxIterations This value.
     */
    public void setMaxIterations(final int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getNumClusters() {
        return this.centers.rows();
    }

    public List<Integer> getCluster(final int k) {
        final List<Integer> cluster = new ArrayList<>();

        for (int i = 0; i < this.clusters.size(); i++) {
            if (this.clusters.get(i) == k) {
                cluster.add(i);
            }
        }

        return cluster;
    }

    private Dissimilarity getMetric() {
        return this.metric;
    }

    /**
     * @return the number of iterations.
     */
    public int iterations() {
        return this.iterations;
    }

    /**
     * The squared error of the kth cluster.
     *
     * @param k The index of the cluster in question.
     * @return this squared error.
     */
    private double squaredError(final int k) {
        double squaredError = 0.0;

        for (int i = 0; i < this.data.rows(); i++) {
            if (this.clusters.get(i) == k) {
                final Vector datum = this.data.getRow(i);
                final Vector center = this.centers.getRow(k);
                squaredError += this.metric.dissimilarity(datum, center);
            }
        }
        return squaredError;
    }

    /**
     * Total squared error for most recent run.
     *
     * @return the total squared error.
     */
    private double totalSquaredError() {
        double totalSquaredError = 0.0;

        for (int k = 0; k < this.centers.rows(); k++) {
            totalSquaredError += squaredError(k);
        }

        return totalSquaredError;
    }

    /**
     * @return a string representation of the cluster result.
     */
    public String toString() {
        final NumberFormat n1 = NumberFormatUtil.getInstance().getNumberFormat();

        final Vector counts = countClusterSizes();
        final double totalSquaredError = totalSquaredError();

        final StringBuilder buf = new StringBuilder();
        buf.append("Cluster Result (").append(this.clusters.size())
                .append(" cases, ").append(this.centers.columns())
                .append(" feature(s), ").append(this.centers.rows())
                .append(" clusters)");

        for (int k = 0; k < this.centers.rows(); k++) {
            buf.append("\n\tCluster #").append(k + 1).append(": n = ").append(counts.get(k));
            buf.append(" Squared Error = ").append(n1.format(squaredError(k)));
        }

        buf.append("\n\tTotal Squared Error = ").append(n1.format(totalSquaredError));
        return buf.toString();
    }

    //==========================PRIVATE METHODS=========================//

    private int reassignPoints() {
        int numChanged = 0;

        for (int i = 0; i < this.data.rows(); i++) {
            final Vector datum = this.data.getRow(i);
            double minDissimilarity = Double.POSITIVE_INFINITY;
            int cluster = -1;

            for (int k = 0; k < this.centers.rows(); k++) {
                final Vector center = this.centers.getRow(k);
                final double dissimilarity = getMetric().dissimilarity(datum, center);

                if (dissimilarity < minDissimilarity) {
                    minDissimilarity = dissimilarity;
                    cluster = k;
                }
            }

            if (cluster != this.clusters.get(i)) {
                this.clusters.set(i, cluster);
                numChanged++;
            }
        }

        //System.out.println("Moved " + numChanged + " points.");
        return numChanged;
    }

    private void moveCentersToMeans() {
        for (int k = 0; k < this.centers.rows(); k++) {
            final double[] sums = new double[this.centers.columns()];
            int count = 0;

            for (int i = 0; i < this.data.rows(); i++) {
                if (this.clusters.get(i) == k) {
                    for (int j = 0; j < this.data.columns(); j++) {
                        sums[j] += this.data.get(i, j);
                    }

                    count++;
                }
            }

            if (count != 0) {
                for (int j = 0; j < this.centers.columns(); j++) {
                    this.centers.set(k, j, sums[j] / count);
                }
            }
        }
    }

    private Matrix pickCenters(final int numCenters, final Matrix data) {
        final SortedSet<Integer> indexSet = new TreeSet<>();

        while (indexSet.size() < numCenters) {
            final int candidate = RandomUtil.getInstance().nextInt(data.rows());

            if (!indexSet.contains(candidate)) {
                indexSet.add(candidate);
            }
        }

        final int[] rows = new int[numCenters];

        int i = -1;

        for (final int row : indexSet) {
            rows[++i] = row;
        }

        final int[] cols = new int[data.columns()];

        for (int j = 0; j < data.columns(); j++) {
            cols[j] = j;
        }

        return data.getSelection(rows, cols).copy();
    }

//    private double dissimilarity(TetradVector d1, TetradVector d2) {
//        return DissimilarityMeasures.squaredEuclideanDistance(d1, d2);
//    }

    private Vector countClusterSizes() {
        final Vector counts = new Vector(this.centers.rows());

        for (final int cluster : this.clusters) {
            if (cluster == -1) {
                continue;
            }

            counts.set(cluster, counts.get(cluster) + 1);
        }

        return counts;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }
}


