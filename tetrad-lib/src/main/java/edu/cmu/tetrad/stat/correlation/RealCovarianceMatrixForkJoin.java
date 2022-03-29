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
import java.util.concurrent.RecursiveAction;

/**
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

    public RealCovarianceMatrixForkJoin(final double[][] data, final int numOfThreads) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
        this.numOfThreads = (numOfThreads > this.numOfCols) ? this.numOfCols : numOfThreads;
    }

    @Override
    public double[] computeLowerTriangle(final boolean biasCorrected) {
        final double[] covarianceMatrix = new double[(this.numOfCols * (this.numOfCols + 1)) / 2];
        final double[] means = new double[this.numOfCols];

        final ForkJoinPool pool = new ForkJoinPool(this.numOfThreads);
        pool.invoke(new MeanAction(means, this.data, 0, this.numOfCols - 1));
        pool.invoke(new CovarianceLowerTriangleAction(covarianceMatrix, means, 0, this.numOfCols - 1, biasCorrected));
        pool.shutdown();

        return covarianceMatrix;
    }

    @Override
    public double[][] compute(final boolean biasCorrected) {
        final double[][] covarianceMatrix = new double[this.numOfCols][this.numOfCols];
        final double[] means = new double[this.numOfCols];

        final ForkJoinPool pool = new ForkJoinPool(this.numOfThreads);
        pool.invoke(new MeanAction(means, this.data, 0, this.numOfCols - 1));
        pool.invoke(new CovarianceAction(covarianceMatrix, means, 0, this.numOfCols - 1, biasCorrected));
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

        public CovarianceAction(final double[][] covariance, final double[] means, final int start, final int end, final boolean biasCorrected) {
            this.covariance = covariance;
            this.means = means;
            this.start = start;
            this.end = end;
            this.biasCorrected = biasCorrected;
        }

        private void computeCovariance() {
            for (int col = this.start; col <= this.end; col++) {
                for (int col2 = 0; col2 < col; col2++) {
                    double variance = 0;
                    for (int row = 0; row < RealCovarianceMatrixForkJoin.this.numOfRows; row++) {
                        variance += ((RealCovarianceMatrixForkJoin.this.data[row][col] - this.means[col]) * (RealCovarianceMatrixForkJoin.this.data[row][col2] - this.means[col2]) - variance) / (row + 1);
                    }
                    variance = this.biasCorrected ? variance * ((double) RealCovarianceMatrixForkJoin.this.numOfRows / (double) (RealCovarianceMatrixForkJoin.this.numOfRows - 1)) : variance;
                    this.covariance[col][col2] = variance;
                    this.covariance[col2][col] = variance;
                }
                double variance = 0;
                for (int row = 0; row < RealCovarianceMatrixForkJoin.this.numOfRows; row++) {
                    variance += ((RealCovarianceMatrixForkJoin.this.data[row][col] - this.means[col]) * (RealCovarianceMatrixForkJoin.this.data[row][col] - this.means[col]) - variance) / (row + 1);
                }
                this.covariance[col][col] = this.biasCorrected ? variance * ((double) RealCovarianceMatrixForkJoin.this.numOfRows / (double) (RealCovarianceMatrixForkJoin.this.numOfRows - 1)) : variance;
            }
        }

        @Override
        protected void compute() {
            final int limit = RealCovarianceMatrixForkJoin.this.numOfCols / RealCovarianceMatrixForkJoin.this.numOfThreads;
            final int length = this.end - this.start;
            final int delta = length / RealCovarianceMatrixForkJoin.this.numOfThreads;
            if (length <= limit) {
                computeCovariance();
            } else {
                final List<CovarianceAction> actions = new LinkedList<>();
                int startIndex = this.start;
                int endIndex = startIndex + delta;
                while (startIndex < RealCovarianceMatrixForkJoin.this.numOfCols) {
                    if (endIndex >= RealCovarianceMatrixForkJoin.this.numOfCols) {
                        endIndex = RealCovarianceMatrixForkJoin.this.numOfCols - 1;
                    }
                    actions.add(new CovarianceAction(this.covariance, this.means, startIndex, endIndex, this.biasCorrected));
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

        public CovarianceLowerTriangleAction(final double[] covariance, final double[] means, final int start, final int end, final boolean biasCorrected) {
            this.covariance = covariance;
            this.means = means;
            this.start = start;
            this.end = end;
            this.biasCorrected = biasCorrected;
        }

        private void computeCovariance() {
            int index = (this.start * (this.start + 1)) / 2;
            for (int col = this.start; col <= this.end; col++) {
                for (int col2 = 0; col2 < col; col2++) {
                    double variance = 0;
                    for (int row = 0; row < RealCovarianceMatrixForkJoin.this.numOfRows; row++) {
                        variance += ((RealCovarianceMatrixForkJoin.this.data[row][col] - this.means[col]) * (RealCovarianceMatrixForkJoin.this.data[row][col2] - this.means[col2]) - variance) / (row + 1);
                    }
                    this.covariance[index++] = this.biasCorrected ? variance * ((double) RealCovarianceMatrixForkJoin.this.numOfRows / (double) (RealCovarianceMatrixForkJoin.this.numOfRows - 1)) : variance;
                }
                double variance = 0;
                for (int row = 0; row < RealCovarianceMatrixForkJoin.this.numOfRows; row++) {
                    variance += ((RealCovarianceMatrixForkJoin.this.data[row][col] - this.means[col]) * (RealCovarianceMatrixForkJoin.this.data[row][col] - this.means[col]) - variance) / (row + 1);
                }
                this.covariance[index++] = this.biasCorrected ? variance * ((double) RealCovarianceMatrixForkJoin.this.numOfRows / (double) (RealCovarianceMatrixForkJoin.this.numOfRows - 1)) : variance;
            }
        }

        @Override
        protected void compute() {
            final int limit = RealCovarianceMatrixForkJoin.this.numOfCols / RealCovarianceMatrixForkJoin.this.numOfThreads;
            final int length = this.end - this.start;
            final int delta = length / RealCovarianceMatrixForkJoin.this.numOfThreads;
            if (length <= limit) {
                computeCovariance();
            } else {
                final List<CovarianceLowerTriangleAction> actions = new LinkedList<>();
                int startIndex = this.start;
                int endIndex = startIndex + delta;
                while (startIndex < RealCovarianceMatrixForkJoin.this.numOfCols) {
                    if (endIndex >= RealCovarianceMatrixForkJoin.this.numOfCols) {
                        endIndex = RealCovarianceMatrixForkJoin.this.numOfCols - 1;
                    }
                    actions.add(new CovarianceLowerTriangleAction(this.covariance, this.means, startIndex, endIndex, this.biasCorrected));
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

        public MeanAction(final double[] means, final double[][] data, final int start, final int end) {
            this.means = means;
            this.data = data;
            this.start = start;
            this.end = end;
        }

        private void computeMean() {
            for (int col = this.start; col <= this.end; col++) {
                double sum = 0;
                for (int row = 0; row < RealCovarianceMatrixForkJoin.this.numOfRows; row++) {
                    sum += this.data[row][col];
                }
                this.means[col] = sum / RealCovarianceMatrixForkJoin.this.numOfRows;
            }
        }

        @Override
        protected void compute() {
            final int limit = RealCovarianceMatrixForkJoin.this.numOfCols / RealCovarianceMatrixForkJoin.this.numOfThreads;
            final int length = this.end - this.start;
            final int delta = length / RealCovarianceMatrixForkJoin.this.numOfThreads;
            if (length <= limit) {
                computeMean();
            } else {
                final List<MeanAction> actions = new LinkedList<>();
                int startIndex = this.start;
                int endIndex = startIndex + delta;
                while (startIndex < RealCovarianceMatrixForkJoin.this.numOfCols) {
                    if (endIndex >= RealCovarianceMatrixForkJoin.this.numOfCols) {
                        endIndex = RealCovarianceMatrixForkJoin.this.numOfCols - 1;
                    }
                    actions.add(new MeanAction(this.means, this.data, startIndex, endIndex));
                    startIndex = endIndex + 1;
                    endIndex = startIndex + delta;
                }
                invokeAll(actions);
            }
        }
    }

}
