///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.CStaS;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.abs;

/**
 * Tests CStaS.
 *
 * @author Joseph Ramsey
 */
public class TestCStaS {

//    public void testIda() {
//        Parameters parameters = new Parameters();
//        parameters.set("penaltyDiscount", 2);
//        parameters.set("numSubsamples", 30);
//        parameters.set("percentSubsampleSize", 0.5);
//        parameters.set("maxQ", 100);
//        parameters.set("targetName", "X50");
//        parameters.set("alpha", 0.001);
//
//        Graph graph = GraphUtils.randomGraph(10, 0, 10,
//                100, 100, 100, false);
//
//        System.out.println(graph);
//
//        SemPm pm = new SemPm(graph);
//        SemIm im = new SemIm(pm);
//        DataSet dataSet = im.simulateData(1000, false);
//
//        Node y = dataSet.getCause("X10");
//
//        SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(dataSet));
//        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
//        IndependenceTest test = new IndTestScore(score);
//
//        PcAll pc = new PcAll(test, null);
//        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
//        pc.setConflictRule(PcAll.ConflictRule.PRIORITY);
//        pc.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
//        Graph pattern = pc.search();
//
//        Ida ida = new Ida(dataSet, pattern);
//
//        Ida.NodeEffects effects = ida.getSortedMinEffects(y);
//
//        for (int i = 0; i < effects.getNodes().size(); i++) {
//            Node x = effects.getNodes().get(i);
//            System.out.println(x + "\t" + effects.getEffects().get(i));
//        }
//    }

    //    @Test
    public void testCStaS() {
        int numNodes = 400;
        int avgDegree = 4;
        int sampleSize = 50;
        int numIterations = 10;
        int numSubsamples = 100;
        double penaltyDiscount = 1.2;
        double selectionAlpha = 0.2;
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", penaltyDiscount);
        parameters.set("numSubsamples", numSubsamples);
        parameters.set("depth", 2);
        parameters.set("selectionAlpha", selectionAlpha);

        parameters.set("numMeasures", numNodes);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("verbose", false);

        parameters.set("coefLow", 0.5);
        parameters.set("coefHigh", 1.2);
        parameters.set("includeNegativeCoefs", true);
        parameters.set("sampleSize", sampleSize);
        parameters.set("intervalBetweenShocks", 5);
        parameters.set("intervalBetweenRecordings", 5);

        parameters.set("sampleSize", sampleSize);

        parameters.set("parallelism", 40);

        List<int[]> cstasRet = new ArrayList<>();

        RandomGraph randomForward = new RandomForward();
        LinearFisherModel fisher = new LinearFisherModel(randomForward);
        fisher.createData(parameters);
        DataSet fullData = (DataSet) fisher.getDataModel(0);

        final Graph trueDag = fisher.getTrueGraph(0);

        List<Node> nodes = trueDag.getNodes();

        Map<Node, Integer> numAncestors = new HashMap<>();

        for (Node n : nodes) {
            numAncestors.put(n, trueDag.getAncestors(Collections.singletonList(n)).size());
        }

        nodes.sort((o1, o2) -> Integer.compare(numAncestors.get(o2), numAncestors.get(o1)));

        for (int i = 0; i < numIterations; i++) {
            parameters.set("targetName", nodes.get(i).getName());

            CStaS cstas = new CStaS();
            cstas.setIndependenceWrapper(new SemBicTest());
            cstas.setTrueDag(trueDag);

            Graph graph = cstas.search(fullData, parameters);

            int[] ret = getResult(trueDag, graph);
            cstasRet.add(ret);
        }

        System.out.println();

        System.out.println("\tTreks\tAncestors\tNon-Treks\tNon-Ancestors");

        for (int i = 0; i < numIterations; i++) {
            System.out.println((i + 1) + ".\t"
                    + cstasRet.get(i)[0] + "\t"
                    + cstasRet.get(i)[1] + "\t"
                    + cstasRet.get(i)[2] + "\t"
                    + cstasRet.get(i)[3]
            );
        }
    }

    @Test
    public void testCStaSMulti() {
        int numTargets = 10;

        int numNodes = 500;
        int avgDegree = 4;
        int sampleSize = 200;
        int numIterations = 10;
        int numSubsamples = 50;
        double penaltyDiscount = 1;
        double selectionAlpha = 0.1;

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", penaltyDiscount);
        parameters.set("numSubsamples", numSubsamples);
        parameters.set("depth", 2);
        parameters.set("selectionAlpha", selectionAlpha);

        parameters.set("numMeasures", numNodes);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("verbose", false);

        parameters.set("coefLow", 0.5);
        parameters.set("coefHigh", 1.0);
        parameters.set("includeNegativeCoefs", true);
        parameters.set("sampleSize", sampleSize);
        parameters.set("intervalBetweenShocks", 5);
        parameters.set("intervalBetweenRecordings", 5);

        parameters.set("sampleSize", sampleSize);

        parameters.set("parallelism", 40);

        for (int i = 0; i < numIterations; i++) {
            RandomGraph randomForward = new RandomForward();
            LinearFisherModel fisher = new LinearFisherModel(randomForward);
            fisher.createData(parameters);
            DataSet dataSet = (DataSet) fisher.getDataModel(0);

            final Graph trueDag = fisher.getTrueGraph(0);

            List<Node> nodes = trueDag.getNodes();

            Map<Node, Integer> numAncestors = new HashMap<>();

            for (Node n : nodes) {
                numAncestors.put(n, trueDag.getAncestors(Collections.singletonList(n)).size());
            }

            nodes.sort((o1, o2) -> Integer.compare(numAncestors.get(o2), numAncestors.get(o1)));

            CStaSMulti cstas = new CStaSMulti();
            cstas.setTrueDag(trueDag);
            cstas.setNumSubsamples(numSubsamples);

            List<Node> targets = new ArrayList<>();

            for (int t = 0; t < numTargets; t++) {
                targets.add(nodes.get(t));
            }

            List<Node> selectionVars = new ArrayList<>();
            targets = GraphUtils.replaceNodes(targets, dataSet.getVariables());

            for (Node target : targets) {
                List<Node> _selectionVars = edu.cmu.tetrad.search.CStaS.selectVariables(dataSet, target, selectionAlpha, 40);
                _selectionVars.removeAll(targets);

                if (_selectionVars.size() > selectionVars.size()) {
                    selectionVars = _selectionVars;
                }
            }

            selectionVars = GraphUtils.replaceNodes(selectionVars, dataSet.getVariables());
            List<Node> augmented = new ArrayList<>(selectionVars);

            for (Node target : targets) {
                final Node variable = dataSet.getVariable(target.getName());
                if (!augmented.contains(variable)) augmented.add(variable);
            }

            augmented = GraphUtils.replaceNodes(augmented, dataSet.getVariables());
            DataSet augmentedData = dataSet.subsetColumns(augmented);

            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(augmentedData));
            score.setPenaltyDiscount(penaltyDiscount);
            IndependenceTest test = new IndTestScore(score);

            List<CStaSMulti.Record> records = cstas.getRecords(augmentedData, selectionVars, targets, test).getLast();

            System.out.println(cstas.makeTable(records));


//            int[] ret = getResult(trueDag, graph);
//            cstasRet.add(ret);
        }

//        System.out.println();
//
//        System.out.println("\tTreks\tAncestors\tNon-Treks\tNon-Ancestors");
//
//        for (int i = 0; i < numIterations; i++) {
//            System.out.println((i + 1) + ".\t"
//                    + cstasRet.get(i)[0] + "\t"
//                    + cstasRet.get(i)[1] + "\t"
//                    + cstasRet.get(i)[2] + "\t"
//                    + cstasRet.get(i)[3]
//            );
//        }
    }

    //    @Test
//    public void testFgesMbAncestors() {
//        int numNodes = 500;
//        int avgDegree = 7;
//        int sampleSize = 100;
//        int numIterations = 10;
//        int numSubsamples = 100;
//        double penaltyDiscount = 1.3;
//        double selectionAlpha = 0.05;
//        Parameters parameters = new Parameters();
//
//        parameters.set("penaltyDiscount", penaltyDiscount);
//        parameters.set("numSubsamples", numSubsamples);
//        parameters.set("depth", 2);
//        parameters.set("selectionAlpha", selectionAlpha);
//
//        parameters.set("numMeasures", numNodes);
//        parameters.set("numLatents", 0);
//        parameters.set("avgDegree", avgDegree);
//        parameters.set("maxDegree", 100);
//        parameters.set("maxIndegree", 100);
//        parameters.set("maxOutdegree", 100);
//        parameters.set("connected", false);
//
//        parameters.set("verbose", false);
//
//        parameters.set("coefLow", 0.5);
//        parameters.set("coefHigh", 1.2);
//        parameters.set("includeNegativeCoefs", true);
//        parameters.set("sampleSize", sampleSize);
//        parameters.set("intervalBetweenShocks", 5);
//        parameters.set("intervalBetweenRecordings", 5);
//
//        parameters.set("sampleSize", sampleSize);
//
//        parameters.set("parallelism", 40);
//
//        RandomGraph randomForward = new RandomForward();
//        LinearFisherModel fisher = new LinearFisherModel(randomForward);
//        fisher.createData(parameters);
//        DataSet fullData = (DataSet) fisher.getDataModel(0);
//
//        final Graph trueDag = fisher.getTrueGraph(0);
//
//        List<Node> nodes = trueDag.getNodes();
//
//        Map<Node, Integer> numAncestors = new HashMap<>();
//
//        for (Node n : nodes) {
//            numAncestors.put(n, trueDag.getAncestors(Collections.singletonList(n)).size());
//        }
//
//        nodes.sort((o1, o2) -> Integer.compare(numAncestors.get(o2), numAncestors.get(o1)));
//
//        int[][] counts = new int[numIterations][4];
//        double[][] times = new double[numIterations][2];
//        int[][] fp = new int[numIterations][2];
//        double[][] cev = new double[numIterations][2];
//
//        for (int i = 0; i < numIterations; i++) {
//            parameters.set("targetName", nodes.get(i).getName());
//
//            CStaS c = new CStaS();
//            c.setIndependenceWrapper(new SemBicTest());
//            c.setTrueDag(trueDag);
//
//            long cStart = System.currentTimeMillis();
//
//            Graph cGraph = c.search(fullData, parameters);
//            cev[i][0] = c.getEvBound();
//            cev[i][1] = c.getMBEvBound();
//
//            long cStop = System.currentTimeMillis();
//
//            FgesMbAncestors f = new FgesMbAncestors();
//            f.setScoreWrapper(new edu.cmu.tetrad.algcomparison.score.SemBicScore());
//
//            long fStart = System.currentTimeMillis();
//
//            Graph fGraph = f.search(fullData, parameters);
//
//            long fStop = System.currentTimeMillis();
//
//            int cTotal = cGraph.getNumNodes() - 1;
//            int cAnc = 0;
//
//            List<Node> cNodes = cGraph.getNodes();
//            cNodes = GraphUtils.replaceNodes(cNodes, trueDag.getNodes());
//
//            for (Node node : cNodes) {
//                if (node == nodes.get(i)) continue;
//                if (trueDag.isAncestorOf(node, nodes.get(i))) {
//                    cAnc++;
//                }
//            }
//
//            int fTotal = fGraph.getNumNodes() - 1;
//            int fAnc = 0;
//
//            List<Node> fNodes = fGraph.getNodes();
//            fNodes = GraphUtils.replaceNodes(fNodes, trueDag.getNodes());
//
//            for (Node node : fNodes) {
//                if (node == nodes.get(i)) continue;
//                if (trueDag.isAncestorOf(node, nodes.get(i))) {
//                    fAnc++;
//                }
//            }
//
//            final double cTime = (cStop - cStart) / 1000.0;
//            final double fTime = (fStop - fStart) / 1000.0;
//
//            System.out.println("### cTotal = " + (cTotal - 1) + " cAnc = " + cAnc + " fTotal = " + (fTotal - 1) + " fAnc = " + fAnc);
//            System.out.println("### cElapsed = " + cTime + "s fElapsed = " + fTime + "s");
//            System.out.println();
//
//            counts[i][0] = cTotal;
//            counts[i][1] = cAnc;
//            counts[i][2] = fTotal;
//            counts[i][3] = fAnc;
//
//            fp[i][0] = cTotal - cAnc;
//            fp[i][1] = fTotal - fAnc;
//
//            times[i][0] = cTime;
//            times[i][1] = fTime;
//
//        }
//
//        TextTable table = new TextTable(numIterations + 1, 11);
//        int col = 0;
//
//        table.setToken(0, col++, "Index");
//
//        table.setToken(0, col++, "" + "cTotal");
//        table.setToken(0, col++, "" + "cAnc");
//        table.setToken(0, col++, "" + "cFP");
//        table.setToken(0, col++, "" + "cEV");
//        table.setToken(0, col++, "" + "cEV-MB");
//
//        table.setToken(0, col++, "" + "fTotal");
//        table.setToken(0, col++, "" + "fAnc");
//        table.setToken(0, col++, "" + "fFP");
//
//        table.setToken(0, col++, "" + "cTime");
//        table.setToken(0, col++, "" + "rTime");
//
//        NumberFormat nf = new DecimalFormat("0.00");
//
//        for (int i = 0; i < numIterations; i++) {
//            col = 0;
//
//            table.setToken(i + 1, col++, "" + (i + 1));
//
//            table.setToken(i + 1, col++, "" + counts[i][0]);
//            table.setToken(i + 1, col++, "" + counts[i][1]);
//            table.setToken(i + 1, col++, "" + fp[i][0]);
//            table.setToken(i + 1, col++, "" + nf.format(cev[i][0]));
//            table.setToken(i + 1, col++, "" + nf.format(cev[i][1]));
//
//            table.setToken(i + 1, col++, "" + counts[i][2]);
//            table.setToken(i + 1, col++, "" + counts[i][3]);
//            table.setToken(i + 1, col++, "" + fp[i][1]);
//
//
//            table.setToken(i + 1, col++, "" + times[i][0]);
//            table.setToken(i + 1, col++, "" + times[i][1]);
//        }
//
//        System.out.println(table);
//
//    }

    //    @Test
//    public void testCombinations() {
//        int avgDegree = 6;
//
//        Parameters parameters = new Parameters();
//        parameters.set("numLatents", 0);
//        parameters.set("avgDegree", avgDegree);
//        parameters.set("maxDegree", 100);
//        parameters.set("maxIndegree", 100);
//        parameters.set("maxOutdegree", 100);
//        parameters.set("connected", false);
//
//        parameters.set("verbose", false);
//
//        parameters.set("coefLow", 0.3);
//        parameters.set("coefHigh", 1.0);
//        parameters.set("intervalBetweenShocks", 40);
//        parameters.set("intervalBetweenRecordings", 40);
//
//        parameters.set("parallelism", 40);
//
//        parameters.set("penaltyDiscount", 2);
//        parameters.set("numSubsamples", 50);
//        parameters.set("percentSubsampleSize", 0.5);
//        parameters.set("maxQ", 200);
//        parameters.set("maxEr", 5);
//        parameters.set("depth", 3);
//
//        int numIterations = 5;
//
//        for (int numNodes : new int[]{400, 600}) {//, 100, 200, 400, 600}) {
//            for (int sampleSize : new int[]{1000}) {//50, 100, 200, 400, 600, 1000}) {
//                parameters.set("numMeasures", numNodes);
//                parameters.set("sampleSize", sampleSize);
//
//
//                List<int[]> cstasRet = new ArrayList<>();
//
//                RandomGraph randomForward = new RandomForward();
//                LinearFisherModel fisher = new LinearFisherModel(randomForward);
//                fisher.createData(parameters);
//                DataSet fullData = (DataSet) fisher.getDataModel(0);
//
//                Graph trueDag = fisher.getTrueGraph(0);
//                Graph truePattern = SearchGraphUtils.patternForDag(trueDag);
//
//                int m = trueDag.getNumNodes() + 1;
//
//                for (int i = 0; i < numIterations; i++) {
//                    m--;
//
//                    final Node t = trueDag.getNodes().get(m - 1);
//                    Set<Node> p = new HashSet<>(trueDag.getParents(t));
//
//                    for (Node q : new HashSet<>(p)) {
//                        p.addAll(trueDag.getParents(q));
//                    }
//
//                    if (p.size() < 15) {
//                        i--;
//                        continue;
//                    }
//
//                    parameters.set("targetName", "X" + m);
//
//                    CStaS cstas = new CStaS(new SemBicTest());
//                    cstas.setTrueDag(trueDag);
//                    Graph graph = cstas.search(fullData, parameters);
//
//                    int[] ret = getResult(truePattern, graph);
//                    cstasRet.add(ret);
//                }
//
//                int allFp = 0;
//
//                for (int i = 0; i < numIterations; i++) {
//                    allFp += cstasRet.get(i)[1];
//                }
//
//                double avgFp = allFp / (double) numIterations;
//
//                System.out.println("# nodes = " + numNodes + " sample size = " + sampleSize + " avg FP = " + avgFp);
//            }
//        }
//
//    }

    @Test
    public void testHughes() {
        int numEffects = 50;
        int numSubsamples = 50;
        double penaltyDiscount = 3;
        double minBump = 0.01;
        int qFrom = 100;
        int qTo = 500;
        int qIncrement = 100;

        NumberFormat nf = new DecimalFormat("0.00");

        try {
            File file = new File("/Users/user/Downloads/stand.data.exp.csv");
            File file2 = new File("/Users/user/Downloads/mutant.txt");
            File file3 = new File("/Users/user/Downloads/z.pos.txt");

            TabularDataReader reader = new ContinuousTabularDataFileReader(file, Delimiter.COMMA);
            Dataset data = reader.readInData();

            DataSet standDataExp = (DataSet) DataConvertUtils.toDataModel(data);

            for (Node node : standDataExp.getVariables()) {
                String name = node.getName();
                if (name.startsWith("\""))
                    name = name.substring(1, name.length() - 1);
                node.setName(name);
            }

            TabularDataReader reader2 = new ContinuousTabularDataFileReader(file2, Delimiter.TAB);
            Dataset data2 = reader2.readInData();

            DataSet mutant = (DataSet) DataConvertUtils.toDataModel(data2);

            BufferedReader in = new BufferedReader(new FileReader(file3));
            String line;
            List<Node> selectionVars = new ArrayList<>();
            List<Integer> zPos = new ArrayList<>();

            // Predictors.
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split(" ");

                for (String token : tokens) {
                    final int index = Integer.parseInt(token) - 1; // one-indexed.
                    selectionVars.add(standDataExp.getVariable(index));
                    zPos.add(index);
                }
            }

            // Effects.
            List<Node> effects = new ArrayList<>();
            int i = 0;
            int count = 0;

            while (count < numEffects) {
                final Node node = standDataExp.getVariables().get(i);

                if (!selectionVars.contains(node)) {
                    effects.add(node);
                    count++;
                }

                i++;
            }

            List<Node> augmented = new ArrayList<>(selectionVars);

            for (Node target : effects) {
                if (!augmented.contains(target)) augmented.add(target);
            }

            DataSet selection = standDataExp.subsetColumns(augmented);

            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(selection));
            score.setPenaltyDiscount(penaltyDiscount);
            IndependenceTest test = new IndTestScore(score);

            edu.cmu.tetrad.search.CStaSMulti cstas = new edu.cmu.tetrad.search.CStaSMulti();
            cstas.setNumSubsamples(numSubsamples);
            cstas.setqFrom(qFrom);
            cstas.setqTo(qTo);
            cstas.setqIncrement(qIncrement);

            LinkedList<List<edu.cmu.tetrad.search.CStaSMulti.Record>> allRecords
                    = cstas.getRecords(selection, selectionVars, effects, test);

            List<Double> sortedRatios = new ArrayList<>();
            DataSet ratios = new ColtDataSet(mutant.getNumRows(), mutant.getVariables());

            for (int cause = 0; cause < mutant.getNumRows(); cause++) {
                final double causeBump = abs(cell(cause, zPos.get(cause), mutant) - avg(cause, zPos.get(cause), mutant));

                for (int effect = 0; effect < mutant.getNumColumns(); effect++) {
                    final double effectBump = abs(cell(cause, effect, mutant) - avg(cause, effect, mutant));
                    double ratio = effectBump / causeBump;
                    ratios.setDouble(cause, effect, ratio);

                    if (causeBump >= minBump) {
                        sortedRatios.add(ratio);
                    }
                }
            }

            System.out.println("B");

            sortedRatios.sort((o1, o2) -> Double.compare(o2, o1));

            System.out.println();

            for (List<CStaSMulti.Record> records : allRecords) {
                System.out.println(cstas.makeTable(records));
                System.out.println(getResult(nf, mutant, zPos, records, ratios, sortedRatios));
            }

            System.out.println("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getResult(NumberFormat nf, DataSet mutant, List<Integer> zPos, List<CStaSMulti.Record> records,
                             DataSet ratios, List<Double> sortedRatios) {
        StringBuilder buf = new StringBuilder();

        double[] cutoffs = {0.001, 0.01, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.6, 0.7, .8, .9, 1.0};
        double[] _cutoffs = new double[cutoffs.length];

        System.out.println("Sorted ratios size = " + sortedRatios.size());

        for (int w = 0; w < cutoffs.length; w++) {
            double v = sortedRatios.size() * cutoffs[w] - 1;
            final Double cutoff = sortedRatios.get((int) v);
            buf.append("\n").append(cutoffs[w] * 100).append("% of all ratios are above ").append(nf.format(cutoff));
            _cutoffs[w] = cutoff;
        }

        buf.append("\nq = ").append(records.size()).append("\n");

        int[] counts = new int[cutoffs.length];

        for (CStaSMulti.Record record : records) {
            Node cause = record.getCause();
            Node effect = record.getTarget();

            int _cause = mutant.getColumn(mutant.getVariable(cause.getName()));

            for (int s = 0; s < zPos.size(); s++) {
                if (_cause == zPos.get(s)) _cause = s;
            }

            int _effect = mutant.getColumn(mutant.getVariable(effect.getName()));

            final double ratio = ratios.getDouble(_cause, _effect);
//                System.out.println("The ratio for record " + (e + 1) + " is " + nf.format(ratio));

            for (int w = 0; w < cutoffs.length; w++) {
                if (ratio >= _cutoffs[w]) counts[w]++;
            }
        }

            buf.append("\n");

        for (int w = 0; w < counts.length; w++) {
            final double allRatiosPercent = cutoffs[w] * 100.0;
            buf.append("\nThere are ").append(counts[w]).append(" records with ratios in the top ").append(nf.format(allRatiosPercent)).append("% of all ratios.");
        }

        buf.append("\n");

        for (int w = 0; w < counts.length; w++) {
            final double allRatiosPercent = cutoffs[w] * 100.0;
            final double allRecordsPercent = 100.0 * (counts[w] / (double) records.size());
            buf.append("\n").append(nf.format(allRecordsPercent)).append("% of records have ratios in the top ").append(nf.format(allRatiosPercent)).append("% of all ratios.");
        }

        return buf.toString();
    }

    private double cell(int cause, int effect, DataSet mutants) {
        return mutants.getDouble(cause, effect);
    }

    private double avg(int cause, int effect, DataSet mutants) {
        double sum = 0.0;

        for (int p = 0; p < mutants.getNumRows(); p++) {
            if (p == cause) continue;
            sum += cell(p, effect, mutants);
        }

        return sum / (double) (mutants.getNumRows() - 1);
    }

    //    @Test
    public void testConditionalGaussian() {

        Parameters parameters = new Parameters();
        parameters.set("numMeasures", 100);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", 5);

        parameters.set("numCategories", 3);
        parameters.set("percentDiscrete", 50);
        parameters.set("numRuns", 10);
        parameters.set("differentGraphs", true);
        parameters.set("sampleSize", 1000);

        parameters.set("penaltyDiscount", 1.2);
        parameters.set("numSubsamples", 100);
        parameters.set("maxEr", 10);
        parameters.set("targetName", "X100");

        RandomGraph graph = new RandomForward();

        LeeHastieSimulation simulation = new LeeHastieSimulation(graph);
        simulation.createData(parameters);

        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            edu.cmu.tetrad.search.CStaS cStaS = new edu.cmu.tetrad.search.CStaS();
            cStaS.setTrueDag(simulation.getTrueGraph(i));
            final DataSet dataSet = (DataSet) simulation.getDataModel(i);
            final ConditionalGaussianScore score = new ConditionalGaussianScore(dataSet, 1, false);
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            final IndependenceTest test = new IndTestScore(score);
            List<edu.cmu.tetrad.search.CStaS.Record> records = cStaS.getRecords(dataSet,
                    simulation.getTrueGraph(i).getNode(parameters.getString("targetName")),
                    test, 0.02);
            System.out.println(cStaS.makeTable(records));
        }
    }

    private int[] getResult(Graph trueDag, Graph graph) {
        graph = GraphUtils.replaceNodes(graph, trueDag.getNodes());

        if (graph == null) throw new NullPointerException("Graph null");

        Set<Edge> allTreks = new HashSet<>();
        Set<Edge> allAncestors = new HashSet<>();
        Set<Edge> nonTreks = new HashSet<>();
        Set<Edge> nonAncestors = new HashSet<>();

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            boolean ancestor = trueDag.isAncestorOf(x, y);

            List<List<Node>> treks = GraphUtils.treks(trueDag, x, y, 10);

            boolean trekToTarget = !treks.isEmpty();

            if (trekToTarget) {
                allTreks.add(edge);
            } else {
                nonTreks.add(edge);
            }

            if (ancestor) {
                allAncestors.add(edge);
            } else {
                nonAncestors.add(edge);
            }
        }

        int[] ret = new int[4];

        ret[0] = allTreks.size();
        ret[1] = allAncestors.size();
        ret[2] = nonTreks.size();
        ret[3] = nonAncestors.size();

        return ret;
    }

    public static void main(String[] args) {
        new TestCStaS().testCStaS();
    }
}



