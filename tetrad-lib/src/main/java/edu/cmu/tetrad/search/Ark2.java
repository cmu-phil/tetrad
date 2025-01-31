package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.MathUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class Ark2 extends NTadTest2 {
    private double sp;
    private final SimpleMatrix S1;
    private final SimpleMatrix S2;

    public Ark2(SimpleMatrix df) {
        super(df);
        this.S1 = computeCovariance(df);
        this.S2 = computeCovariance(df);
    }

    public Ark2(SimpleMatrix df, double sp) {
        super(df);
        this.sp = sp > 0 ? sp : 1 - sp;
        int splitIndex = (int) (this.sp * df.getNumRows());
        this.S1 = computeCovariance(df.extractMatrix(0, splitIndex, 0, df.getNumCols()));
        this.S2 = computeCovariance(df.extractMatrix(splitIndex, df.getNumRows(), 0, df.getNumCols()));
    }

    @Override
    public double tetrad(int[][] tet, boolean resample, double frac) {
        SimpleMatrix S1, S2;
        int n;

        if (resample) {
            SimpleMatrix sampledDf = sampleRows(df, frac);
            n = sampledDf.getNumRows();
            int splitIndex = (int) (this.sp * n);
            S1 = computeCovariance(sampledDf.extractMatrix(0, splitIndex, 0, sampledDf.getNumCols()));
            S2 = computeCovariance(sampledDf.extractMatrix(splitIndex, n, 0, sampledDf.getNumCols()));
        } else {
            n = this.n;
            S1 = this.S1;
            S2 = this.S2;
        }

        int[] a = tet[0];
        int[] b = tet[1];
        int z = a.length;

        SimpleMatrix XY = extractSubMatrix(S2, a, b);
        SimpleSVD<SimpleMatrix> svd = XY.svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix VT = svd.getV().transpose();

        XY = this.sp < 1 ? extractSubMatrix(S1, a, b) : XY;
        SimpleMatrix XXi = extractSubMatrix(S1, a, a).invert();
        SimpleMatrix YYi = extractSubMatrix(S1, b, b).invert();

        SimpleMatrix A = U.transpose().mult(XXi).mult(U);
        SimpleMatrix B = VT.mult(YYi).mult(VT.transpose());
        SimpleMatrix C = U.transpose().mult(XXi).mult(XY).mult(YYi).mult(VT.transpose());

        int[] indicesA = new int[z];
        int[] indicesB = new int[z];
        for (int i = 0; i < z; i++) {
            indicesA[i] = i;
            indicesB[i] = i + z;
        }

        SimpleMatrix R = new SimpleMatrix(2 * z, 2 * z);
        R.insertIntoThis(0, 0, A);
        R.insertIntoThis(0, z, C);
        R.insertIntoThis(z, 0, C.transpose());
        R.insertIntoThis(z, z, B);

        SimpleMatrix D = new SimpleMatrix(2 * z, 2 * z);
        for (int i = 0; i < 2 * z; i++) {
            D.set(i, i, Math.sqrt(R.get(i, i)));
        }

        SimpleMatrix Di = D.invert();
        R = Di.mult(R).mult(Di);

        int[] idx = new int[z + 1];
        idx[0] = indicesA[z - 1];
        idx[1] = indicesB[z - 1];
        System.arraycopy(indicesA, 0, idx, 2, z - 1);

        SimpleMatrix subR = extractSubMatrix(R, idx, idx).invert();

        double p_corr = -subR.get(0, 1) / Math.sqrt(subR.get(0, 0) * subR.get(1, 1));
        double z_score = MathUtils.arctanh(p_corr) * Math.sqrt(sp * n - idx.length - 1);

        NormalDistribution normalDist = new NormalDistribution();
        return 2 * normalDist.cumulativeProbability(-Math.abs(z_score));
    }

    @Override
    public double tetrad(int[][] tet) {
        return tetrad(tet, false, 1);
    }

    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    public double tetrads(List<int[][]> tets) {
        List<Double> p_values = new ArrayList<>();

        for (int[][] tet : tets) {
            if (tet.length != 2) {
                throw new IllegalArgumentException("Each tetrad must contain two pairs of nodes.");
            }
            if (tet[0].length != tet[1].length) {
                throw new IllegalArgumentException("Each pair of nodes must have the same length.");
            }

            double pValue = this.tetrad(tet);

            pValue = Math.max(pValue, 1e-16);
            p_values.add(pValue);
        }

        double sum = 0.0;

        for (double p : p_values) {
            sum += Math.log(p);
        }

        sum *= -2;
        return 1.0 - new ChiSquaredDistribution(2 * p_values.size()).cumulativeProbability(sum);
    }
}

