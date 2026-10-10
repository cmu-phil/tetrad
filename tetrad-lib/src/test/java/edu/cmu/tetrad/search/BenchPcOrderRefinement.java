package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * Hand-run benchmark (not a JUnit test) comparing PC with and without order refinement, and BOSS, on small sparse
 * linear Gaussian models. Run main() with no arguments for the 10-node/10-edge and 20-node/20-edge cases, or with
 * arguments: numNodes numEdges sampleSize numRuns coefLow.
 * <p>
 * All accuracy figures are against the true CPDAG. AP/AR are adjacency precision and recall, AHP/AHR arrowhead
 * precision and recall, SHD counts a pair as one error if its adjacency or either endpoint differs, and "exact" is
 * the fraction of runs with SHD zero.
 */
public class BenchPcOrderRefinement {

    private interface Alg {
        Graph run(ICovarianceMatrix cov) throws Exception;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 5) {
            bench(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]), Double.parseDouble(args[4]));
            return;
        }

        for (double coefLow : new double[]{0.0, 0.3}) {
            bench(10, 10, 1000, 300, coefLow);
            bench(20, 20, 1000, 200, coefLow);
        }
    }

    private static void bench(int numNodes, int numEdges, int sampleSize, int numRuns, double coefLow)
            throws Exception {
        Map<String, Alg> algs = new LinkedHashMap<>();
        algs.put("PC sepsets", cov -> pc(cov, Pc.ColliderOrientationStyle.SEPSETS, false, 0));
        algs.put("PC max-p", cov -> pc(cov, Pc.ColliderOrientationStyle.MAX_P, false, 0));
        algs.put("PC max-p + refinement (0.001)", cov -> pc(cov, Pc.ColliderOrientationStyle.MAX_P, true, 0.001));
        algs.put("PC max-p + refinement (alpha)", cov -> pc(cov, Pc.ColliderOrientationStyle.MAX_P, true, 0));
        algs.put("BOSS penalty 2, 1 start", cov -> boss(cov, 1));
        algs.put("BOSS penalty 2, 10 starts", cov -> boss(cov, 10));

        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, Long> nanos = new LinkedHashMap<>();
        for (String name : algs.keySet()) {
            sums.put(name, new double[8]);
            nanos.put(name, 0L);
        }

        Parameters parameters = new Parameters();
        parameters.set(Params.COEF_LOW, coefLow);
        parameters.set(Params.COEF_HIGH, 1.0);

        for (int r = 0; r < numRuns; r++) {
            RandomUtil.getInstance().setSeed(5000L + r);
            Graph dag = RandomGraph.randomGraph(numNodes, 0, numEdges, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag), parameters);
            DataSet data = im.simulateData(sampleSize, false);

            // Shuffle the columns so that nothing can depend on the simulation's causal order.
            List<Node> vars = new ArrayList<>(data.getVariables());
            Collections.shuffle(vars, new Random(5000L + r));
            data = data.subsetColumns(vars);

            ICovarianceMatrix cov = new CovarianceMatrix(data);
            Graph trueCpdag = GraphTransforms.dagToCpdag(dag);

            for (Map.Entry<String, Alg> e : algs.entrySet()) {
                long t0 = System.nanoTime();
                Graph est = e.getValue().run(cov);
                nanos.merge(e.getKey(), System.nanoTime() - t0, Long::sum);
                double[] m = counts(trueCpdag, est);
                double[] s = sums.get(e.getKey());
                for (int i = 0; i < 8; i++) s[i] += m[i];
            }
        }

        System.out.printf("%n== %d nodes, %d edges, N = %d, %d runs, coefficients in +-[%.1f, 1.0]%n",
                numNodes, numEdges, sampleSize, numRuns, coefLow);
        System.out.printf("%-32s %6s %6s %6s %6s %6s %6s %8s%n", "", "AP", "AR", "AHP", "AHR", "SHD", "exact",
                "ms/run");

        for (String name : algs.keySet()) {
            double[] s = sums.get(name);
            System.out.printf("%-32s %6.3f %6.3f %6.3f %6.3f %6.2f %6.3f %8.1f%n", name,
                    s[0] / (s[0] + s[1]), s[0] / (s[0] + s[2]), s[3] / (s[3] + s[4]), s[3] / (s[3] + s[5]),
                    s[6] / numRuns, s[7] / numRuns, nanos.get(name) / 1e6 / numRuns);
        }
    }

    private static Graph pc(ICovarianceMatrix cov, Pc.ColliderOrientationStyle style, boolean refine,
                            double refinementAlpha) throws InterruptedException {
        Pc pc = new Pc(new IndTestFisherZ(cov, 0.01));
        pc.setColliderOrientationStyle(style);
        pc.setOrderRefinement(refine);
        pc.setRefinementAlpha(refinementAlpha);
        return pc.search();
    }

    private static Graph boss(ICovarianceMatrix cov, int numStarts) throws InterruptedException {
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(2.0);
        Boss boss = new Boss(score);
        boss.setNumStarts(numStarts);
        boss.setUseDataOrder(numStarts == 1);
        return new PermutationSearch(boss).search();
    }

    /**
     * Returns adjacency TP, FP, FN; arrowhead TP, FP, FN; SHD; and 1 if the graphs match exactly, else 0.
     */
    private static double[] counts(Graph trueCpdag, Graph est) {
        List<Node> nodes = trueCpdag.getNodes();
        double aTp = 0, aFp = 0, aFn = 0, hTp = 0, hFp = 0, hFn = 0, shd = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i), b = nodes.get(j);
                Node ea = est.getNode(a.getName()), eb = est.getNode(b.getName());
                Edge t = trueCpdag.getEdge(a, b), e = est.getEdge(ea, eb);

                if (t != null && e != null) aTp++;
                else if (e != null) aFp++;
                else if (t != null) aFn++;

                boolean tA = t != null && t.getProximalEndpoint(a) == Endpoint.ARROW;
                boolean tB = t != null && t.getProximalEndpoint(b) == Endpoint.ARROW;
                boolean eA = e != null && e.getProximalEndpoint(ea) == Endpoint.ARROW;
                boolean eB = e != null && e.getProximalEndpoint(eb) == Endpoint.ARROW;

                if (tA && eA) hTp++;
                else if (eA) hFp++;
                else if (tA) hFn++;

                if (tB && eB) hTp++;
                else if (eB) hFp++;
                else if (tB) hFn++;

                if ((t == null) != (e == null)) shd++;
                else if (t != null && (tA != eA || tB != eB)) shd++;
            }
        }

        return new double[]{aTp, aFp, aFn, hTp, hFp, hFn, shd, shd == 0 ? 1 : 0};
    }
}
