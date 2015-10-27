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

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.AbstractRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;

import java.io.Serializable;

public class BlockRealMatrix4 extends AbstractRealMatrix implements Serializable {
    public static final int BLOCK_SIZE = 52;
    private static final long serialVersionUID = 4991895511313664478L;
    private final OpenMapRealMatrix[] blocks;
    private final int rows;
    private final int columns;
    private final int blockRows;
    private final int blockColumns;

    public BlockRealMatrix4(int rows, int columns) throws NotStrictlyPositiveException {
        super(rows, columns);
        this.rows = rows;
        this.columns = columns;
        this.blockRows = (rows + BLOCK_SIZE - 1) / BLOCK_SIZE;
        this.blockColumns = (columns + BLOCK_SIZE - 1) / BLOCK_SIZE;
        this.blocks = createBlocksLayout(rows, columns);
    }

    public BlockRealMatrix4(double[][] rawData) throws DimensionMismatchException, NotStrictlyPositiveException {
        this(rawData.length, rawData[0].length, toBlocksLayout(rawData), false);
    }

    public BlockRealMatrix4(int rows, int columns, OpenMapRealMatrix[] blockData, boolean copyArray) throws DimensionMismatchException, NotStrictlyPositiveException {
        super(rows, columns);
        this.rows = rows;
        this.columns = columns;
        this.blockRows = (rows + BLOCK_SIZE - 1) / BLOCK_SIZE;
        this.blockColumns = (columns + BLOCK_SIZE - 1) / BLOCK_SIZE;
        if (copyArray) {
            this.blocks = new OpenMapRealMatrix[this.blockRows * this.blockColumns];
        } else {
            this.blocks = blockData;
        }

        int index = 0;

//        for (int iBlock = 0; iBlock < this.blockRows; ++iBlock) {
//            int iHeight = this.blockHeight(iBlock);
//
//            for (int jBlock = 0; jBlock < this.blockColumns; ++index) {
//                if (blockData[index].length != iHeight * this.blockWidth(jBlock)) {
//                    throw new DimensionMismatchException(blockData[index].length, iHeight * this.blockWidth(jBlock));
//                }
//
//                if (copyArray) {
//                    this.blocks[index] = (OpenMapRealMatrix) blockData[index].clone();
//                }
//
//                ++jBlock;
//            }
//        }

    }

    public static OpenMapRealMatrix[] toBlocksLayout(double[][] rawData) throws DimensionMismatchException {
        int rows = rawData.length;
        int columns = rawData[0].length;
        int blockRows = (rows + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int blockColumns = (columns + BLOCK_SIZE - 1) / BLOCK_SIZE;

        int blockIndex;
        for (int blocks = 0; blocks < rawData.length; ++blocks) {
            blockIndex = rawData[blocks].length;
            if (blockIndex != columns) {
                throw new DimensionMismatchException(columns, blockIndex);
            }
        }

        OpenMapRealMatrix[] var18 = new OpenMapRealMatrix[blockRows * blockColumns];
        blockIndex = 0;

        for (int iBlock = 0; iBlock < blockRows; ++iBlock) {
            int pStart = iBlock * BLOCK_SIZE;
            int pEnd = FastMath.min(pStart + BLOCK_SIZE, rows);
            int iHeight = pEnd - pStart;

            for (int jBlock = 0; jBlock < blockColumns; ++jBlock) {
                int qStart = jBlock * BLOCK_SIZE;
                int qEnd = FastMath.min(qStart + BLOCK_SIZE, columns);
                int jWidth = qEnd - qStart;
                OpenMapRealMatrix block = new OpenMapRealMatrix(iHeight, jWidth);
                var18[blockIndex] = block;
                
                for (int p = pStart; p < pEnd; p++) {
                    for (int q = qStart; q < qEnd; q++) {
                        block.setEntry(p, q, rawData[p][q]);
                    }
                }

                ++blockIndex;
            }
        }

        return var18;
    }

    public static OpenMapRealMatrix[] createBlocksLayout(int rows, int columns) {
        int blockRows = (rows + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int blockColumns = (columns + BLOCK_SIZE - 1) / BLOCK_SIZE;
        OpenMapRealMatrix[] blocks = new OpenMapRealMatrix[blockRows * blockColumns];
        int blockIndex = 0;

        for (int iBlock = 0; iBlock < blockRows; ++iBlock) {
            int pStart = iBlock * BLOCK_SIZE;
            int pEnd = FastMath.min(pStart + BLOCK_SIZE, rows);
            int iHeight = pEnd - pStart;

            for (int jBlock = 0; jBlock < blockColumns; ++jBlock) {
                int qStart = jBlock * BLOCK_SIZE;
                int qEnd = FastMath.min(qStart + BLOCK_SIZE, columns);
                int jWidth = qEnd - qStart;
                blocks[blockIndex] = new OpenMapRealMatrix(iHeight, jWidth);
                ++blockIndex;
            }
        }

        return blocks;
    }

    public RealMatrix createMatrix(int rowDimension, int columnDimension) throws NotStrictlyPositiveException {
        return new BlockRealMatrix4(rowDimension, columnDimension);
    }

    public RealMatrix copy() {
        BlockRealMatrix4 copied = new BlockRealMatrix4(this.rows, this.columns);

        for (int i = 0; i < this.blocks.length; ++i) {
            copied.blocks[i] = new OpenMapRealMatrix(blocks[i]);
        }

        return copied;
    }

    public double getEntry(int row, int column) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        int iBlock = row / BLOCK_SIZE;
        int jBlock = column / BLOCK_SIZE;
        final int p = row - iBlock * BLOCK_SIZE;
        final int q = column - jBlock * BLOCK_SIZE;
//        int k = p * this.blockWidth(jBlock) + q;
        return this.blocks[iBlock * this.blockColumns + jBlock].getEntry(p, q);
    }

    public void setEntry(int row, int column, double value) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        int iBlock = row / BLOCK_SIZE;
        int jBlock = column / BLOCK_SIZE;
        final int p = row - iBlock * BLOCK_SIZE;
        final int q = column - jBlock * BLOCK_SIZE;
//        int k = p * this.blockWidth(jBlock) + q;
        this.blocks[iBlock * this.blockColumns + jBlock].setEntry(p, q, (float) value);
    }

    public RealMatrix transpose() {
        return super.transpose();
//        int nRows = this.getRowDimension();
//        int nCols = this.getColumnDimension();
//        BlockRealMatrix4 out = new BlockRealMatrix4(nCols, nRows);
//        int blockIndex = 0;
//
//        for (int iBlock = 0; iBlock < this.blockColumns; ++iBlock) {
//            for (int jBlock = 0; jBlock < this.blockRows; ++jBlock) {
//                OpenMapRealMatrix outBlock = out.blocks[blockIndex];
//                OpenMapRealMatrix tBlock = this.blocks[jBlock * this.blockColumns + iBlock];
//                int pStart = iBlock * BLOCK_SIZE;
//                int pEnd = FastMath.min(pStart + BLOCK_SIZE, this.columns);
//                int qStart = jBlock * BLOCK_SIZE;
//                int qEnd = FastMath.min(qStart + BLOCK_SIZE, this.rows);
//                int k = 0;
//
//                for (int p = pStart; p < pEnd; ++p) {
//                    int lInc = pEnd - pStart;
//                    int l = p - pStart;
//
//                    for (int q = qStart; q < qEnd; ++q) {
//                        outBlock[k] = tBlock[l];
//                        ++k;
//                        l += lInc;
//                    }
//                }
//
//                ++blockIndex;
//            }
//        }
//
//        return out;
    }

    public int getRowDimension() {
        return this.rows;
    }

    public int getColumnDimension() {
        return this.columns;
    }


    private int blockHeight(int blockRow) {
        return blockRow == this.blockRows - 1 ? this.rows - blockRow * BLOCK_SIZE : BLOCK_SIZE;
    }

    private int blockWidth(int blockColumn) {
        return blockColumn == this.blockColumns - 1 ? this.columns - blockColumn * BLOCK_SIZE : BLOCK_SIZE;
    }
}

