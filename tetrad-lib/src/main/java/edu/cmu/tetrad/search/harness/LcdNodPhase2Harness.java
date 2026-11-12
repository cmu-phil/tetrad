/// ////////////////////////////////////////////////////////////////////////////
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.lcdnod.LcdNodPhase2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A watch-the-output harness for Phase 2 of LCD-NOD (Dong et al., ICML 2026): simulates a nonstationary one-factor
 * model in which some latents and some indicators have mechanisms that change across domains, runs
 * {@link LcdNodPhase2} on it with the true clusters, and prints the report, the edges from the domain variable, and
 * the truth. Run the main method and read along; one run takes a few seconds, most of it the bootstrap.
 * <p>
 * The simulator is also used by the unit tests.
 *
 * @author josephramsey
 */
public final class LcdNodPhase2Harness {

    private LcdNodPhase2Harness() {
    }

    /**
     * Runs the demonstration.
     *
     * @param args ignored.
     * @throws InterruptedException if interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        Random rng = new Random(2026L);

        // Latent DAG L1 -> L2 -> L3, L1 -> L3; four indicators per latent; four domains; 1500 rows each.
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        Simulation sim = simulate(1500, 4, latentChanges, indicatorChanges, rng);

        System.out.println("Truth: changing latents "
                           + names(sim.latents, latentChanges) + "; changing indicators "
                           + names(sim.indicators(), indicatorChanges));
        System.out.println();

        for (int bootstraps : new int[]{100, 0}) {
            LcdNodPhase2 phase2 = new LcdNodPhase2(sim.dataSet, "domain", sim.blocks, sim.latents);
            phase2.setNumBootstraps(bootstraps);
            phase2.setAlpha(0.05);
            phase2.setChangeRatio(1.5);
            phase2.setSeed(7);
            long start = System.currentTimeMillis();
            Graph graph = phase2.search();
            long ms = System.currentTimeMillis() - start;

            System.out.println("==== " + (bootstraps > 0 ? "bootstrap rule, B = " + bootstraps : "ratio rule, 1.5")
                               + " (" + ms + " ms) ====");
            System.out.println(phase2.getReport());
            Node domain = graph.getNode("domain");
            System.out.println("Edges from the domain variable: " + graph.getEdges(domain));
            System.out.println("Latent structure: " + graph.getEdges(sim.latents.get(0)));
            System.out.println();
        }
    }

    private static List<String> names(List<Node> nodes, boolean[] flags) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) if (flags[i]) out.add(nodes.get(i).getName());
        return out;
    }

    /**
     * A simulated data set with its true clusters.
     */
    public static final class Simulation {
        /**
         * The data set: indicators X1..Xp (continuous) and a discrete column "domain".
         */
        public DataSet dataSet;
        /**
         * The blocks, as column indices into the data set.
         */
        public List<List<Integer>> blocks;
        /**
         * The latent nodes, one per block.
         */
        public List<Node> latents;

        /**
         * Constructs an empty simulation record.
         */
        public Simulation() {
        }

        /**
         * The indicator nodes in block order.
         *
         * @return the indicators.
         */
        public List<Node> indicators() {
            List<Node> out = new ArrayList<>();
            for (List<Integer> block : blocks) for (int c : block) out.add(dataSet.getVariable(c));
            return out;
        }
    }

    /**
     * Simulates a nonstationary one-factor model over three latents with the DAG L1 -> L2 -> L3, L1 -> L3 and the
     * same number of indicators per latent (the length of the indicator flags divided by three; FOFC needs at least
     * four to seed a cluster). A changing latent has its structural coefficients and its noise variance redrawn in
     * every domain; a changing indicator has its loading and residual variance redrawn. Everything gets a domain
     * intercept, changing or not, so that mean shifts are present throughout.
     *
     * @param nPerDomain       rows per domain.
     * @param numDomains       the number of domains.
     * @param latentChanges    per latent (3), whether its mechanism changes.
     * @param indicatorChanges per indicator (a multiple of 3, in block order), whether its measurement changes.
     * @param rng              the random source.
     * @return the simulation.
     */
    public static Simulation simulate(int nPerDomain, int numDomains, boolean[] latentChanges,
                                      boolean[] indicatorChanges, Random rng) {
        int k = 3;
        if (latentChanges.length != k || indicatorChanges.length % k != 0 || indicatorChanges.length < 2 * k) {
            throw new IllegalArgumentException("Expecting 3 latent flags and a multiple of 3 (at least 6) indicator "
                                               + "flags.");
        }
        int perBlock = indicatorChanges.length / k;
        int p = k * perBlock;

        // Base (invariant) parameters.
        double[][] beta = new double[k][k]; // beta[j][i]: coefficient of L_j in L_i
        beta[0][1] = 0.8;
        beta[1][2] = 0.7;
        beta[0][2] = 0.5;
        double[] noise = {1.0, 0.8, 0.6};
        double[] loading = new double[p];
        double[] unique = new double[p];
        for (int a = 0; a < p; a++) {
            loading[a] = 0.7 + 0.6 * rng.nextDouble();
            unique[a] = 0.3 + 0.5 * rng.nextDouble();
        }

        // Variance multipliers for the changing mechanisms: a random permutation of values evenly spread over
        // [0.5, 2] across the domains, so that the paper's Assumption A2 holds (a changing latent's noise variance
        // and a changing indicator's residual variance really do differ across domains). Coefficient and loading
        // changes are drawn freely. Without this, a changing latent can by chance draw nearly equal noise variances
        // in every domain and change only through its coefficients, which Theorem 8's conditional-variance test
        // cannot see.
        double[][] latentNoiseMult = new double[k][];
        double[][] uniqueMult = new double[p][];
        for (int i = 0; i < k; i++) latentNoiseMult[i] = spreadMultipliers(numDomains, rng);
        for (int a = 0; a < p; a++) uniqueMult[a] = spreadMultipliers(numDomains, rng);

        int n = nPerDomain * numDomains;
        double[][] data = new double[n][p + 1];
        int row = 0;

        for (int t = 0; t < numDomains; t++) {
            double[][] betaT = new double[k][k];
            double[] noiseT = noise.clone();
            double[] loadingT = loading.clone();
            double[] uniqueT = unique.clone();
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < k; j++) betaT[j][i] = beta[j][i];
                if (latentChanges[i]) {
                    for (int j = 0; j < k; j++) {
                        if (beta[j][i] != 0) betaT[j][i] = beta[j][i] * (0.4 + 1.2 * rng.nextDouble());
                    }
                    noiseT[i] = noise[i] * latentNoiseMult[i][t];
                }
            }
            for (int a = 0; a < p; a++) {
                if (indicatorChanges[a]) {
                    loadingT[a] = loading[a] * (0.5 + 1.0 * rng.nextDouble());
                    uniqueT[a] = unique[a] * uniqueMult[a][t];
                }
            }
            double[] latentMean = new double[k];
            double[] indicatorMean = new double[p];
            for (int i = 0; i < k; i++) latentMean[i] = 2 * rng.nextGaussian();
            for (int a = 0; a < p; a++) indicatorMean[a] = 2 * rng.nextGaussian();

            for (int r = 0; r < nPerDomain; r++) {
                double[] L = new double[k];
                for (int i = 0; i < k; i++) { // topological order 0, 1, 2
                    double v = latentMean[i] + Math.sqrt(noiseT[i]) * rng.nextGaussian();
                    for (int j = 0; j < i; j++) v += betaT[j][i] * L[j];
                    L[i] = v;
                }
                for (int a = 0; a < p; a++) {
                    data[row][a] = indicatorMean[a] + loadingT[a] * L[a / perBlock]
                                   + Math.sqrt(uniqueT[a]) * rng.nextGaussian();
                }
                data[row][p] = t;
                row++;
            }
        }

        List<Node> vars = new ArrayList<>();
        for (int a = 0; a < p; a++) vars.add(new ContinuousVariable("X" + (a + 1)));
        List<String> cats = new ArrayList<>();
        for (int t = 0; t < numDomains; t++) cats.add("d" + (t + 1));
        vars.add(new DiscreteVariable("domain", cats));
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);

        Simulation sim = new Simulation();
        sim.dataSet = dataSet;
        sim.blocks = new ArrayList<>();
        sim.latents = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            List<Integer> block = new ArrayList<>();
            for (int j = 0; j < perBlock; j++) block.add(i * perBlock + j);
            sim.blocks.add(block);
            Node latent = new GraphNode("L" + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            sim.latents.add(latent);
        }
        return sim;
    }

    /**
     * Multipliers evenly spread over [0.5, 2] (so the largest is four times the smallest), in random order.
     */
    private static double[] spreadMultipliers(int numDomains, Random rng) {
        double[] m = new double[numDomains];
        for (int t = 0; t < numDomains; t++) {
            m[t] = numDomains == 1 ? 1.0 : 0.5 + 1.5 * t / (numDomains - 1.0);
        }
        for (int t = numDomains - 1; t > 0; t--) {
            int j = rng.nextInt(t + 1);
            double tmp = m[t];
            m[t] = m[j];
            m[j] = tmp;
        }
        return m;
    }

    /**
     * Convenience: the string form of a flag array for printing.
     *
     * @param flags the flags.
     * @return the string.
     */
    public static String flags(boolean[] flags) {
        return Arrays.toString(flags);
    }
}
