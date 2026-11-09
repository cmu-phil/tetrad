///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadPrecision;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadRecall;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.FaskPool;
import edu.cmu.tetrad.search.FaskVote;
import edu.cmu.tetrad.search.PooledAdjacencySearch;
import edu.cmu.tetrad.search.test.IndTestFisherZFisherPValue;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness comparing the multi-subject cyclic methods in Tetrad on a common cyclic
 * simulation: CCD and CPW, each run with the multi-dataset Fisher-pooled Fisher Z test,
 * and FASK-Vote and FASK-Pool, each run with all five adjacency settings of
 * {@link PooledAdjacencySearch.Method}.
 *
 * <p>Simulation: a random 10-node, 10-edge directed graph containing at least one
 * cycle; D datasets sharing the edge support, with coefficients re-drawn per dataset
 * (uniform magnitude in [COEF_LOW, COEF_HIGH], random sign, the whole matrix rescaled
 * to spectral radius TARGET_RADIUS when needed for stability); errors drawn i.i.d.
 * from Beta(2, 5), centered at their mean 2/7 and scaled by a per-variable,
 * per-dataset standard deviation drawn uniformly from [0.7, 1.2]; X = (I - B)^{-1} E
 * (the reduced form). Columns are in a fixed order -- no column-order randomization.
 * Two-cycle detection is off everywhere: FASK-Vote disables it internally, FASK-Pool
 * has none, and CCD and CPW have no such option.</p>
 *
 * <p>A note on CPW: CPW is a single-dataset algorithm whose CI test is pluggable. Here
 * its CI test is the same pooled Fisher Z test the other methods use (built over the
 * per-dataset standardized datasets), while its pairwise left-right phase, which needs
 * raw columns, runs on the row-concatenation of the per-dataset standardized datasets.
 * Concatenation is exactly what the pooled methods avoid, so if CPW underperforms
 * here, part of that may be the concatenated pairwise phase rather than CPW itself;
 * a pooled pairwise phase for CPW would be the fair follow-up.</p>
 *
 * <p>Metrics: adjacency precision/recall and arrowhead precision/recall against the
 * true cyclic graph, averaged over replicates (NaNs, e.g., precision when nothing is
 * oriented, are skipped and counted). Means and standard deviations are reported.</p>
 *
 * <p>Usage: run main(); optionally pass reps, numDatasets, sampleSize as the first
 * three arguments.</p>
 *
 * @author josephramsey
 */
public final class MultiSubjectCyclicComparison {

    private static final int NUM_NODES = 10;
    private static final int NUM_EDGES = 10;
    private static final int MAX_DEGREE = 6;
    private static final double COEF_LOW = 0.3;
    private static final double COEF_HIGH = 0.8;
    private static final double TARGET_RADIUS = 0.8;
    private static final double ALPHA = 0.01;
    private static final double LING_THRESHOLD = 0.1;
    private static final double PENALTY_DISCOUNT = 2.0;
    private static final long SEED = 42L;

    private static final DecimalFormat DF = new DecimalFormat("0.000");

    private MultiSubjectCyclicComparison() {
    }

    /**
     * Runs the comparison.
     *
     * @param args optionally: reps (default 10), numDatasets (default 10), sampleSize
     *             (default 500)
     * @throws InterruptedException if a search is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        int reps = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int numDatasets = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int sampleSize = args.length > 2 ? Integer.parseInt(args[2]) : 500;

        RandomGenerator rng = new Well44497b(SEED);

        List<String> methods = new ArrayList<>();
        methods.add("CCD");
        methods.add("CPW");
        for (PooledAdjacencySearch.Method m : PooledAdjacencySearch.Method.values()) {
            methods.add("FASK-Vote/" + m);
        }
        for (PooledAdjacencySearch.Method m : PooledAdjacencySearch.Method.values()) {
            methods.add("FASK-Pool/" + m);
        }

        // method -> list of {adjP, adjR, ahP, ahR}
        Map<String, List<double[]>> results = new LinkedHashMap<>();
        for (String m : methods) results.put(m, new ArrayList<>());

        long t0 = System.currentTimeMillis();

        for (int rep = 0; rep < reps; rep++) {
            Graph trueGraph = randomCyclicGraph(rng);
            List<DataSet> dataSets = simulate(trueGraph, numDatasets, sampleSize, rng);
            List<DataSet> standardized = new ArrayList<>();
            for (DataSet d : dataSets) standardized.add(DataTransforms.standardizeData(d));

            // ---- CCD with the pooled Fisher Z test ----
            {
                IndependenceTest test = new IndTestFisherZFisherPValue(standardized, ALPHA);
                Ccd ccd = new Ccd(test);
                ccd.setDepth(-1);
                record(results, "CCD", trueGraph, ccd.search());
            }

            // ---- CPW with the pooled Fisher Z test ----
            {
                List<DataSet> finalStandardized = standardized;
                IndependenceWrapper pooledTest = new IndependenceWrapper() {
                    @Override
                    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
                        return new IndTestFisherZFisherPValue(finalStandardized, ALPHA);
                    }

                    @Override
                    public String getDescription() {
                        return "Fisher-pooled Fisher Z over " + finalStandardized.size() + " datasets";
                    }

                    @Override
                    public DataType getDataType() {
                        return DataType.Continuous;
                    }

                    @Override
                    public List<String> getParameters() {
                        return new ArrayList<>();
                    }
                };

                edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Cpw cpw =
                        new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Cpw(pooledTest);
                Parameters params = new Parameters();
                params.set(Params.ALPHA, ALPHA);
                // The pairwise phase runs on the concatenated standardized data; see the
                // class Javadoc for the caveat.
                record(results, "CPW", trueGraph, cpw.runSearch(concatenate(standardized), params));
            }

            // ---- FASK-Vote and FASK-Pool, all five adjacency settings ----
            for (PooledAdjacencySearch.Method m : PooledAdjacencySearch.Method.values()) {
                Parameters params = new Parameters();
                params.set(Params.PENALTY_DISCOUNT, PENALTY_DISCOUNT);

                FaskVote vote = new FaskVote(dataSets, new SemBicScore());
                vote.setAdjacencyMethod(m);
                vote.setFasAlpha(ALPHA);
                vote.setLingThreshold(LING_THRESHOLD);
                record(results, "FASK-Vote/" + m, trueGraph, vote.search(params));

                FaskPool pool = new FaskPool(dataSets, new SemBicScore());
                pool.setAdjacencyMethod(m);
                pool.setFasAlpha(ALPHA);
                pool.setLingThreshold(LING_THRESHOLD);
                record(results, "FASK-Pool/" + m, trueGraph, pool.search(params));
            }

            System.out.println("rep " + (rep + 1) + "/" + reps + " done ("
                               + (System.currentTimeMillis() - t0) / 1000 + "s)");
        }

        // ---- Report ----
        System.out.println();
        System.out.println("p=" + NUM_NODES + " edges=" + NUM_EDGES + " (cyclic) D=" + numDatasets
                           + " n=" + sampleSize + " reps=" + reps + " alpha=" + ALPHA
                           + " Beta(2,5) errors, coefficients redrawn per dataset, no column randomization");
        System.out.println();
        System.out.printf("%-28s %14s %14s %14s %14s %6s%n",
                "method", "adjPrec", "adjRec", "ahPrec", "ahRec", "used");
        for (String m : methods) {
            List<double[]> rows = results.get(m);
            StringBuilder sb = new StringBuilder(String.format("%-28s", m));
            int minUsed = Integer.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                double sum = 0.0, sumSq = 0.0;
                int used = 0;
                for (double[] row : rows) {
                    if (!Double.isNaN(row[k])) {
                        sum += row[k];
                        sumSq += row[k] * row[k];
                        used++;
                    }
                }
                if (used == 0) {
                    sb.append(String.format(" %14s", "--"));
                    minUsed = 0;
                } else {
                    double mean = sum / used;
                    double sd = used > 1 ? Math.sqrt(Math.max(0, (sumSq - used * mean * mean) / (used - 1))) : 0.0;
                    sb.append(String.format(" %6s±%-7s", DF.format(mean), DF.format(sd)));
                    minUsed = Math.min(minUsed, used);
                }
            }
            sb.append(String.format(" %6d", minUsed == Integer.MAX_VALUE ? 0 : minUsed));
            System.out.println(sb);
        }
        System.out.println();
        System.out.println("total time: " + (System.currentTimeMillis() - t0) / 1000 + "s");
        System.out.println("('used' = fewest non-NaN replicates across the four statistics for that method)");
    }

    private static void record(Map<String, List<double[]>> results, String method,
                               Graph trueGraph, Graph est) {
        est = GraphUtils.replaceNodes(est, trueGraph.getNodes());

        double adjP = new AdjacencyPrecision().getValue(trueGraph, est);
        double adjR = new AdjacencyRecall().getValue(trueGraph, est);
        double ahP = new ArrowheadPrecision().getValue(trueGraph, est);
        double ahR = new ArrowheadRecall().getValue(trueGraph, est);
        results.get(method).add(new double[]{adjP, adjR, ahP, ahR});
    }

    /**
     * Random directed graph with NUM_EDGES edges guaranteed to contain a directed
     * cycle (resampled until it does).
     */
    private static Graph randomCyclicGraph(RandomGenerator rng) {
        for (int tries = 0; tries < 1000; tries++) {
            Graph g = RandomGraph.randomCyclicGraph2(NUM_NODES, NUM_EDGES, MAX_DEGREE);
            if (g.paths().existsDirectedCycle() && g.getNumEdges() == NUM_EDGES) {
                return g;
            }
        }
        throw new IllegalStateException("Could not generate a cyclic graph.");
    }

    /**
     * Simulates numDatasets datasets from the linear cyclic SEM X = B X + e with
     * support given by the graph; coefficients redrawn per dataset; Beta(2, 5) errors,
     * centered; reduced-form solve. Columns are in the graph's node order (no
     * randomization).
     */
    private static List<DataSet> simulate(Graph graph, int numDatasets, int n,
                                          RandomGenerator rng) {
        List<Node> nodes = graph.getNodes();
        int p = nodes.size();

        List<Node> vars = new ArrayList<>();
        for (Node node : nodes) vars.add(new ContinuousVariable(node.getName()));

        BetaDistribution beta = new BetaDistribution(rng, 2, 5);
        double betaMean = 2.0 / 7.0;

        List<DataSet> out = new ArrayList<>();

        for (int d = 0; d < numDatasets; d++) {
            // B[j][i] is the coefficient of X_i in the equation for X_j (edge i -> j).
            double[][] B = new double[p][p];
            for (edu.cmu.tetrad.graph.Edge e : graph.getEdges()) {
                int i = nodes.indexOf(edu.cmu.tetrad.graph.Edges.getDirectedEdgeTail(e));
                int j = nodes.indexOf(edu.cmu.tetrad.graph.Edges.getDirectedEdgeHead(e));
                double mag = COEF_LOW + (COEF_HIGH - COEF_LOW) * rng.nextDouble();
                B[j][i] = rng.nextBoolean() ? mag : -mag;
            }

            // Rescale to spectral radius TARGET_RADIUS if needed (stability).
            RealMatrix Bm = new Array2DRowRealMatrix(B);
            EigenDecomposition eig = new EigenDecomposition(Bm);
            double radius = 0.0;
            for (int k = 0; k < p; k++) {
                double re = eig.getRealEigenvalue(k);
                double im = eig.getImagEigenvalue(k);
                radius = Math.max(radius, Math.hypot(re, im));
            }
            if (radius >= TARGET_RADIUS) {
                Bm = Bm.scalarMultiply(TARGET_RADIUS / radius);
            }

            // Errors: centered Beta(2, 5), scaled per variable.
            double[][] E = new double[n][p];
            for (int j = 0; j < p; j++) {
                double sd = 0.7 + 0.5 * rng.nextDouble();
                for (int i = 0; i < n; i++) {
                    E[i][j] = (beta.sample() - betaMean) * sd;
                }
            }

            // X^T = (I - B)^{-1} E^T, i.e., solve (I - B) X^T = E^T.
            RealMatrix IminusB = org.apache.commons.math3.linear.MatrixUtils
                    .createRealIdentityMatrix(p).subtract(Bm);
            RealMatrix Xt = new LUDecomposition(IminusB)
                    .getSolver().solve(new Array2DRowRealMatrix(E).transpose());

            double[][] cols = Xt.getData(); // p x n
            out.add(new BoxDataSet(new VerticalDoubleDataBox(cols), vars));
        }

        return out;
    }

    /**
     * Row-concatenation of the given datasets (which must share variables).
     */
    private static DataSet concatenate(List<DataSet> dataSets) {
        List<Node> vars = dataSets.get(0).getVariables();
        int p = vars.size();
        int total = 0;
        for (DataSet d : dataSets) total += d.getNumRows();

        double[][] cols = new double[p][total];
        int offset = 0;
        for (DataSet d : dataSets) {
            for (int j = 0; j < p; j++) {
                int cj = d.getColumnIndex(vars.get(j).getName());
                for (int i = 0; i < d.getNumRows(); i++) {
                    cols[j][offset + i] = d.getDouble(i, cj);
                }
            }
            offset += d.getNumRows();
        }

        return new BoxDataSet(new VerticalDoubleDataBox(cols), vars);
    }
}
