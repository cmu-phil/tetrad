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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fofc;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.rlcd.StratifiedChi2RankTest;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndTestStratifiedRank;
import edu.cmu.tetrad.util.StratifiedRankTests;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A watch-the-output runner for trying the stratified (domain-pooled) rank tests on a real data file. Loads a
 * tabular file, splits the rows by a named domain column (batch, site, cohort, semester), and runs PC, FOFC, and
 * RLCD each two ways -- on the concatenated data ignoring domains, and in stratified mode -- printing the graphs
 * and the elapsed times side by side, so the effect of respecting the batch structure can be seen directly.
 * <p>
 * Usage, from IntelliJ's run configuration or the command line:
 * <pre>
 *     StratifiedRealDataRunner data.csv domainColumnName [alpha] [maxDiscreteCategories]
 * </pre>
 * The file is read as mixed data, comma-delimited for a .csv extension and tab-delimited otherwise, with "*" as
 * the missing-value marker; columns with at most maxDiscreteCategories distinct integer values (default 10) are
 * read as discrete. All columns other than the domain column must be continuous. Rows with missing values are
 * removed listwise, with a count printed. RLCD is skipped above 30 variables, since its runtime grows quickly.
 *
 * @author josephramsey
 */
public final class StratifiedRealDataRunner {

    private StratifiedRealDataRunner() {
    }

    /**
     * Runs the comparison; see the class comment for the arguments.
     *
     * @param args data file, domain column name, optional alpha (default 0.01), optional maxDiscreteCategories
     *             (default 10).
     * @throws Exception on file or search errors.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: StratifiedRealDataRunner <data file> <domain column> [alpha] "
                               + "[maxDiscreteCategories]");
            return;
        }

        File file = new File(args[0]);
        String domainName = args[1];
        double alpha = args.length > 2 ? Double.parseDouble(args[2]) : 0.01;
        int maxCategories = args.length > 3 ? Integer.parseInt(args[3]) : 10;

        Delimiter delimiter = file.getName().toLowerCase().endsWith(".csv") ? Delimiter.COMMA : Delimiter.TAB;
        DataSet dataSet = SimpleDataLoader.loadMixedData(file, "//", '"', "*", true, maxCategories,
                delimiter, false);

        System.out.println("Loaded " + dataSet.getNumRows() + " rows, " + dataSet.getNumColumns()
                           + " columns from " + file.getName() + "; alpha = " + alpha + ".");

        if (dataSet.existsMissingValue()) {
            int before = dataSet.getNumRows();
            List<Integer> complete = new ArrayList<>();
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                boolean ok = true;
                for (int j = 0; j < dataSet.getNumColumns() && ok; j++) {
                    if (dataSet.getVariable(j) instanceof edu.cmu.tetrad.data.ContinuousVariable) {
                        ok = !Double.isNaN(dataSet.getDouble(i, j));
                    } else {
                        ok = dataSet.getInt(i, j) != edu.cmu.tetrad.data.DiscreteVariable.MISSING_VALUE;
                    }
                }
                if (ok) complete.add(i);
            }
            int[] rows = complete.stream().mapToInt(Integer::intValue).toArray();
            int[] cols = new int[dataSet.getNumColumns()];
            for (int j = 0; j < cols.length; j++) cols[j] = j;
            dataSet = dataSet.subsetRowsColumns(rows, cols);
            System.out.println("Removed " + (before - dataSet.getNumRows()) + " rows with missing values "
                               + "(listwise); " + dataSet.getNumRows() + " remain.");
        }

        String domainColumn = StratifiedRankTests.resolveDomainColumn(dataSet, domainName);
        Node domain = dataSet.getVariable(domainColumn);
        List<DataSet> perDomain = StratifiedRankTests.split(dataSet, domain);

        System.out.println("Domain column: " + domainColumn + "; " + perDomain.size() + " domains with rows:");
        StringBuilder sizes = new StringBuilder("  ");
        for (DataSet d : perDomain) sizes.append(d.getNumRows()).append(" ");
        System.out.println(sizes);
        System.out.println("Small domains make the per-domain covariances noisy; consider merging any tiny ones.");

        for (DataSet d : perDomain) {
            if (!d.isContinuous()) {
                System.out.println("Besides the domain column, some columns are discrete; the stratified rank "
                                   + "tests are for continuous variables plus one domain column. Stopping.");
                return;
            }
        }

        int dcol = dataSet.getVariables().indexOf(domain);
        int[] keep = new int[dataSet.getNumColumns() - 1];
        int k = 0;
        for (int j = 0; j < dataSet.getNumColumns(); j++) if (j != dcol) keep[k++] = j;
        DataSet sub = dataSet.subsetColumns(keep);

        // ---- PC ----
        System.out.println("\n================ PC ================");
        long t0 = System.currentTimeMillis();
        Graph pcNaive = new Pc(new IndTestFisherZ(sub, alpha)).search();
        long t1 = System.currentTimeMillis();
        Graph pcStratified = new Pc(new IndTestStratifiedRank(perDomain, alpha)).search();
        long t2 = System.currentTimeMillis();

        System.out.println("--- PC, Fisher Z on concatenated data (" + (t1 - t0) + " ms) ---");
        System.out.println(pcNaive);
        System.out.println("--- PC, stratified rank test (" + (t2 - t1) + " ms) ---");
        System.out.println(pcStratified);

        // ---- FOFC ----
        System.out.println("================ FOFC ================");
        t0 = System.currentTimeMillis();
        Fofc fofcNaive = new Fofc(sub, alpha, -1);
        Map<List<Integer>, Integer> naiveClusters = fofcNaive.findClusters();
        t1 = System.currentTimeMillis();
        Fofc fofcStratified = new Fofc(sub, alpha, -1);
        fofcStratified.setStratifiedRankTests(StratifiedRankTests.fromDataSets(perDomain));
        Map<List<Integer>, Integer> stratifiedClusters = fofcStratified.findClusters();
        t2 = System.currentTimeMillis();

        System.out.println("--- FOFC on concatenated data (" + (t1 - t0) + " ms) ---");
        printClusters(naiveClusters, sub);
        System.out.println("--- FOFC in stratified mode (" + (t2 - t1) + " ms) ---");
        printClusters(stratifiedClusters, sub);

        // ---- RLCD ----
        if (sub.getNumColumns() > 30) {
            System.out.println("================ RLCD skipped (" + sub.getNumColumns()
                               + " variables; runtime grows quickly past 30) ================");
            return;
        }

        System.out.println("================ RLCD ================");
        t0 = System.currentTimeMillis();
        edu.cmu.tetrad.search.Rlcd plain = new edu.cmu.tetrad.search.Rlcd(sub);
        plain.setAlpha(alpha);
        Graph rlcdNaive = plain.search();
        t1 = System.currentTimeMillis();
        edu.cmu.tetrad.search.Rlcd stratified = new edu.cmu.tetrad.search.Rlcd(sub);
        stratified.setAlpha(alpha);
        stratified.setStratifiedRankTest(new StratifiedChi2RankTest(perDomain));
        Graph rlcdStratified = stratified.search();
        t2 = System.currentTimeMillis();

        System.out.println("--- RLCD on concatenated data (" + (t1 - t0) + " ms) ---");
        System.out.println(rlcdNaive);
        System.out.println("--- RLCD in stratified mode (" + (t2 - t1) + " ms) ---");
        System.out.println(rlcdStratified);
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
