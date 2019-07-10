/*
 * Copyright (C) 2016 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.stat.correlation;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import static java.util.concurrent.ForkJoinTask.invokeAll;
import java.util.concurrent.RecursiveAction;

/**
 *
 * Jan 27, 2016 5:37:40 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class RealCovarianceMatrixForkJoin implements RealCovariance {
    static final long serialVersionUID = 23L;

    private final double[][] data;

    private final int numOfRows;

    private final int numOfCols;

    private final int numOfThreads;

    public RealCovarianceMatrixForkJoin(double[][] data, int numOfThreads) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
        this.numOfThreads = (numOfThreads > numOfCols) ? numOfCols : numOfThreads;
    }

    @Override
    public double[] computeLowerTriangle(boolean biasCorrected) {
        double[] covarianceMatrix = new double[(numOfCols * (numOfCols + 1)) / 2];
        double[] means = new double[numOfCols];

        final ForkJoinPool pool = new ForkJoinPool(this.numOfThreads);
        pool.invoke(new MeanAction(means, data, 0, numOfCols - 1));
        pool.invoke(new CovarianceLowerTriangleAction(covarianceMatrix, means, 0, numOfCols - 1, biasCorrected));
        pool.shutdown();

        return covarianceMatrix;
    }

    @Override
    public double[][] compute(boolean biasCorrected) {
        double[][] covarianceMatrix = new double[numOfCols][numOfCols];
        double[] means = new double[numOfCols];

        final ForkJoinPool pool = new ForkJoinPool(this.numOfThreads);
        pool.invoke(new MeanAction(means, data, 0, numOfCols - 1));
        pool.invoke(new CovarianceAction(covarianceMatrix, means, 0, numOfCols - 1, biasCorrected));
        pool.shutdown();

        return covarianceMatrix;
    }

    class CovarianceAction extends RecursiveAction {

        private static final long serialVersionUID = 1034920868427599720L;

        private final double[][] covariance;
        private final double[] means;
        private final int start;
        private final int end;
        private final boolean biasCorrected;

        public CovarianceAction(double[][] covariance, double[] means, int start, int end, boolean biasCorrected) {
            this.covariance = covariance;
            this.means = means;
            this.start = start;
            this.end = end;
            this.biasCorrected = biasCorrected;
        }

        private void computeCovariance() {
            for (int col = start; col <= end; col++) {
                for (int col2 = 0; col2 < col; col2++) {
                    double variance = 0;
                    for (int row = 0; row < numOfRows; row++) {
                        variance += ((data[row][col] - means[col]) * (data[row][col2] - means[col2]) - variance) / (row + 1);
                    }
                    variance = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
                    covariance[col][col2] = variance;
                    covariance[col2][col] = variance;
                }
                double variance = 0;
                for (int row = 0; row < numOfRows; row++) {
                    variance += ((data[row][col] - means[col]) * (data[row][col] - means[col]) - variance) / (row + 1);
                }
                covariance[col][col] = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
            }
        }

        @Override
        protected void compute() {
            int limit = numOfCols / numOfThreads;
            int length = end - start;
            int delta = length / numOfThreads;
            if (length <= limit) {
                computeCovariance();
            } else {
                List<CovarianceAction> actions = new LinkedList<>();
                int startIndex = start;
                int endIndex = startIndex + delta;
                while (startIndex < numOfCols) {
                    if (endIndex >= numOfCols) {
                        endIndex = numOfCols - 1;
                    }
                    actions.add(new CovarianceAction(covariance, means, startIndex, endIndex, biasCorrected));
                    startIndex = endIndex + 1;
                    endIndex = startIndex + delta;
                }
                invokeAll(actions);
            }
        }

    }

    class CovarianceLowerTriangleAction extends RecursiveAction {

        private static final long serialVersionUID = 1818119309247848613L;

        private final double[] covariance;
        private final double[] means;
        private final int start;
        private final int end;
        private final boolean biasCorrected;

        public CovarianceLowerTriangleAction(double[] covariance, double[] means, int start, int end, boolean biasCorrected) {
            this.covariance = covariance;
            this.means = means;
            this.start = start;
            this.end = end;
            this.biasCorrected = biasCorrected;
        }

        private void computeCovariance() {
            int index = (start * (start + 1)) / 2;
            for (int col = start; col <= end; col++) {
                for (int col2 = 0; col2 < col; col2++) {
                    double variance = 0;
                    for (int row = 0; row < numOfRows; row++) {
                        variance += ((data[row][col] - means[col]) * (data[row][col2] - means[col2]) - variance) / (row + 1);
                    }
                    covariance[index++] = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
                }
                double variance = 0;
                for (int row = 0; row < numOfRows; row++) {
                    variance += ((data[row][col] - means[col]) * (data[row][col] - means[col]) - variance) / (row + 1);
                }
                covariance[index++] = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
            }
        }

        @Override
        protected void compute() {
            int limit = numOfCols / numOfThreads;
            int length = end - start;
            int delta = length / numOfThreads;
            if (length <= limit) {
                computeCovariance();
            } else {
                List<CovarianceLowerTriangleAction> actions = new LinkedList<>();
                int startIndex = start;
                int endIndex = startIndex + delta;
                while (startIndex < numOfCols) {
                    if (endIndex >= numOfCols) {
                        endIndex = numOfCols - 1;
                    }
                    actions.add(new CovarianceLowerTriangleAction(covariance, means, startIndex, endIndex, biasCorrected));
                    startIndex = endIndex + 1;
                    endIndex = startIndex + delta;
                }
                invokeAll(actions);
            }
        }

    }

    class MeanAction extends RecursiveAction {

        private static final long serialVersionUID = 2419217605658853345L;

        private final double[] means;
        private final double[][] data;
        private final int start;
        private final int end;

        public MeanAction(double[] means, double[][] data, int start, int end) {
            this.means = means;
            this.data = data;
            this.start = start;
            this.end = end;
        }

        private void computeMean() {
            for (int col = start; col <= end; col++) {
                double sum = 0;
                for (int row = 0; row < numOfRows; row++) {
                    sum += data[row][col];
                }
                means[col] = sum / numOfRows;
            }
        }

        @Override
        protected void compute() {
            int limit = numOfCols / numOfThreads;
            int length = end - start;
            int delta = length / numOfThreads;
            if (length <= limit) {
                computeMean();
            } else {
                List<MeanAction> actions = new LinkedList<>();
                int startIndex = start;
                int endIndex = startIndex + delta;
                while (startIndex < numOfCols) {
                    if (endIndex >= numOfCols) {
                        endIndex = numOfCols - 1;
                    }
                    actions.add(new MeanAction(means, data, startIndex, endIndex));
                    startIndex = endIndex + 1;
                    endIndex = startIndex + delta;
                }
                invokeAll(actions);
            }
        }
    }

}
