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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.FgesMbAncestors;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TextTable;
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

/**
 * Tests CStaS.
 *
 * @author Joseph Ramsey
 */
public class TestCStaS {

    public void testIda() {
        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 30);
        parameters.set("percentSubsampleSize", 0.5);
        parameters.set("maxQ", 100);
        parameters.set("targetName", "X50");
        parameters.set("alpha", 0.001);

        Graph graph = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        Node y = dataSet.getVariable("X10");

        SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndependenceTest test = new IndTestScore(score);

        PcAll pc = new PcAll(test, null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.PRIORITY);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
        Graph pattern = pc.search();

        Ida ida = new Ida(dataSet, pattern, dataSet.getVariables());

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    @Test
    public void testCStaS() {
        int numTargets = 10;

        int numNodes = 500;
        int avgDegree = 4;
        int sampleSize = 200;
        int numIterations = 10;
        int numSubsamples = 50;
        double penaltyDiscount = 1;
        double selectionAlpha = 0.1;
        CStaS.PatternAlgorithm algorithm = CStaS.PatternAlgorithm.PC_STABLE;

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

            CStaS cstas = new CStaS();
            cstas.setTrueDag(trueDag);
            cstas.setNumSubsamples(numSubsamples);
            cstas.setPatternAlgorithm(algorithm);
            cstas.setqFrom(50);
            cstas.setqTo(100);
            cstas.setqIncrement(1);

            List<Node> targets = new ArrayList<>();

            for (int t = 0; t < numTargets; t++) {
                targets.add(nodes.get(t));
            }

            List<Node> selectionVars = new ArrayList<>();
            targets = GraphUtils.replaceNodes(targets, dataSet.getVariables());

            for (Node target : targets) {
                List<Node> _selectionVars = DataUtils.selectVariables(dataSet, target, selectionAlpha, 40);
                _selectionVars.removeAll(targets);

                if (_selectionVars.size() > selectionVars.size()) {
                    selectionVars = _selectionVars;
                }
            }

            System.out.println("Selected # nodes = " + selectionVars.size());

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

            List<CStaS.Record> records = cstas.getRecords(augmentedData, selectionVars, targets, test);

            System.out.println(cstas.makeTable(records));
        }
    }

    @Test
    public void testHughes() {
        int numSubsamples = 100;
        int numEffects = 200;
        double penaltyDiscount = 3;
        double minBump = 0.001;
        int qFrom = 50;
        int qTo = 500;
        int qIcrement = 50;
        CStaS.PatternAlgorithm algorithm = CStaS.PatternAlgorithm.PC_STABLE;

        try {

            // Load stand.daa.exp, mutant, and z.pos.
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
            List<Node> possibleCauses = new ArrayList<>();
            List<Integer> zPos = new ArrayList<>();

            // Causes.
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split(" ");

                for (String token : tokens) {
                    final int index = Integer.parseInt(token) - 1; // one-indexed.
                    possibleCauses.add(standDataExp.getVariable(index));
                    zPos.add(index);
                }
            }

            // Effects.
            List<Node> effects = new ArrayList<>();
            int i = 0;
            int count = 0;

            while (count < numEffects) {
                final Node node = standDataExp.getVariables().get(i);

                if (!possibleCauses.contains(node)) {
                    effects.add(node);
                    count++;
                }

                i++;
            }

            List<Node> augmented = new ArrayList<>(possibleCauses);

            for (Node effect : effects) {
                if (!augmented.contains(effect)) augmented.add(effect);
            }

            DataSet augmentedData = standDataExp.subsetColumns(augmented);

            // Run CStaS.
            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(augmentedData));
            score.setPenaltyDiscount(penaltyDiscount);
            IndependenceTest test = new IndTestScore(score);

            CStaS cstas = new CStaS();
            cstas.setNumSubsamples(numSubsamples);
            cstas.setqFrom(qFrom);
            cstas.setqTo(qTo);
            cstas.setqIncrement(qIcrement);
            cstas.setPatternAlgorithm(algorithm);
            cstas.setVerbose(true);

            List<CStaS.Record> records = cstas.getRecords(augmentedData, possibleCauses, effects, test);

            System.out.println(cstas.makeTable(records));

            List<Double> sortedRatios = new ArrayList<>();

            List<Node> variables = mutant.getVariables();
            double[][] ratios = new double[mutant.getNumRows()][mutant.getNumColumns()];

            for (int cause = 0; cause < mutant.getNumRows(); cause++) {
                final double causeBump = (cell(cause, zPos.get(cause), mutant) - avg(cause, zPos.get(cause), mutant));

                for (int effect = 0; effect < mutant.getNumColumns(); effect++) {
                    final double effectBump = (cell(cause, effect, mutant) - avg(cause, effect, mutant));

                    double ratio = effectBump / causeBump;
                    ratios[cause][effect] = ratio;

                    if (effectBump > minBump) {
                        sortedRatios.add(ratio);
                    }
                }
            }

            sortedRatios.sort((o1, o2) -> Double.compare(o2, o1));

            int size = sortedRatios.size();

            double[] cutoffs = {0.01, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.6, 0.7, .8, .9, 1.0};
            double[] _cutoffs = new double[cutoffs.length];

            for (int w = 0; w < cutoffs.length; w++) {
                final Double cutoff = sortedRatios.get((int) (size * cutoffs[w] - 1));
                _cutoffs[w] = cutoff;
            }

            int[] counts = new int[cutoffs.length];

            for (int e = 0; e < records.size(); e++) {
                CStaS.Record record = records.get(e);
                Node causeNode = record.getCauseNode();
                Node effectNode = record.getEffectNode();

                int _cause = variables.indexOf(causeNode);

                for (int s = 0; s < zPos.size(); s++) {
                    if (_cause == zPos.get(s)) {
                        _cause = s;
                        break;
                    }
                }

                int _effect = variables.indexOf(effectNode);

                double ratio = -1;

                try {
                    ratio = ratios[_cause][_effect];
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                for (int w = 0; w < cutoffs.length; w++) {
                    if (ratio >= _cutoffs[w]) counts[w]++;
                }
            }

            System.out.println("\nPercentages");
            System.out.println();
            NumberFormat nf = new DecimalFormat("0.00");

            for (int w = 0; w < counts.length; w++) {
                System.out.println((cutoffs[w] * 100.0) + "% " + nf.format(100.0 * (counts[w] / (double) records.size())));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double cell(int predictor, int effect, DataSet mutants) {
        return mutants.getDouble(predictor, effect);
    }

    private double avg(int cause, int effect, DataSet mutants) {
        double sum = 0.0;
        int count = 0;

        for (int p = 0; p < mutants.getNumRows(); p++) {
            if (p == cause) continue;
            count++;
            sum += cell(p, effect, mutants);
        }

        return sum / count;
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
}



