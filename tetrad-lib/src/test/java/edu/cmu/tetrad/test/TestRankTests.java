package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RankTests;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.ejml.simple.SimpleMatrix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class TestRankTests {

    public static void main(String[] args) {
        double alpha = 0.05;
        double regParam = 0.01;
        double essMult = 1;
        int size = 3;
        int rank = 2;

        test(size, rank, true, alpha, regParam, essMult);
        test(size, rank, false, alpha, regParam, essMult);
    }

    private static void test(int size, int rank, boolean coherent, double alpha, double regParam, double essMult) {
        try {
            DataSet data = SimpleDataLoader.loadContinuousData(
                    new File("/Users/josephramsey/IdeaProjects/BryanStuff/lv_work/check_ranks.csv"),
                    "//", '"', "*", true, Delimiter.COMMA, false);
            SimpleMatrix S = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();
            int dim = S.getNumRows();
            int origSize = dim / 2;

            int[] dims = new int[size];
            Arrays.fill(dims, dim);

//            CombinationGenerator gen = new CombinationGenerator(dims);
            ChoiceGenerator gen = new ChoiceGenerator(dim, size);
            int[] indexx;

            C:
            while ((indexx = gen.next()) != null) {
//                System.out.println("   " + Arrays.toString(indexx));

//                boolean hasDuplicates = false;
//                for (int i = 0; i < indexx.length; i++) {
//                    for (int j = i + 1; j < indexx.length; j++) {
//                        if (indexx[i] == indexx[j]) {
//                            hasDuplicates = true;
//                            break;
//                        }
//                    }
//                    if (hasDuplicates) break;
//                }
//                if (hasDuplicates) continue;


                boolean hasSmaller = false;
                boolean hasLarger = false;

                for (int idx : indexx) {
                    if (idx < origSize) {
                        hasSmaller = true;
                    } else {
                        hasLarger = true;
                    }
                }

                if (coherent && !((hasSmaller && !hasLarger) || (!hasSmaller && hasLarger))) {
                    continue;
                }

                if (!coherent && !(hasSmaller && hasLarger)) {
                    continue;
                }

                ArrayList<Integer> indices = new ArrayList<>();

//                System.out.println(Arrays.toString(indexx));

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

                int ess = (int) (data.getNumRows() * essMult);
                double p = RankTests.getRccaPValueRankLE(S, indexx, indexy, ess, rank, regParam);
                int estRank = RankTests.estimateRccaRank(S, indexx, indexy, ess, alpha, regParam);

                // Print out the discrepancies
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
