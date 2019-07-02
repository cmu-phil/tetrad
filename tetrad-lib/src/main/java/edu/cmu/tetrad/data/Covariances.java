package edu.cmu.tetrad.data;

public interface Covariances {
    double covariance(int i, int j);

    int size();

    void setCovariance(int i, int j, double v);

    double[][] getMatrix();

    double[][] getSubMatrix(int[] rows, int[] cols);
}
