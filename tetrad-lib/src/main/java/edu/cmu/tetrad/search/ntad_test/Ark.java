package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.MathUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Ark class extends the NtadTest class and provides a mechanism to perform statistical operations based on tetrads
 * and their probabilities. It leverages covariance computation, sampling, and matrix manipulation to calculate p-values
 * and z-scores for tetrads. This class is specifically designed to operate on instances of SimpleMatrix for
 * multivariate analysis.
 *
 * @author bryanandrews
 */
public class Ark extends NtadTest {
    private final SimpleMatrix S1;
    private final SimpleMatrix S2;
    private final double sp;

    /**
     * Constructs an Ark object based on the given data matrix and split proportion. This method initializes the Ark
     * analysis by splitting the data matrix into two segments based on the given split proportion, and computes the
     * covariance matrix for each segment.
     *
     * @param df the input data matrix as a SimpleMatrix object, where each row represents an observation and each
     *           column represents a variable.
     * @param sp the split proportion, a value between 0 and 1, which determines the proportion of the dataset allocated
     *           to the first segment of the split. If the given value is not valid, it is adjusted towards the
     *           complementary split (1 - sp instead of sp). Is the value is 1, the full dataset is used throughout.
     */
    public Ark(SimpleMatrix df, double sp) {
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

        // if self.sp < 1: XY = S2[np.ix_(a, b)]
        //        else: XY = S1[np.ix_(a, b)]

        SimpleMatrix XY = this.sp < 1 ? extractSubMatrix(S2, a, b) : extractSubMatrix(S1, a, b);
        SimpleSVD<SimpleMatrix> svd = XY.svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix VT = svd.getV().transpose();

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
        double z_score = MathUtils.arctanh(p_corr) * Math.sqrt(n - idx.length - 1);

        NormalDistribution normalDist = new NormalDistribution();
        return 2 * normalDist.cumulativeProbability(-Math.abs(z_score));
    }

    @Override
    public double tetrad(int[][] tet) {
        return tetrad(tet, false, 1);
    }

    @Override
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    @Override
    public double tetrads(List<int[][]> tets) {
        double sum = 0.0;
        int count = 0;

        for (int[][] tet : tets) {
            if (tet.length != 2) {
                throw new IllegalArgumentException("Each tetrad must contain two pairs of nodes.");
            }
            if (tet[0].length != tet[1].length) {
                throw new IllegalArgumentException("Each pair of nodes must have the same length.");
            }

            double pValue = this.tetrad(tet);
            if (pValue == 0) {
                sum = Double.NEGATIVE_INFINITY;
            } else {
                sum += Math.log(pValue);
            }

            count++;
        }

        sum *= -2;
        return 1.0 - new ChiSquaredDistribution(2 * count).cumulativeProbability(sum);
    }
}

