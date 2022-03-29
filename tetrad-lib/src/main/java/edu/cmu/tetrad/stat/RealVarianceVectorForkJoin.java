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
package edu.cmu.tetrad.stat;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

/**
 * Feb 9, 2016 3:15:29 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class RealVarianceVectorForkJoin implements RealVariance {

    private final double[][] data;

    private final int numOfRows;

    private final int numOfCols;

    private final int numOfThreads;

    public RealVarianceVectorForkJoin(double[][] data, int numOfThreads) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
        this.numOfThreads = numOfThreads;
    }

    @Override
    public double[] compute(boolean biasCorrected) {
        double[] means = new double[this.numOfCols];

        ForkJoinPool pool = new ForkJoinPool(this.numOfThreads);
        pool.invoke(new MeanAction(this.data, means, 0, this.numOfCols - 1));
        pool.invoke(new VarianceAction(this.data, means, biasCorrected, 0, this.numOfCols - 1));
        pool.shutdown();

        return means;
    }

    class VarianceAction extends RecursiveAction {

        private static final long serialVersionUID = 8630127061304877790L;

        private final double[][] data;
        private final double[] means;
        private final boolean biasCorrected;
        private final int start;
        private final int end;

        public VarianceAction(double[][] data, double[] means, boolean biasCorrected, int start, int end) {
            this.data = data;
            this.means = means;
            this.biasCorrected = biasCorrected;
            this.start = start;
            this.end = end;
        }

        private void computeVariance() {
            for (int col = this.start; col <= this.end; col++) {
                double mean = this.means[col];
                double value = 0;
                double squareValue = 0;
                for (int row = 0; row < RealVarianceVectorForkJoin.this.numOfRows; row++) {
                    double val = this.data[row][col] - mean;
                    squareValue += val * val;
                    value += val;
                }
                this.means[col] = (this.biasCorrected)
                        ? (squareValue - (value * value / RealVarianceVectorForkJoin.this.numOfRows)) / (RealVarianceVectorForkJoin.this.numOfRows - 1.0f)
                        : (squareValue - (value * value / RealVarianceVectorForkJoin.this.numOfRows)) / RealVarianceVectorForkJoin.this.numOfRows;
            }
        }

        @Override
        protected void compute() {
            int length = this.end - this.start;
            int limit = RealVarianceVectorForkJoin.this.numOfCols / RealVarianceVectorForkJoin.this.numOfThreads;
            int delta = RealVarianceVectorForkJoin.this.numOfCols % RealVarianceVectorForkJoin.this.numOfThreads;
            int size = limit + delta;
            if (length <= size) {
                computeVariance();
            } else {
                int middle = (this.end + this.start) / 2;
                ForkJoinTask.invokeAll(new VarianceAction(this.data, this.means, this.biasCorrected, this.start, middle), new VarianceAction(this.data, this.means, this.biasCorrected, middle + 1, this.end));
            }
        }

    }

    class MeanAction extends RecursiveAction {

        private static final long serialVersionUID = 3741759201009022262L;

        private final double[][] data;
        private final double[] means;
        private final int start;
        private final int end;

        public MeanAction(double[][] data, double[] means, int start, int end) {
            this.data = data;
            this.means = means;
            this.start = start;
            this.end = end;
        }

        private void computeMean() {
            for (int col = this.start; col <= this.end; col++) {
                double sum = 0;
                for (int row = 0; row < RealVarianceVectorForkJoin.this.numOfRows; row++) {
                    sum += this.data[row][col];
                }
                this.means[col] = sum / RealVarianceVectorForkJoin.this.numOfRows;
            }
        }

        @Override
        protected void compute() {
            int length = this.end - this.start;
            int limit = RealVarianceVectorForkJoin.this.numOfCols / RealVarianceVectorForkJoin.this.numOfThreads;
            int delta = RealVarianceVectorForkJoin.this.numOfCols % RealVarianceVectorForkJoin.this.numOfThreads;
            int size = limit + delta;
            if (length <= size) {
                computeMean();
            } else {
                int middle = (this.end + this.start) / 2;
                ForkJoinTask.invokeAll(new MeanAction(this.data, this.means, this.start, middle), new MeanAction(this.data, this.means, middle + 1, this.end));
            }
        }
    }

}
