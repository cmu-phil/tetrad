
package edu.cmu.tetrad.data;

import static java.lang.Math.round;

/**
 * Compute covariance using the standard calculation.
 * <p>
 * Jan 27, 2016 4:37:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class CovariancesFloatStandard implements Covariances {
    private final float[][] _data;
    private final int numOfRows;
    private final int numOfCols;
    private float[][] covariances;

    public CovariancesFloatStandard(double[][] data, boolean biasCorrected) {
        _data = new float[data.length][data[0].length];

        for (int i = 0; i < _data.length; i++) {
            for (int j = 0; j < _data[0].length; j++) {
                this._data[i][j] = (float) (round(data[i][j] * 100.0) / 100.0);
            }
        }

        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
        this.covariances = compute(biasCorrected);
    }

    public CovariancesFloatStandard(float[][] data, boolean biasCorrected) {
        _data = new float[data.length][data[0].length];

        for (int i = 0; i < _data.length; i++) {
            for (int j = 0; j < _data[0].length; j++) {
                this._data[i][j] = data[i][j];
            }
        }

        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
        this.covariances = compute(biasCorrected);
    }

    public CovariancesFloatStandard(double[][] matrix, int sampleSize) {
        this.covariances = new float[matrix.length][matrix[0].length];

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                covariances[i][j] = (float) matrix[i][j];
            }
        }

        this.numOfCols = matrix.length;
        this.numOfRows = sampleSize;
        this._data = null;
    }

    public float[] computeLowerTriangle(boolean biasCorrected) {
        float[] covarianceMatrix = new float[(numOfCols * (numOfCols + 1)) / 2];

        center();

        int index = 0;
        for (int col = 0; col < numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                float variance = 0;
                for (int row = 0; row < numOfRows; row++) {
                    variance += ((_data[row][col]) * (_data[row][col2]));
                }
                variance /= numOfRows - 1;
                covarianceMatrix[index++] = biasCorrected ? variance * ((float) numOfRows / (float) (numOfRows - 1)) : variance;
            }
            float variance = 0;
            for (int row = 0; row < numOfRows; row++) {
                variance += ((_data[row][col]) * (_data[row][col]));
            }
            variance /= numOfRows - 1;
            covarianceMatrix[index++] = biasCorrected ? variance * ((float) numOfRows / (float) (numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

    public float[][] compute(boolean biasCorrected) {
        float[][] covarianceMatrix = new float[numOfCols][numOfCols];

        center();

        for (int col1 = 0; col1 < numOfCols; col1++) {
            for (int col2 = 0; col2 < col1; col2++) {
                float cov = 0;
                for (int row = 0; row < numOfRows; row++) {
                    cov += ((_data[row][col1]) * (_data[row][col2]));
                }
                cov /= numOfRows - 1;
                cov = biasCorrected ? cov * ((float) numOfRows / (float) (numOfRows - 1)) : cov;
                covarianceMatrix[col1][col2] = cov;
                covarianceMatrix[col2][col1] = cov;
            }
            float variance = 0;
            for (int row = 0; row < numOfRows; row++) {
                variance += ((_data[row][col1]) * (_data[row][col1])) / (numOfRows - 1);
            }
            covarianceMatrix[col1][col1] = biasCorrected ? variance * ((float) numOfRows / (float) (numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

    private void center() {
        for (int col = 0; col < numOfCols; col++) {
            double mean = 0;
            for (int row = 0; row < numOfRows; row++) {
                mean += _data[row][col];
            }
            mean /= numOfRows;
            for (int row = 0; row < numOfRows; row++) {
                _data[row][col] -= mean;
            }
        }
    }

    @Override
    public double covariance(int i, int j) {
        return covariances[i][j];
    }

    public int size() {
        return numOfCols;
    }

    public void setCovariance(int i, int j, double v) {
        covariances[i][j] = (float) v;
        covariances[j][i] = (float) v;
    }

    @Override
    public double[][] getMatrix() {
        int[] rows = new int[size()];
        for (int i = 0; i < rows.length; i++) rows[i] = i;
        return getSubMatrix(rows, rows);
    }

    @Override
    public double[][] getSubMatrix(int[] rows, int[] cols) {
        double[][] submatrix = new double[rows.length][cols.length];

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                submatrix[i][j] = covariances[rows[i]][cols[j]];
            }
        }

        return submatrix;
    }
}

