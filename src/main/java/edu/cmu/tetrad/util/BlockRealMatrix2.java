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

package edu.cmu.tetrad.util;

import org.apache.commons.math3.exception.*;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class BlockRealMatrix2 extends AbstractRealMatrix implements Serializable {
    public static final int BLOCK_SIZE = 52;
    private static final long serialVersionUID = 4991895511313664478L;
    private final double[][] blocks;
    private final int rows;
    private final int columns;
    private final int blockRows;
    private final int blockColumns;

    public BlockRealMatrix2(int rows, int columns) throws NotStrictlyPositiveException {
        super(rows, columns);
        this.rows = rows;
        this.columns = columns;
        this.blockRows = (rows + 52 - 1) / 52;
        this.blockColumns = (columns + 52 - 1) / 52;
        this.blocks = createBlocksLayout(rows, columns);
    }

    public BlockRealMatrix2(double[][] rawData) throws DimensionMismatchException, NotStrictlyPositiveException {
        this(rawData.length, rawData[0].length, toBlocksLayout(rawData), false);
    }

    public BlockRealMatrix2(int rows, int columns, double[][] blockData, boolean copyArray) throws DimensionMismatchException, NotStrictlyPositiveException {
        super(rows, columns);
        this.rows = rows;
        this.columns = columns;
        this.blockRows = (rows + 52 - 1) / 52;
        this.blockColumns = (columns + 52 - 1) / 52;
        if (copyArray) {
            this.blocks = new double[this.blockRows * this.blockColumns][];
        } else {
            this.blocks = blockData;
        }

        int index = 0;

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int iHeight = this.blockHeight(iBlock);

            for (int jBlock = 0; jBlock < this.blockColumns; ++index) {
                if (blockData[index].length != iHeight * this.blockWidth(jBlock)) {
                    throw new DimensionMismatchException(blockData[index].length, iHeight * this.blockWidth(jBlock));
                }

                if (copyArray) {
                    this.blocks[index] = (double[]) blockData[index].clone();
                }

                ++jBlock;
            }
        }

    }

    public static double[][] toBlocksLayout(double[][] rawData) throws DimensionMismatchException {
        int rows = rawData.length;
        int columns = rawData[0].length;
        int blockRows = (rows + 52 - 1) / 52;
        int blockColumns = (columns + 52 - 1) / 52;

        int blockIndex;
        for (int blocks = 0; blocks < rawData.length; ++blocks) {
            blockIndex = rawData[blocks].length;
            if (blockIndex != columns) {
                throw new DimensionMismatchException(columns, blockIndex);
            }
        }

        double[][] var18 = new double[blockRows * blockColumns][];
        blockIndex = 0;

        for (int iBlock = 0; iBlock < blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, rows);
            int iHeight = pEnd - pStart;

            for (int jBlock = 0; jBlock < blockColumns; ++jBlock) {
                int qStart = jBlock * 52;
                int qEnd = FastMath.min(qStart + 52, columns);
                int jWidth = qEnd - qStart;
                double[] block = new double[iHeight * jWidth];
                var18[blockIndex] = block;
                int index = 0;

                for (int p = pStart; p < pEnd; ++p) {
                    System.arraycopy(rawData[p], qStart, block, index, jWidth);
                    index += jWidth;
                }

                ++blockIndex;
            }
        }

        return var18;
    }

    public static double[][] createBlocksLayout(int rows, int columns) {
        int blockRows = (rows + 52 - 1) / 52;
        int blockColumns = (columns + 52 - 1) / 52;
        double[][] blocks = new double[blockRows * blockColumns][];
        int blockIndex = 0;

        for (int iBlock = 0; iBlock < blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, rows);
            int iHeight = pEnd - pStart;

            for (int jBlock = 0; jBlock < blockColumns; ++jBlock) {
                int qStart = jBlock * 52;
                int qEnd = FastMath.min(qStart + 52, columns);
                int jWidth = qEnd - qStart;
                blocks[blockIndex] = new double[iHeight * jWidth];
                ++blockIndex;
            }
        }

        return blocks;
    }

    public BlockRealMatrix2 createMatrix(int rowDimension, int columnDimension) throws NotStrictlyPositiveException {
        return new BlockRealMatrix2(rowDimension, columnDimension);
    }

    public BlockRealMatrix2 copy() {
        BlockRealMatrix2 copied = new BlockRealMatrix2(this.rows, this.columns);

        for (int i = 0; i < this.blocks.length; ++i) {
            System.arraycopy(this.blocks[i], 0, copied.blocks[i], 0, this.blocks[i].length);
        }

        return copied;
    }

    public BlockRealMatrix2 add(RealMatrix m) throws MatrixDimensionMismatchException {
        try {
            return this.add((BlockRealMatrix2) ((BlockRealMatrix2) m));
        } catch (ClassCastException var16) {
            MatrixUtils.checkAdditionCompatible(this, m);
            BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, this.columns);
            int blockIndex = 0;

            for (int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
                for (int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
                    double[] outBlock = out.blocks[blockIndex];
                    double[] tBlock = this.blocks[blockIndex];
                    int pStart = iBlock * 52;
                    int pEnd = FastMath.min(pStart + 52, this.rows);
                    int qStart = jBlock * 52;
                    int qEnd = FastMath.min(qStart + 52, this.columns);
                    int k = 0;

                    for (int p = pStart; p < pEnd; ++p) {
                        for (int q = qStart; q < qEnd; ++q) {
                            outBlock[k] = tBlock[k] + m.getEntry(p, q);
                            ++k;
                        }
                    }

                    ++blockIndex;
                }
            }

            return out;
        }
    }

    public BlockRealMatrix2 add(BlockRealMatrix2 m) throws MatrixDimensionMismatchException {
        MatrixUtils.checkAdditionCompatible(this, m);
        BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, this.columns);

        for (int blockIndex = 0; blockIndex < out.blocks.length; ++blockIndex) {
            double[] outBlock = out.blocks[blockIndex];
            double[] tBlock = this.blocks[blockIndex];
            double[] mBlock = m.blocks[blockIndex];

            for (int k = 0; k < outBlock.length; ++k) {
                outBlock[k] = tBlock[k] + mBlock[k];
            }
        }

        return out;
    }

    public BlockRealMatrix2 subtract(RealMatrix m) throws MatrixDimensionMismatchException {
        try {
            return this.subtract((BlockRealMatrix2) ((BlockRealMatrix2) m));
        } catch (ClassCastException var16) {
            MatrixUtils.checkSubtractionCompatible(this, m);
            BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, this.columns);
            int blockIndex = 0;

            for (int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
                for (int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
                    double[] outBlock = out.blocks[blockIndex];
                    double[] tBlock = this.blocks[blockIndex];
                    int pStart = iBlock * 52;
                    int pEnd = FastMath.min(pStart + 52, this.rows);
                    int qStart = jBlock * 52;
                    int qEnd = FastMath.min(qStart + 52, this.columns);
                    int k = 0;

                    for (int p = pStart; p < pEnd; ++p) {
                        for (int q = qStart; q < qEnd; ++q) {
                            outBlock[k] = tBlock[k] - m.getEntry(p, q);
                            ++k;
                        }
                    }

                    ++blockIndex;
                }
            }

            return out;
        }
    }

    public BlockRealMatrix2 subtract(BlockRealMatrix2 m) throws MatrixDimensionMismatchException {
        MatrixUtils.checkSubtractionCompatible(this, m);
        BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, this.columns);

        for (int blockIndex = 0; blockIndex < out.blocks.length; ++blockIndex) {
            double[] outBlock = out.blocks[blockIndex];
            double[] tBlock = this.blocks[blockIndex];
            double[] mBlock = m.blocks[blockIndex];

            for (int k = 0; k < outBlock.length; ++k) {
                outBlock[k] = tBlock[k] - mBlock[k];
            }
        }

        return out;
    }

    public BlockRealMatrix2 scalarAdd(double d) {
        BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, this.columns);

        for (int blockIndex = 0; blockIndex < out.blocks.length; ++blockIndex) {
            double[] outBlock = out.blocks[blockIndex];
            double[] tBlock = this.blocks[blockIndex];

            for (int k = 0; k < outBlock.length; ++k) {
                outBlock[k] = tBlock[k] + d;
            }
        }

        return out;
    }

    public RealMatrix scalarMultiply(double d) {
        BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, this.columns);

        for (int blockIndex = 0; blockIndex < out.blocks.length; ++blockIndex) {
            double[] outBlock = out.blocks[blockIndex];
            double[] tBlock = this.blocks[blockIndex];

            for (int k = 0; k < outBlock.length; ++k) {
                outBlock[k] = tBlock[k] * d;
            }
        }

        return out;
    }

    public BlockRealMatrix2 multiply(RealMatrix m) throws DimensionMismatchException {
        try {
            return this.multiply((BlockRealMatrix2) ((BlockRealMatrix2) m));
        } catch (ClassCastException var25) {
            MatrixUtils.checkMultiplicationCompatible(this, m);
            BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, m.getColumnDimension());
            int blockIndex = 0;

            for (int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
                int pStart = iBlock * 52;
                int pEnd = FastMath.min(pStart + 52, this.rows);

                for (int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
                    int qStart = jBlock * 52;
                    int qEnd = FastMath.min(qStart + 52, m.getColumnDimension());
                    double[] outBlock = out.blocks[blockIndex];

                    for (int kBlock = 0; kBlock < this.blockColumns; ++kBlock) {
                        int kWidth = this.blockWidth(kBlock);
                        double[] tBlock = this.blocks[iBlock * this.blockColumns + kBlock];
                        int rStart = kBlock * 52;
                        int k = 0;

                        for (int p = pStart; p < pEnd; ++p) {
                            int lStart = (p - pStart) * kWidth;
                            int lEnd = lStart + kWidth;

                            for (int q = qStart; q < qEnd; ++q) {
                                double sum = 0.0D;
                                int r = rStart;

                                for (int l = lStart; l < lEnd; ++l) {
                                    sum += tBlock[l] * m.getEntry(r, q);
                                    ++r;
                                }

                                outBlock[k] += sum;
                                ++k;
                            }
                        }
                    }

                    ++blockIndex;
                }
            }

            return out;
        }
    }

    public BlockRealMatrix2 multiply(final BlockRealMatrix2 m) throws DimensionMismatchException {
        if (this.getRowDimension() > 500 || this.getColumnDimension() > 500 ||
                m.getRowDimension() > 500 || m.getColumnDimension() > 500) {
            return multiplyThreaded(m);
        }

        MatrixUtils.checkMultiplicationCompatible(this, m);
        final BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, m.columns);
        int blockIndex = 0;

        for(int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, this.rows);

            for(int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
                int jWidth = out.blockWidth(jBlock);
                int jWidth2 = jWidth + jWidth;
                int jWidth3 = jWidth2 + jWidth;
                int jWidth4 = jWidth3 + jWidth;

                double[] outBlock = out.blocks[blockIndex];

                for(int kBlock = 0; kBlock < this.blockColumns; ++kBlock) {
                    int kWidth = this.blockWidth(kBlock);
                    double[] tBlock = this.blocks[iBlock * this.blockColumns + kBlock];
                    double[] mBlock = m.blocks[kBlock * m.blockColumns + jBlock];
                    int k = 0;

                    for(int p = pStart; p < pEnd; ++p) {
                        int lStart = (p - pStart) * kWidth;
                        int lEnd = lStart + kWidth;

                        for(int nStart = 0; nStart < jWidth; ++nStart) {
                            double sum = 0.0D;
                            int l = lStart;

                            int n;
                            for(n = nStart; l < lEnd - 3; n += jWidth4) {
                                sum += tBlock[l] * mBlock[n] + tBlock[l + 1] * mBlock[n + jWidth] + tBlock[l + 2] * mBlock[n + jWidth2] + tBlock[l + 3] * mBlock[n + jWidth3];
                                l += 4;
                            }

                            while(l < lEnd) {
                                sum += tBlock[l++] * mBlock[n];
                                n += jWidth;
                            }

                            outBlock[k] += sum;
                            ++k;
                        }
                    }
                }

                ++blockIndex;
            }
        }

        return out;
    }

    public BlockRealMatrix2 multiplyThreaded(final BlockRealMatrix2 m) throws DimensionMismatchException {
        MatrixUtils.checkMultiplicationCompatible(this, m);
        final BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, m.columns);

        final int _rows = this.rows;
        final int _blockColumns = this.blockColumns;

        final int NTHREADS = Runtime.getRuntime().availableProcessors() * 5;

        ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        class MultiplyTask extends RecursiveTask<Boolean> {
            private int from;
            private int to;

            public MultiplyTask(int from, int to) {
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                int chunk = out.blockRows / NTHREADS + 1;

                if (to - from <= chunk) {
                    for (int iBlock = from; iBlock < to; ++iBlock) {
//                        for (int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
                        int pStart = iBlock * 52;
                        int pEnd = FastMath.min(pStart + 52, _rows);

                        for (int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
                            int jWidth = out.blockWidth(jBlock);
                            int jWidth2 = jWidth + jWidth;
                            int jWidth3 = jWidth2 + jWidth;
                            int jWidth4 = jWidth3 + jWidth;

                            int blockIndex = iBlock * out.blockRows + jBlock;

                            double[] outBlock = out.blocks[blockIndex];

                            for (int kBlock = 0; kBlock < _blockColumns; ++kBlock) {
                                int kWidth = blockWidth(kBlock);
                                double[] tBlock = blocks[iBlock * blockColumns + kBlock];
                                double[] mBlock = m.blocks[kBlock * m.blockColumns + jBlock];
                                int k = 0;

                                for (int p = pStart; p < pEnd; ++p) {
                                    int lStart = (p - pStart) * kWidth;
                                    int lEnd = lStart + kWidth;

                                    for (int nStart = 0; nStart < jWidth; ++nStart) {
                                        double sum = 0.0D;
                                        int l = lStart;

                                        int n;
                                        for (n = nStart; l < lEnd - 3; n += jWidth4) {
                                            sum += tBlock[l] * mBlock[n] + tBlock[l + 1] * mBlock[n + jWidth] + tBlock[l + 2] * mBlock[n + jWidth2] + tBlock[l + 3] * mBlock[n + jWidth3];
                                            l += 4;
                                        }

                                        while (l < lEnd) {
                                            sum += tBlock[l++] * mBlock[n];
                                            n += jWidth;
                                        }

                                        outBlock[k] += sum;
                                        ++k;
                                    }
                                }
                            }
                        }
                    }

                    return true;
                }
//                 else {
//                    int mid = from + (to - from) / 2;
//
//                    MultiplyTask task1 = new MultiplyTask(from, mid);
//                    MultiplyTask task2 = new MultiplyTask(mid, to);
//
//                    task1.fork();
//                    task2.fork();
//
//                    task1.join();
//                    task2.join();
//
//                    return true;
//                }
                else {
                    int numIntervals = 4;

                    int step = (to - from) / numIntervals + 1;

                    List<MultiplyTask> tasks = new ArrayList<MultiplyTask>();

                    for (int i = 0; i < numIntervals; i++) {
                        tasks.add(new MultiplyTask(from + i * step, Math.min(from + (i + 1) * step, to)));
                    }

                    invokeAll(tasks);

//                    for (int i = 0; i < numIntervals; i++) {
//                        tasks.get(i).fork();
//                    }
//
//                    for (int i = 0; i < numIntervals; i++) {
//                        tasks.get(i).join();
//                    }

                    return true;
                }
            }
        }

        pool.invoke(new MultiplyTask(0, out.blockRows));


//        for (int t = 0; t < NTHREADS; t++) {
//            final int _t = t;
//
//            Runnable worker = new Runnable() {
//                @Override
//                public void run() {
//                    int chunk = out.blockRows / NTHREADS + 1;
//                    for (int iBlock = _t * chunk; iBlock < Math.min((_t + 1) * chunk, out.blockRows); ++iBlock) {
////                        for (int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
//                        int pStart = iBlock * 52;
//                        int pEnd = FastMath.min(pStart + 52, _rows);
//
//                        for (int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
//                            int jWidth = out.blockWidth(jBlock);
//                            int jWidth2 = jWidth + jWidth;
//                            int jWidth3 = jWidth2 + jWidth;
//                            int jWidth4 = jWidth3 + jWidth;
//
//                            int blockIndex = iBlock * out.blockRows + jBlock;
//
//                            double[] outBlock = out.blocks[blockIndex];
//
//                            for (int kBlock = 0; kBlock < _blockColumns; ++kBlock) {
//                                int kWidth = blockWidth(kBlock);
//                                double[] tBlock = blocks[iBlock * blockColumns + kBlock];
//                                double[] mBlock = m.blocks[kBlock * m.blockColumns + jBlock];
//                                int k = 0;
//
//                                for (int p = pStart; p < pEnd; ++p) {
//                                    int lStart = (p - pStart) * kWidth;
//                                    int lEnd = lStart + kWidth;
//
//                                    for (int nStart = 0; nStart < jWidth; ++nStart) {
//                                        double sum = 0.0D;
//                                        int l = lStart;
//
//                                        int n;
//                                        for (n = nStart; l < lEnd - 3; n += jWidth4) {
//                                            sum += tBlock[l] * mBlock[n] + tBlock[l + 1] * mBlock[n + jWidth] + tBlock[l + 2] * mBlock[n + jWidth2] + tBlock[l + 3] * mBlock[n + jWidth3];
//                                            l += 4;
//                                        }
//
//                                        while (l < lEnd) {
//                                            sum += tBlock[l++] * mBlock[n];
//                                            n += jWidth;
//                                        }
//
//                                        outBlock[k] += sum;
//                                        ++k;
//                                    }
//                                }
//                            }
//                        }
//                    }
//
//                }
//            };
//
//            pool.submit(worker);
//        }

//        while (!pool.isQuiescent()) {
//        }
//

//        for(int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
//            int pStart = iBlock * 52;
//            int pEnd = FastMath.min(pStart + 52, this.rows);
//
//            for(int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
//                int jWidth = out.blockWidth(jBlock);
//                int jWidth2 = jWidth + jWidth;
//                int jWidth3 = jWidth2 + jWidth;
//                int jWidth4 = jWidth3 + jWidth;
//
//                int blockIndex = iBlock * out.blockRows + jBlock;
//
//                double[] outBlock = out.blocks[blockIndex];
//
//                for(int kBlock = 0; kBlock < this.blockColumns; ++kBlock) {
//                    int kWidth = this.blockWidth(kBlock);
//                    double[] tBlock = this.blocks[iBlock * this.blockColumns + kBlock];
//                    double[] mBlock = m.blocks[kBlock * m.blockColumns + jBlock];
//                    int k = 0;
//
//                    for(int p = pStart; p < pEnd; ++p) {
//                        int lStart = (p - pStart) * kWidth;
//                        int lEnd = lStart + kWidth;
//
//                        for(int nStart = 0; nStart < jWidth; ++nStart) {
//                            double sum = 0.0D;
//                            int l = lStart;
//
//                            int n;
//                            for(n = nStart; l < lEnd - 3; n += jWidth4) {
//                                sum += tBlock[l] * mBlock[n] + tBlock[l + 1] * mBlock[n + jWidth] + tBlock[l + 2] * mBlock[n + jWidth2] + tBlock[l + 3] * mBlock[n + jWidth3];
//                                l += 4;
//                            }
//
//                            while(l < lEnd) {
//                                sum += tBlock[l++] * mBlock[n];
//                                n += jWidth;
//                            }
//
//                            outBlock[k] += sum;
//                            ++k;
//                        }
//                    }
//                }
//
////                ++blockIndex;
//            }
//        }

        return out;
    }

    public double[][] getData() {
        double[][] data = new double[this.getRowDimension()][this.getColumnDimension()];
        int lastColumns = this.columns - (this.blockColumns - 1) * 52;

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, this.rows);
            int regularPos = 0;
            int lastPos = 0;

            for (int p = pStart; p < pEnd; ++p) {
                double[] dataP = data[p];
                int blockIndex = iBlock * this.blockColumns;
                int dataPos = 0;

                for (int jBlock = 0; jBlock < this.blockColumns - 1; ++jBlock) {
                    System.arraycopy(this.blocks[blockIndex++], regularPos, dataP, dataPos, 52);
                    dataPos += 52;
                }

                System.arraycopy(this.blocks[blockIndex], lastPos, dataP, dataPos, lastColumns);
                regularPos += 52;
                lastPos += lastColumns;
            }
        }

        return data;
    }

    public double getNorm() {
        double[] colSums = new double[52];
        double maxColSum = 0.0D;

        for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
            int jWidth = this.blockWidth(jBlock);
            Arrays.fill(colSums, 0, jWidth, 0.0D);

            int j;
            for (j = 0; j < this.blockRows; ++j) {
                int iHeight = this.blockHeight(j);
                double[] block = this.blocks[j * this.blockColumns + jBlock];

                for (int j1 = 0; j1 < jWidth; ++j1) {
                    double sum = 0.0D;

                    for (int i = 0; i < iHeight; ++i) {
                        sum += FastMath.abs(block[i * jWidth + j1]);
                    }

                    colSums[j1] += sum;
                }
            }

            for (j = 0; j < jWidth; ++j) {
                maxColSum = FastMath.max(maxColSum, colSums[j]);
            }
        }

        return maxColSum;
    }

    public double getFrobeniusNorm() {
        double sum2 = 0.0D;

        for (int blockIndex = 0; blockIndex < this.blocks.length; ++blockIndex) {
            double[] arr$ = this.blocks[blockIndex];
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                double entry = arr$[i$];
                sum2 += entry * entry;
            }
        }

        return FastMath.sqrt(sum2);
    }

    public BlockRealMatrix2 getSubMatrix(int startRow, int endRow, int startColumn, int endColumn) throws OutOfRangeException, NumberIsTooSmallException {
        MatrixUtils.checkSubMatrixIndex(this, startRow, endRow, startColumn, endColumn);
        BlockRealMatrix2 out = new BlockRealMatrix2(endRow - startRow + 1, endColumn - startColumn + 1);
        int blockStartRow = startRow / 52;
        int rowsShift = startRow % 52;
        int blockStartColumn = startColumn / 52;
        int columnsShift = startColumn % 52;
        int pBlock = blockStartRow;

        for (int iBlock = 0; iBlock < out.blockRows; ++iBlock) {
            int iHeight = out.blockHeight(iBlock);
            int qBlock = blockStartColumn;

            for (int jBlock = 0; jBlock < out.blockColumns; ++jBlock) {
                int jWidth = out.blockWidth(jBlock);
                int outIndex = iBlock * out.blockColumns + jBlock;
                double[] outBlock = out.blocks[outIndex];
                int index = pBlock * this.blockColumns + qBlock;
                int width = this.blockWidth(qBlock);
                int heightExcess = iHeight + rowsShift - 52;
                int widthExcess = jWidth + columnsShift - 52;
                int width2;
                if (heightExcess > 0) {
                    if (widthExcess > 0) {
                        width2 = this.blockWidth(qBlock + 1);
                        this.copyBlockPart(this.blocks[index], width, rowsShift, 52, columnsShift, 52, outBlock, jWidth, 0, 0);
                        this.copyBlockPart(this.blocks[index + 1], width2, rowsShift, 52, 0, widthExcess, outBlock, jWidth, 0, jWidth - widthExcess);
                        this.copyBlockPart(this.blocks[index + this.blockColumns], width, 0, heightExcess, columnsShift, 52, outBlock, jWidth, iHeight - heightExcess, 0);
                        this.copyBlockPart(this.blocks[index + this.blockColumns + 1], width2, 0, heightExcess, 0, widthExcess, outBlock, jWidth, iHeight - heightExcess, jWidth - widthExcess);
                    } else {
                        this.copyBlockPart(this.blocks[index], width, rowsShift, 52, columnsShift, jWidth + columnsShift, outBlock, jWidth, 0, 0);
                        this.copyBlockPart(this.blocks[index + this.blockColumns], width, 0, heightExcess, columnsShift, jWidth + columnsShift, outBlock, jWidth, iHeight - heightExcess, 0);
                    }
                } else if (widthExcess > 0) {
                    width2 = this.blockWidth(qBlock + 1);
                    this.copyBlockPart(this.blocks[index], width, rowsShift, iHeight + rowsShift, columnsShift, 52, outBlock, jWidth, 0, 0);
                    this.copyBlockPart(this.blocks[index + 1], width2, rowsShift, iHeight + rowsShift, 0, widthExcess, outBlock, jWidth, 0, jWidth - widthExcess);
                } else {
                    this.copyBlockPart(this.blocks[index], width, rowsShift, iHeight + rowsShift, columnsShift, jWidth + columnsShift, outBlock, jWidth, 0, 0);
                }

                ++qBlock;
            }

            ++pBlock;
        }

        return out;
    }

    private void copyBlockPart(double[] srcBlock, int srcWidth, int srcStartRow, int srcEndRow, int srcStartColumn, int srcEndColumn, double[] dstBlock, int dstWidth, int dstStartRow, int dstStartColumn) {
        int length = srcEndColumn - srcStartColumn;
        int srcPos = srcStartRow * srcWidth + srcStartColumn;
        int dstPos = dstStartRow * dstWidth + dstStartColumn;

        for (int srcRow = srcStartRow; srcRow < srcEndRow; ++srcRow) {
            System.arraycopy(srcBlock, srcPos, dstBlock, dstPos, length);
            srcPos += srcWidth;
            dstPos += dstWidth;
        }

    }

    public void setSubMatrix(double[][] subMatrix, int row, int column) throws OutOfRangeException, NoDataException, NullArgumentException, DimensionMismatchException {
        MathUtils.checkNotNull(subMatrix);
        int refLength = subMatrix[0].length;
        if (refLength == 0) {
            throw new NoDataException(LocalizedFormats.AT_LEAST_ONE_COLUMN);
        } else {
            int endRow = row + subMatrix.length - 1;
            int endColumn = column + refLength - 1;
            MatrixUtils.checkSubMatrixIndex(this, row, endRow, column, endColumn);
            double[][] blockStartRow = subMatrix;
            int blockEndRow = subMatrix.length;

            int blockStartColumn;
            for (blockStartColumn = 0; blockStartColumn < blockEndRow; ++blockStartColumn) {
                double[] blockEndColumn = blockStartRow[blockStartColumn];
                if (blockEndColumn.length != refLength) {
                    throw new DimensionMismatchException(refLength, blockEndColumn.length);
                }
            }

            int var24 = row / 52;
            blockEndRow = (endRow + 52) / 52;
            blockStartColumn = column / 52;
            int var25 = (endColumn + 52) / 52;

            for (int iBlock = var24; iBlock < blockEndRow; ++iBlock) {
                int iHeight = this.blockHeight(iBlock);
                int firstRow = iBlock * 52;
                int iStart = FastMath.max(row, firstRow);
                int iEnd = FastMath.min(endRow + 1, firstRow + iHeight);

                for (int jBlock = blockStartColumn; jBlock < var25; ++jBlock) {
                    int jWidth = this.blockWidth(jBlock);
                    int firstColumn = jBlock * 52;
                    int jStart = FastMath.max(column, firstColumn);
                    int jEnd = FastMath.min(endColumn + 1, firstColumn + jWidth);
                    int jLength = jEnd - jStart;
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

                    for (int i = iStart; i < iEnd; ++i) {
                        System.arraycopy(subMatrix[i - row], jStart - column, block, (i - firstRow) * jWidth + (jStart - firstColumn), jLength);
                    }
                }
            }

        }
    }

    public BlockRealMatrix2 getRowMatrix(int row) throws OutOfRangeException {
        MatrixUtils.checkRowIndex(this, row);
        BlockRealMatrix2 out = new BlockRealMatrix2(1, this.columns);
        int iBlock = row / 52;
        int iRow = row - iBlock * 52;
        int outBlockIndex = 0;
        int outIndex = 0;
        double[] outBlock = out.blocks[outBlockIndex];

        for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
            int jWidth = this.blockWidth(jBlock);
            double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
            int available = outBlock.length - outIndex;
            if (jWidth > available) {
                System.arraycopy(block, iRow * jWidth, outBlock, outIndex, available);
                ++outBlockIndex;
                outBlock = out.blocks[outBlockIndex];
                System.arraycopy(block, iRow * jWidth, outBlock, 0, jWidth - available);
                outIndex = jWidth - available;
            } else {
                System.arraycopy(block, iRow * jWidth, outBlock, outIndex, jWidth);
                outIndex += jWidth;
            }
        }

        return out;
    }

    public void setRowMatrix(int row, RealMatrix matrix) throws OutOfRangeException, MatrixDimensionMismatchException {
        try {
            this.setRowMatrix(row, (BlockRealMatrix2) ((BlockRealMatrix2) matrix));
        } catch (ClassCastException var4) {
            super.setRowMatrix(row, matrix);
        }

    }

    public void setRowMatrix(int row, BlockRealMatrix2 matrix) throws OutOfRangeException, MatrixDimensionMismatchException {
        MatrixUtils.checkRowIndex(this, row);
        int nCols = this.getColumnDimension();
        if (matrix.getRowDimension() == 1 && matrix.getColumnDimension() == nCols) {
            int iBlock = row / 52;
            int iRow = row - iBlock * 52;
            int mBlockIndex = 0;
            int mIndex = 0;
            double[] mBlock = matrix.blocks[mBlockIndex];

            for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                int jWidth = this.blockWidth(jBlock);
                double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                int available = mBlock.length - mIndex;
                if (jWidth > available) {
                    System.arraycopy(mBlock, mIndex, block, iRow * jWidth, available);
                    ++mBlockIndex;
                    mBlock = matrix.blocks[mBlockIndex];
                    System.arraycopy(mBlock, 0, block, iRow * jWidth, jWidth - available);
                    mIndex = jWidth - available;
                } else {
                    System.arraycopy(mBlock, mIndex, block, iRow * jWidth, jWidth);
                    mIndex += jWidth;
                }
            }

        } else {
            throw new MatrixDimensionMismatchException(matrix.getRowDimension(), matrix.getColumnDimension(), 1, nCols);
        }
    }

    public BlockRealMatrix2 getColumnMatrix(int column) throws OutOfRangeException {
        MatrixUtils.checkColumnIndex(this, column);
        BlockRealMatrix2 out = new BlockRealMatrix2(this.rows, 1);
        int jBlock = column / 52;
        int jColumn = column - jBlock * 52;
        int jWidth = this.blockWidth(jBlock);
        int outBlockIndex = 0;
        int outIndex = 0;
        double[] outBlock = out.blocks[outBlockIndex];

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int iHeight = this.blockHeight(iBlock);
            double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

            for (int i = 0; i < iHeight; ++i) {
                if (outIndex >= outBlock.length) {
                    ++outBlockIndex;
                    outBlock = out.blocks[outBlockIndex];
                    outIndex = 0;
                }

                outBlock[outIndex++] = block[i * jWidth + jColumn];
            }
        }

        return out;
    }

    public void setColumnMatrix(int column, RealMatrix matrix) throws OutOfRangeException, MatrixDimensionMismatchException {
        try {
            this.setColumnMatrix(column, (BlockRealMatrix2) ((BlockRealMatrix2) matrix));
        } catch (ClassCastException var4) {
            super.setColumnMatrix(column, matrix);
        }

    }

    void setColumnMatrix(int column, BlockRealMatrix2 matrix) throws OutOfRangeException, MatrixDimensionMismatchException {
        MatrixUtils.checkColumnIndex(this, column);
        int nRows = this.getRowDimension();
        if (matrix.getRowDimension() == nRows && matrix.getColumnDimension() == 1) {
            int jBlock = column / 52;
            int jColumn = column - jBlock * 52;
            int jWidth = this.blockWidth(jBlock);
            int mBlockIndex = 0;
            int mIndex = 0;
            double[] mBlock = matrix.blocks[mBlockIndex];

            for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
                int iHeight = this.blockHeight(iBlock);
                double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

                for (int i = 0; i < iHeight; ++i) {
                    if (mIndex >= mBlock.length) {
                        ++mBlockIndex;
                        mBlock = matrix.blocks[mBlockIndex];
                        mIndex = 0;
                    }

                    block[i * jWidth + jColumn] = mBlock[mIndex++];
                }
            }

        } else {
            throw new MatrixDimensionMismatchException(matrix.getRowDimension(), matrix.getColumnDimension(), nRows, 1);
        }
    }

    public RealVector getRowVector(int row) throws OutOfRangeException {
        MatrixUtils.checkRowIndex(this, row);
        double[] outData = new double[this.columns];
        int iBlock = row / 52;
        int iRow = row - iBlock * 52;
        int outIndex = 0;

        for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
            int jWidth = this.blockWidth(jBlock);
            double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
            System.arraycopy(block, iRow * jWidth, outData, outIndex, jWidth);
            outIndex += jWidth;
        }

        return new ArrayRealVector(outData, false);
    }

    public void setRowVector(int row, RealVector vector) throws OutOfRangeException, MatrixDimensionMismatchException {
        try {
            this.setRow(row, ((ArrayRealVector) vector).getDataRef());
        } catch (ClassCastException var4) {
            super.setRowVector(row, vector);
        }

    }

    public RealVector getColumnVector(int column) throws OutOfRangeException {
        MatrixUtils.checkColumnIndex(this, column);
        double[] outData = new double[this.rows];
        int jBlock = column / 52;
        int jColumn = column - jBlock * 52;
        int jWidth = this.blockWidth(jBlock);
        int outIndex = 0;

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int iHeight = this.blockHeight(iBlock);
            double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

            for (int i = 0; i < iHeight; ++i) {
                outData[outIndex++] = block[i * jWidth + jColumn];
            }
        }

        return new ArrayRealVector(outData, false);
    }

    public void setColumnVector(int column, RealVector vector) throws OutOfRangeException, MatrixDimensionMismatchException {
        try {
            this.setColumn(column, ((ArrayRealVector) vector).getDataRef());
        } catch (ClassCastException var4) {
            super.setColumnVector(column, vector);
        }

    }

    public double[] getRow(int row) throws OutOfRangeException {
        MatrixUtils.checkRowIndex(this, row);
        double[] out = new double[this.columns];
        int iBlock = row / 52;
        int iRow = row - iBlock * 52;
        int outIndex = 0;

        for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
            int jWidth = this.blockWidth(jBlock);
            double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
            System.arraycopy(block, iRow * jWidth, out, outIndex, jWidth);
            outIndex += jWidth;
        }

        return out;
    }

    public void setRow(int row, double[] array) throws OutOfRangeException, MatrixDimensionMismatchException {
        MatrixUtils.checkRowIndex(this, row);
        int nCols = this.getColumnDimension();
        if (array.length != nCols) {
            throw new MatrixDimensionMismatchException(1, array.length, 1, nCols);
        } else {
            int iBlock = row / 52;
            int iRow = row - iBlock * 52;
            int outIndex = 0;

            for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                int jWidth = this.blockWidth(jBlock);
                double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                System.arraycopy(array, outIndex, block, iRow * jWidth, jWidth);
                outIndex += jWidth;
            }

        }
    }

    public double[] getColumn(int column) throws OutOfRangeException {
        MatrixUtils.checkColumnIndex(this, column);
        double[] out = new double[this.rows];
        int jBlock = column / 52;
        int jColumn = column - jBlock * 52;
        int jWidth = this.blockWidth(jBlock);
        int outIndex = 0;

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int iHeight = this.blockHeight(iBlock);
            double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

            for (int i = 0; i < iHeight; ++i) {
                out[outIndex++] = block[i * jWidth + jColumn];
            }
        }

        return out;
    }

    public void setColumn(int column, double[] array) throws OutOfRangeException, MatrixDimensionMismatchException {
        MatrixUtils.checkColumnIndex(this, column);
        int nRows = this.getRowDimension();
        if (array.length != nRows) {
            throw new MatrixDimensionMismatchException(array.length, 1, nRows, 1);
        } else {
            int jBlock = column / 52;
            int jColumn = column - jBlock * 52;
            int jWidth = this.blockWidth(jBlock);
            int outIndex = 0;

            for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
                int iHeight = this.blockHeight(iBlock);
                double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

                for (int i = 0; i < iHeight; ++i) {
                    block[i * jWidth + jColumn] = array[outIndex++];
                }
            }

        }
    }

    public double getEntry(int row, int column) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        int iBlock = row / 52;
        int jBlock = column / 52;
        int k = (row - iBlock * 52) * this.blockWidth(jBlock) + (column - jBlock * 52);
        return this.blocks[iBlock * this.blockColumns + jBlock][k];
    }

    public void setEntry(int row, int column, double value) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        int iBlock = row / 52;
        int jBlock = column / 52;
        int k = (row - iBlock * 52) * this.blockWidth(jBlock) + (column - jBlock * 52);
        this.blocks[iBlock * this.blockColumns + jBlock][k] = value;
    }

    public void addToEntry(int row, int column, double increment) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        int iBlock = row / 52;
        int jBlock = column / 52;
        int k = (row - iBlock * 52) * this.blockWidth(jBlock) + (column - jBlock * 52);
        this.blocks[iBlock * this.blockColumns + jBlock][k] += increment;
    }

    public void multiplyEntry(int row, int column, double factor) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        int iBlock = row / 52;
        int jBlock = column / 52;
        int k = (row - iBlock * 52) * this.blockWidth(jBlock) + (column - jBlock * 52);
        this.blocks[iBlock * this.blockColumns + jBlock][k] *= factor;
    }

    public BlockRealMatrix2 transpose() {
        int nRows = this.getRowDimension();
        int nCols = this.getColumnDimension();
        BlockRealMatrix2 out = new BlockRealMatrix2(nCols, nRows);
        int blockIndex = 0;

        for (int iBlock = 0; iBlock < this.blockColumns; ++iBlock) {
            for (int jBlock = 0; jBlock < this.blockRows; ++jBlock) {
                double[] outBlock = out.blocks[blockIndex];
                double[] tBlock = this.blocks[jBlock * this.blockColumns + iBlock];
                int pStart = iBlock * 52;
                int pEnd = FastMath.min(pStart + 52, this.columns);
                int qStart = jBlock * 52;
                int qEnd = FastMath.min(qStart + 52, this.rows);
                int k = 0;

                for (int p = pStart; p < pEnd; ++p) {
                    int lInc = pEnd - pStart;
                    int l = p - pStart;

                    for (int q = qStart; q < qEnd; ++q) {
                        outBlock[k] = tBlock[l];
                        ++k;
                        l += lInc;
                    }
                }

                ++blockIndex;
            }
        }

        return out;
    }

    public int getRowDimension() {
        return this.rows;
    }

    public int getColumnDimension() {
        return this.columns;
    }

    public double[] operate(double[] v) throws DimensionMismatchException {
        if (v.length != this.columns) {
            throw new DimensionMismatchException(v.length, this.columns);
        } else {
            double[] out = new double[this.rows];

            for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
                int pStart = iBlock * 52;
                int pEnd = FastMath.min(pStart + 52, this.rows);

                for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                    int qStart = jBlock * 52;
                    int qEnd = FastMath.min(qStart + 52, this.columns);
                    int k = 0;

                    for (int p = pStart; p < pEnd; ++p) {
                        double sum = 0.0D;

                        int q;
                        for (q = qStart; q < qEnd - 3; q += 4) {
                            sum += block[k] * v[q] + block[k + 1] * v[q + 1] + block[k + 2] * v[q + 2] + block[k + 3] * v[q + 3];
                            k += 4;
                        }

                        while (q < qEnd) {
                            sum += block[k++] * v[q++];
                        }

                        out[p] += sum;
                    }
                }
            }

            return out;
        }
    }

    public double[] preMultiply(double[] v) throws DimensionMismatchException {
        if (v.length != this.rows) {
            throw new DimensionMismatchException(v.length, this.rows);
        } else {
            double[] out = new double[this.columns];

            for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                int jWidth = this.blockWidth(jBlock);
                int jWidth2 = jWidth + jWidth;
                int jWidth3 = jWidth2 + jWidth;
                int jWidth4 = jWidth3 + jWidth;
                int qStart = jBlock * 52;
                int qEnd = FastMath.min(qStart + 52, this.columns);

                for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                    int pStart = iBlock * 52;
                    int pEnd = FastMath.min(pStart + 52, this.rows);

                    for (int q = qStart; q < qEnd; ++q) {
                        int k = q - qStart;
                        double sum = 0.0D;

                        int p;
                        for (p = pStart; p < pEnd - 3; p += 4) {
                            sum += block[k] * v[p] + block[k + jWidth] * v[p + 1] + block[k + jWidth2] * v[p + 2] + block[k + jWidth3] * v[p + 3];
                            k += jWidth4;
                        }

                        while (p < pEnd) {
                            sum += block[k] * v[p++];
                            k += jWidth;
                        }

                        out[q] += sum;
                    }
                }
            }

            return out;
        }
    }

    public double walkInRowOrder(RealMatrixChangingVisitor visitor) {
        visitor.start(this.rows, this.columns, 0, this.rows - 1, 0, this.columns - 1);

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, this.rows);

            for (int p = pStart; p < pEnd; ++p) {
                for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                    int jWidth = this.blockWidth(jBlock);
                    int qStart = jBlock * 52;
                    int qEnd = FastMath.min(qStart + 52, this.columns);
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                    int k = (p - pStart) * jWidth;

                    for (int q = qStart; q < qEnd; ++q) {
                        block[k] = visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }
            }
        }

        return visitor.end();
    }

    public double walkInRowOrder(RealMatrixPreservingVisitor visitor) {
        visitor.start(this.rows, this.columns, 0, this.rows - 1, 0, this.columns - 1);

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, this.rows);

            for (int p = pStart; p < pEnd; ++p) {
                for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                    int jWidth = this.blockWidth(jBlock);
                    int qStart = jBlock * 52;
                    int qEnd = FastMath.min(qStart + 52, this.columns);
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                    int k = (p - pStart) * jWidth;

                    for (int q = qStart; q < qEnd; ++q) {
                        visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }
            }
        }

        return visitor.end();
    }

    public double walkInRowOrder(RealMatrixChangingVisitor visitor, int startRow, int endRow, int startColumn, int endColumn) throws OutOfRangeException, NumberIsTooSmallException {
        MatrixUtils.checkSubMatrixIndex(this, startRow, endRow, startColumn, endColumn);
        visitor.start(this.rows, this.columns, startRow, endRow, startColumn, endColumn);

        for (int iBlock = startRow / 52; iBlock < 1 + endRow / 52; ++iBlock) {
            int p0 = iBlock * 52;
            int pStart = FastMath.max(startRow, p0);
            int pEnd = FastMath.min((iBlock + 1) * 52, 1 + endRow);

            for (int p = pStart; p < pEnd; ++p) {
                for (int jBlock = startColumn / 52; jBlock < 1 + endColumn / 52; ++jBlock) {
                    int jWidth = this.blockWidth(jBlock);
                    int q0 = jBlock * 52;
                    int qStart = FastMath.max(startColumn, q0);
                    int qEnd = FastMath.min((jBlock + 1) * 52, 1 + endColumn);
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                    int k = (p - p0) * jWidth + qStart - q0;

                    for (int q = qStart; q < qEnd; ++q) {
                        block[k] = visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }
            }
        }

        return visitor.end();
    }

    public double walkInRowOrder(RealMatrixPreservingVisitor visitor, int startRow, int endRow, int startColumn, int endColumn) throws OutOfRangeException, NumberIsTooSmallException {
        MatrixUtils.checkSubMatrixIndex(this, startRow, endRow, startColumn, endColumn);
        visitor.start(this.rows, this.columns, startRow, endRow, startColumn, endColumn);

        for (int iBlock = startRow / 52; iBlock < 1 + endRow / 52; ++iBlock) {
            int p0 = iBlock * 52;
            int pStart = FastMath.max(startRow, p0);
            int pEnd = FastMath.min((iBlock + 1) * 52, 1 + endRow);

            for (int p = pStart; p < pEnd; ++p) {
                for (int jBlock = startColumn / 52; jBlock < 1 + endColumn / 52; ++jBlock) {
                    int jWidth = this.blockWidth(jBlock);
                    int q0 = jBlock * 52;
                    int qStart = FastMath.max(startColumn, q0);
                    int qEnd = FastMath.min((jBlock + 1) * 52, 1 + endColumn);
                    double[] block = this.blocks[iBlock * this.blockColumns + jBlock];
                    int k = (p - p0) * jWidth + qStart - q0;

                    for (int q = qStart; q < qEnd; ++q) {
                        visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }
            }
        }

        return visitor.end();
    }

    public double walkInOptimizedOrder(RealMatrixChangingVisitor visitor) {
        visitor.start(this.rows, this.columns, 0, this.rows - 1, 0, this.columns - 1);
        int blockIndex = 0;

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, this.rows);

            for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                int qStart = jBlock * 52;
                int qEnd = FastMath.min(qStart + 52, this.columns);
                double[] block = this.blocks[blockIndex];
                int k = 0;

                for (int p = pStart; p < pEnd; ++p) {
                    for (int q = qStart; q < qEnd; ++q) {
                        block[k] = visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }

                ++blockIndex;
            }
        }

        return visitor.end();
    }

    public double walkInOptimizedOrder(RealMatrixPreservingVisitor visitor) {
        visitor.start(this.rows, this.columns, 0, this.rows - 1, 0, this.columns - 1);
        int blockIndex = 0;

        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
            int pStart = iBlock * 52;
            int pEnd = FastMath.min(pStart + 52, this.rows);

            for (int jBlock = 0; jBlock < this.blockColumns; ++jBlock) {
                int qStart = jBlock * 52;
                int qEnd = FastMath.min(qStart + 52, this.columns);
                double[] block = this.blocks[blockIndex];
                int k = 0;

                for (int p = pStart; p < pEnd; ++p) {
                    for (int q = qStart; q < qEnd; ++q) {
                        visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }

                ++blockIndex;
            }
        }

        return visitor.end();
    }

    public double walkInOptimizedOrder(RealMatrixChangingVisitor visitor, int startRow, int endRow, int startColumn, int endColumn) throws OutOfRangeException, NumberIsTooSmallException {
        MatrixUtils.checkSubMatrixIndex(this, startRow, endRow, startColumn, endColumn);
        visitor.start(this.rows, this.columns, startRow, endRow, startColumn, endColumn);

        for (int iBlock = startRow / 52; iBlock < 1 + endRow / 52; ++iBlock) {
            int p0 = iBlock * 52;
            int pStart = FastMath.max(startRow, p0);
            int pEnd = FastMath.min((iBlock + 1) * 52, 1 + endRow);

            for (int jBlock = startColumn / 52; jBlock < 1 + endColumn / 52; ++jBlock) {
                int jWidth = this.blockWidth(jBlock);
                int q0 = jBlock * 52;
                int qStart = FastMath.max(startColumn, q0);
                int qEnd = FastMath.min((jBlock + 1) * 52, 1 + endColumn);
                double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

                for (int p = pStart; p < pEnd; ++p) {
                    int k = (p - p0) * jWidth + qStart - q0;

                    for (int q = qStart; q < qEnd; ++q) {
                        block[k] = visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }
            }
        }

        return visitor.end();
    }

    public double walkInOptimizedOrder(RealMatrixPreservingVisitor visitor, int startRow, int endRow, int startColumn, int endColumn) throws OutOfRangeException, NumberIsTooSmallException {
        MatrixUtils.checkSubMatrixIndex(this, startRow, endRow, startColumn, endColumn);
        visitor.start(this.rows, this.columns, startRow, endRow, startColumn, endColumn);

        for (int iBlock = startRow / 52; iBlock < 1 + endRow / 52; ++iBlock) {
            int p0 = iBlock * 52;
            int pStart = FastMath.max(startRow, p0);
            int pEnd = FastMath.min((iBlock + 1) * 52, 1 + endRow);

            for (int jBlock = startColumn / 52; jBlock < 1 + endColumn / 52; ++jBlock) {
                int jWidth = this.blockWidth(jBlock);
                int q0 = jBlock * 52;
                int qStart = FastMath.max(startColumn, q0);
                int qEnd = FastMath.min((jBlock + 1) * 52, 1 + endColumn);
                double[] block = this.blocks[iBlock * this.blockColumns + jBlock];

                for (int p = pStart; p < pEnd; ++p) {
                    int k = (p - p0) * jWidth + qStart - q0;

                    for (int q = qStart; q < qEnd; ++q) {
                        visitor.visit(p, q, block[k]);
                        ++k;
                    }
                }
            }
        }

        return visitor.end();
    }

    private int blockHeight(int blockRow) {
        return blockRow == this.blockRows - 1 ? this.rows - blockRow * 52 : 52;
    }

    private int blockWidth(int blockColumn) {
        return blockColumn == this.blockColumns - 1 ? this.columns - blockColumn * 52 : 52;
    }
}

