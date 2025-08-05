package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.RankTests;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.ejml.simple.SimpleMatrix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class TestRankTests {

    public static void main(String[] args) {
        double alpha = 0.01;
        double regParam = 0.01;

        test(4, 1, true, alpha, regParam);
        test(4, 1, false, alpha, regParam);
    }

    private static void test(int size, int rank, boolean coherent, double alpha, double regParam) {
        try {
            DataSet data = SimpleDataLoader.loadContinuousData(
                    new File("/Users/josephramsey/IdeaProjects/BryanStuff/lv_work/check_ranks.csv"),
                    "//", '"', "*", true, Delimiter.COMMA, false);
            SimpleMatrix S = new CovarianceMatrix(data).getMatrix().getDataCopy();
            int dim = S.getNumRows();
            int origSize = dim / 2;
            S = S.plus(SimpleMatrix.identity(dim).scale(regParam));

            int[] dims = new int[size];
            Arrays.fill(dims, dim);

            CombinationGenerator gen = new CombinationGenerator(dims);
            int[] indexx;

            C:
            while ((indexx = gen.next()) != null) {
                boolean hasDuplicates = false;
                for (int i = 0; i < indexx.length; i++) {
                    for (int j = i + 1; j < indexx.length; j++) {
                        if (indexx[i] == indexx[j]) {
                            hasDuplicates = true;
                            break;
                        }
                    }
                    if (hasDuplicates) break;
                }
                if (hasDuplicates) continue;


                if ((coherent != (indexx[0] < origSize) || (coherent != (indexx[1] < origSize)))) {
                    continue;
                }

                for (int q = 2; q < indexx.length; q++) {
                    if (indexx[q] >= origSize) {
                        continue C;
                    }
                }

                ArrayList<Integer> indices = new ArrayList<>();

                Q:
                for (int q = 0; q < dim; q++) {
                    for (int i : indexx) {
                        if (i == q) {
                            continue Q;
                        }
                    }

                    indices.add(q);
                }
                int[] indexy = indices.stream().mapToInt(Integer::intValue).toArray();

                double p = RankTests.getRccaPValueRankLE(S, indexx, indexy, data.getNumRows(), rank, regParam);
                int estRank = RankTests.estimateRccaRank(S, indexx, indexy, data.getNumRows(), alpha, regParam);

                if ((coherent && p < alpha) || (!coherent && p > alpha)) {
                    System.out.println((coherent ? "coher" : "incoh") + " size = " + size + " rank = " + rank + ": " + Arrays.toString(indexx)
                                       + " p = " + p + (p > alpha ? " p > alpha" : " p < alpha") + " est_rank = " + estRank);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
