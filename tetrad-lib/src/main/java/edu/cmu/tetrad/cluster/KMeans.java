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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

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
    public static final int RANDOM_POINTS = 0;

    /**
     * The type of initialization in which points are assigned randomly to
     * clusters.
     */
    public static final int RANDOM_CLUSTERS = 1;

    /**
     * The type of initialiation in which explicit points are provided to
     * serve as clusters.
     */
    public static final int EXPLICIT_POINTS = 2;

    /**
     * The data, columns as features, rows as cases.
     */
    private TetradMatrix data;

    /**
     * The centers.
     */
    private TetradMatrix centers;

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
    private Dissimilarity metric = new SquaredErrorLoss();

    /**
     * The number of centers (i.e. the number clusters) that the algorithm
     * will find.
     */
    private int numCenters;

    /**
     * The type of initialization, one of RANDOM_POINTS,
     */
    private int initializationType = RANDOM_POINTS;

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
    public static KMeans randomPoints(int numCenters) {
        KMeans algorithm = new KMeans();
        algorithm.numCenters = numCenters;
        algorithm.initializationType = RANDOM_POINTS;

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
    public static KMeans randomClusters(int numCenters) {
        KMeans algorithm = new KMeans();
        algorithm.numCenters = numCenters;
        algorithm.initializationType = RANDOM_CLUSTERS;

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
    public static KMeans explicitPoints(TetradMatrix centers) {
        KMeans algorithm = new KMeans();
        algorithm.centers = centers;

        return algorithm;
    }

    //===========================PUBLIC METHODS=======================//

    /**
     * Runs the batch K-means clustering algorithm on the data, returning a
     * result.
     */
    public void cluster(TetradMatrix data) {
        this.data = data;

        if (initializationType == RANDOM_POINTS) {
            centers = pickCenters(numCenters, data);
            clusters = new ArrayList<Integer>();

            for (int i = 0; i < data.rows(); i++) {
                clusters.add(-1);
            }
        } else if (initializationType == RANDOM_CLUSTERS) {
            centers = new TetradMatrix(numCenters, data.columns());

            // Randomly assign points to clusters and get the initial centers of
            // mass from that assignment.
            clusters = new ArrayList<Integer>();

            for (int i = 0; i < data.rows(); i++) {
                clusters.add(RandomUtil.getInstance()
                        .nextInt(centers.rows()));
            }

            moveCentersToMeans();
        } else if (initializationType == EXPLICIT_POINTS) {
            clusters = new ArrayList<Integer>();

            for (int i = 0; i < data.rows(); i++) {
                clusters.add(-1);
            }
        }

        boolean changed = true;
        iterations = 0;

//        System.out.println("Original centers: " + centers);

        while (changed && (maxIterations == -1 || iterations < maxIterations)) {
            iterations++;
//            System.out.println("Iteration = " + iterations);

            // Step #1: Assign each point to its closest center, forming a cluster for
            // each center.
            int numChanged = reassignPoints();
            changed = numChanged > 0;

            // Step #2: Replace each center by the center of mass of its cluster.
            moveCentersToMeans();

//            System.out.println("New centers: " + centers);
//            System.out.println("Cluster counts: " + countClusterSizes());
        }

    }

    public List<List<Integer>> getClusters() {
        return convertClusterIndicesToLists(clusters);
    }

    public static List<List<Integer>> convertClusterIndicesToLists(List<Integer> clusterIndices) {
        int max = 0;

        for (int i = 0; i < clusterIndices.size(); i++) {
            if (clusterIndices.get(i) > max) max = clusterIndices.get(i);
        }

        List<List<Integer>> clusters = new ArrayList<List<Integer>>();

        for (int i = 0; i <= max; i++) {
            clusters.add(new LinkedList<Integer>());
        }

        for (int i = 0; i < clusterIndices.size(); i++) {
            Integer index = clusterIndices.get(i);

            if (index == -1) continue;

            clusters.get(index).add(i);
        }

        return clusters;
    }

    public TetradMatrix getPrototypes() {
        return centers.copy();
    }

    /**
     * Return the maximum number of iterations, or -1 if the algorithm is
     * allowed to run unconstrainted.
     *
     * @return This value.
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * Sets the maximum number of iterations, or -1 if the algorithm is allowed
     * to run unconstrainted.
     *
     * @param maxIterations This value.
     */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getNumClusters() {
        return centers.rows();
    }

    public List<Integer> getCluster(int k) {
        List<Integer> cluster = new ArrayList<Integer>();

        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i) == k) {
                cluster.add(i);
            }
        }

        return cluster;
    }

    public Dissimilarity getMetric() {
        return metric;
    }

    /**
     * @return the number of iterations.
     */
    public int iterations() {
        return iterations;
    }

    /**
     * The squared error of the kth cluster.
     *
     * @param k The index of the cluster in question.
     * @return this squared error.
     */
    public double squaredError(int k) {
        double squaredError = 0.0;

        for (int i = 0; i < data.rows(); i++) {
            if (clusters.get(i) == k) {
                TetradVector datum = data.getRow(i);
                TetradVector center = centers.getRow(k);
                squaredError += metric.dissimilarity(datum, center);
            }
        }
        return squaredError;
    }

    /**
     * Total squared error for most recent run.
     *
     * @return the total squared error.
     */
    public double totalSquaredError() {
        double totalSquaredError = 0.0;

        for (int k = 0; k < centers.rows(); k++) {
            totalSquaredError += squaredError(k);
        }

        return totalSquaredError;
    }

    /**
     * @return a string representation of the cluster result.
     */
    public String toString() {
        NumberFormat n1 = NumberFormatUtil.getInstance().getNumberFormat();

        TetradVector counts = countClusterSizes();
        double totalSquaredError = totalSquaredError();

        StringBuilder buf = new StringBuilder();
        buf.append("Cluster Result (").append(clusters.size())
                .append(" cases, ").append(centers.columns())
                .append(" feature(s), ").append(centers.rows())
                .append(" clusters)");

        for (int k = 0; k < centers.rows(); k++) {
            buf.append("\n\tCluster #").append(k + 1).append(": n = ").append(counts.get(k));
            buf.append(" Squared Error = ").append(n1.format(squaredError(k)));
        }

        buf.append("\n\tTotal Squared Error = ").append(n1.format(totalSquaredError));
        return buf.toString();
    }

    //==========================PRIVATE METHODS=========================//

    private int reassignPoints() {
        int numChanged = 0;

        for (int i = 0; i < data.rows(); i++) {
            TetradVector datum = data.getRow(i);
            double minDissimilarity = Double.POSITIVE_INFINITY;
            int cluster = -1;

            for (int k = 0; k < centers.rows(); k++) {
                TetradVector center = centers.getRow(k);
                double dissimilarity = getMetric().dissimilarity(datum, center);

                if (dissimilarity < minDissimilarity) {
                    minDissimilarity = dissimilarity;
                    cluster = k;
                }
            }

            if (cluster != clusters.get(i)) {
                clusters.set(i, cluster);
                numChanged++;
            }
        }

        //System.out.println("Moved " + numChanged + " points.");
        return numChanged;
    }

    private void moveCentersToMeans() {
        for (int k = 0; k < centers.rows(); k++) {
            double[] sums = new double[centers.columns()];
            int count = 0;

            for (int i = 0; i < data.rows(); i++) {
                if (clusters.get(i) == k) {
                    for (int j = 0; j < data.columns(); j++) {
                        sums[j] += data.get(i, j);
                    }

                    count++;
                }
            }

            if (count != 0) {
                for (int j = 0; j < centers.columns(); j++) {
                    centers.set(k, j, sums[j] / count);
                }
            }
        }
    }

    private TetradMatrix pickCenters(int numCenters, TetradMatrix data) {
        SortedSet<Integer> indexSet = new TreeSet<Integer>();

        while (indexSet.size() < numCenters) {
            int candidate = RandomUtil.getInstance().nextInt(data.rows());

            if (!indexSet.contains(candidate)) {
                indexSet.add(candidate);
            }
        }

        int[] rows = new int[numCenters];

        int i = -1;

        for (int row : indexSet) {
            rows[++i] = row;
        }

        int[] cols = new int[data.columns()];

        for (int j = 0; j < data.columns(); j++) {
            cols[j] = j;
        }

        return data.getSelection(rows, cols).copy();
    }

//    private double dissimilarity(TetradVector d1, TetradVector d2) {
//        return DissimilarityMeasures.squaredEuclideanDistance(d1, d2);
//    }

    private TetradVector countClusterSizes() {
        TetradVector counts = new TetradVector(centers.rows());

        for (int cluster : clusters) {
            if (cluster == -1) {
                continue;
            }

            counts.set(cluster, counts.get(cluster) + 1);
        }

        return counts;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


