///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fofc;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndTestStratifiedRank;
import edu.cmu.tetrad.util.StratifiedRankTests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A watch-the-output harness for the stratified (domain-pooled) rank test of Dong et al. (ICML 2026), Phase 1 of
 * LCD-NOD. Two demonstrations, each on nonstationary data -- a fixed graph whose coefficients, intercepts, and
 * noise scales are redrawn in each domain:
 * <p>
 * (1) PC on a five-variable DAG, run three ways: Fisher Z on the concatenated data (wrong: the domain shifts
 * induce spurious dependence and mask real dependence), Fisher Z on one domain alone (right but wasteful), and the
 * stratified rank test pooling all domains (right and using all the data).
 * <p>
 * (2) FOFC on a two-latent one-factor model with four indicators per latent, run on the concatenated data and then
 * in stratified mode, printing the clusters found.
 * <p>
 * Run the main method and read along; one run takes a few seconds.
 *
 * @author josephramsey
 */
public final class StratifiedRankHarness {

    private StratifiedRankHarness() {
    }

    /**
     * Runs the demonstrations.
     *
     * @param args ignored.
     * @throws InterruptedException if a search is interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        Random rng = new Random(1704L);

        pcDemo(rng);
        fofcDemo(rng);
    }

    // ================================ PC demo ================================

    /**
     * True DAG: X1 -> X2 -> X3 -> X4, X2 -> X5, X1 -> X3. Coefficients, intercepts, and noise scales redrawn per
     * domain; the coefficient on X2 -> X3 alternates in sign across domains, so concatenation masks it.
     */
    private static void pcDemo(Random rng) throws InterruptedException {
        System.out.println("=============================================================");
        System.out.println("PC on nonstationary data: 6 domains x 500 rows, 5 variables");
        System.out.println("True DAG: X1->X2, X2->X3, X3->X4, X2->X5, X1->X3");
        System.out.println("Per-domain coefficients and intercepts; X2->X3 flips sign.");
        System.out.println("=============================================================");

        int domains = 6;
        int nPerDomain = 500;
        double alpha = 0.01;

        List<DataSet> perDomain = new ArrayList<>();
        for (int t = 0; t < domains; t++) {
            perDomain.add(simulatePcDomain(nPerDomain, t, rng));
        }
        DataSet concatenated = concatenate(perDomain);

        Graph gNaive = new Pc(new IndTestFisherZ(concatenated, alpha)).search();
        Graph gOneDomain = new Pc(new IndTestFisherZ(perDomain.get(0), alpha)).search();
        Graph gStratified = new Pc(new IndTestStratifiedRank(perDomain, alpha)).search();

        System.out.println("\n--- PC, Fisher Z on concatenated data (ignores domains) ---");
        System.out.println(gNaive);
        System.out.println("--- PC, Fisher Z on domain 0 alone (n = " + nPerDomain + ") ---");
        System.out.println(gOneDomain);
        System.out.println("--- PC, stratified rank test pooled over all domains ---");
        System.out.println(gStratified);
        System.out.println("True adjacencies: X1--X2, X2--X3, X3--X4, X2--X5, X1--X3.\n");
    }

    private static DataSet simulatePcDomain(int n, int t, Random rng) {
        double b12 = draw(rng), b23 = (t % 2 == 0 ? 1 : -1) * 0.6, b34 = draw(rng);
        double b25 = draw(rng), b13 = draw(rng);
        double[] m = new double[5];
        for (int j = 0; j < 5; j++) m[j] = 3.0 * rng.nextGaussian();

        double[][] data = new double[n][5];
        for (int i = 0; i < n; i++) {
            double x1 = m[0] + rng.nextGaussian();
            double x2 = m[1] + b12 * x1 + rng.nextGaussian();
            double x3 = m[2] + b23 * x2 + b13 * x1 + rng.nextGaussian();
            double x4 = m[3] + b34 * x3 + rng.nextGaussian();
            double x5 = m[4] + b25 * x2 + rng.nextGaussian();
            data[i][0] = x1;
            data[i][1] = x2;
            data[i][2] = x3;
            data[i][3] = x4;
            data[i][4] = x5;
        }

        List<Node> vars = new ArrayList<>();
        for (int j = 1; j <= 5; j++) vars.add(new ContinuousVariable("X" + j));
        return new BoxDataSet(new DoubleDataBox(data), vars);
    }

    /**
     * A coefficient in [0.4, 1.0] with random sign.
     */
    private static double draw(Random rng) {
        return (rng.nextBoolean() ? 1 : -1) * (0.4 + 0.6 * rng.nextDouble());
    }

    // =============================== FOFC demo ===============================

    /**
     * True one-factor model: latents L1 -> L2; L1 has indicators X1..X4, L2 has indicators X5..X8. Loadings and
     * indicator intercepts redrawn per domain.
     */
    private static void fofcDemo(Random rng) {
        System.out.println("=============================================================");
        System.out.println("FOFC on nonstationary one-factor data: 6 domains x 500 rows");
        System.out.println("True clusters: {X1..X4} <- L1 -> L2 -> {X5..X8}");
        System.out.println("Per-domain loadings and indicator intercepts.");
        System.out.println("=============================================================");

        int domains = 6;
        int nPerDomain = 500;
        double alpha = 0.001;

        List<DataSet> perDomain = new ArrayList<>();
        for (int t = 0; t < domains; t++) {
            perDomain.add(simulateFofcDomain(nPerDomain, rng));
        }
        DataSet concatenated = concatenate(perDomain);

        Fofc fofcNaive = new Fofc(concatenated, alpha, -1);
        Map<List<Integer>, Integer> naiveClusters = fofcNaive.findClusters();

        Fofc fofcStratified = new Fofc(concatenated, alpha, -1);
        fofcStratified.setStratifiedRankTests(StratifiedRankTests.fromDataSets(perDomain));
        Map<List<Integer>, Integer> stratifiedClusters = fofcStratified.findClusters();

        System.out.println("\n--- FOFC on concatenated data (ignores domains) ---");
        printClusters(naiveClusters, concatenated);
        System.out.println("--- FOFC in stratified mode, pooled over all domains ---");
        printClusters(stratifiedClusters, concatenated);
        System.out.println("True clusters: {X1, X2, X3, X4} and {X5, X6, X7, X8}.");
    }

    private static DataSet simulateFofcDomain(int n, Random rng) {
        double[] loading = new double[8];
        double[] shift = new double[8];
        for (int j = 0; j < 8; j++) {
            loading[j] = 0.6 + 0.7 * rng.nextDouble();
            shift[j] = 3.0 * rng.nextGaussian();
        }
        double b = draw(rng); // L1 -> L2, redrawn per domain

        double[][] data = new double[n][8];
        for (int i = 0; i < n; i++) {
            double l1 = rng.nextGaussian();
            double l2 = b * l1 + rng.nextGaussian();
            for (int j = 0; j < 4; j++) data[i][j] = shift[j] + loading[j] * l1 + 0.7 * rng.nextGaussian();
            for (int j = 4; j < 8; j++) data[i][j] = shift[j] + loading[j] * l2 + 0.7 * rng.nextGaussian();
        }

        List<Node> vars = new ArrayList<>();
        for (int j = 1; j <= 8; j++) vars.add(new ContinuousVariable("X" + j));
        return new BoxDataSet(new DoubleDataBox(data), vars);
    }

    // ================================ helpers ================================

    private static DataSet concatenate(List<DataSet> parts) {
        int n = 0;
        for (DataSet p : parts) n += p.getNumRows();
        int d = parts.get(0).getNumColumns();

        double[][] all = new double[n][d];
        int row = 0;
        for (DataSet p : parts) {
            for (int i = 0; i < p.getNumRows(); i++) {
                for (int j = 0; j < d; j++) all[row][j] = p.getDouble(i, j);
                row++;
            }
        }
        return new BoxDataSet(new DoubleDataBox(all), new ArrayList<>(parts.get(0).getVariables()));
    }

    private static void printClusters(Map<List<Integer>, Integer> clusters, DataSet dataSet) {
        if (clusters.isEmpty()) {
            System.out.println("(no clusters found)");
        }
        for (Map.Entry<List<Integer>, Integer> e : clusters.entrySet()) {
            StringBuilder sb = new StringBuilder("{");
            List<Integer> cluster = e.getKey();
            for (int i = 0; i < cluster.size(); i++) {
                sb.append(dataSet.getVariables().get(cluster.get(i)).getName());
                if (i < cluster.size() - 1) sb.append(", ");
            }
            sb.append("}  rank ").append(e.getValue());
            System.out.println(sb);
        }
        System.out.println();
    }
}
