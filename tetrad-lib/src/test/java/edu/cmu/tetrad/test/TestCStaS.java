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

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;

import java.io.*;
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

    public void testCStaS() {
        int numNodes = 500;
        int numEffects = 500;
        int avgDegree = 1;
        int sampleSize = 100;
        int numSubsamples = 50;
        double penaltyDiscount = 1;
        double selectionAlpha = 0.2;

        int qFrom = 100;
        int qTo = 1000;
        int qIncrement = 100;

        CStaS.PatternAlgorithm algorithm = CStaS.PatternAlgorithm.FGES;
        CStaS.SampleStyle sampleStyle = CStaS.SampleStyle.SPLIT;

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", penaltyDiscount);
        parameters.set("numSubsamples", numSubsamples);
        parameters.set("depth", 4);
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

        RandomUtil.getInstance().setSeed(10302005L);

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
        cstas.setNumSubsamples(numSubsamples);
        cstas.setPatternAlgorithm(algorithm);
        cstas.setSampleStyle(sampleStyle);
        cstas.setqFrom(qFrom);
        cstas.setqTo(qTo);
        cstas.setqIncrement(qIncrement);
        cstas.setVerbose(true);

        List<Node> potentialEffects = new ArrayList<>();

        for (int t = 0; t < numEffects; t++) {
            potentialEffects.add(nodes.get(t));
        }

        List<Node> potentialCauses = new ArrayList<>();
        potentialEffects = GraphUtils.replaceNodes(potentialEffects, dataSet.getVariables());

        for (Node target : potentialEffects) {
            List<Node> _selectionVars = DataUtils.selectVariables(dataSet, target, selectionAlpha, 40);

            if (_selectionVars.size() > potentialCauses.size()) {
                potentialCauses = _selectionVars;
            }
        }

        System.out.println("Selected # nodes = " + potentialCauses.size());

        potentialCauses = GraphUtils.replaceNodes(potentialCauses, dataSet.getVariables());
        List<Node> augmented = new ArrayList<>(potentialCauses);

        for (Node target : potentialEffects) {
            final Node variable = dataSet.getVariable(target.getName());
            if (!augmented.contains(variable)) augmented.add(variable);
        }

        augmented = GraphUtils.replaceNodes(augmented, dataSet.getVariables());
        DataSet augmentedData = dataSet.subsetColumns(augmented);

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(augmentedData));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score);

        final LinkedList<LinkedList<CStaS.Record>> allRecords = cstas.getRecords(
                augmentedData, potentialCauses, potentialEffects, test, "/Users/user/Downloads/cstas.fges.out");

        for (LinkedList<CStaS.Record> records : allRecords) {
            System.out.println(cstas.makeTable(records, false));
        }

//        System.out.println("\n\nCStaR table");
//
//        final LinkedList<CStaS.Record> records = CStaS.cStar(allRecords);
//
//        System.out.println(cstas.makeTable(records, false));
    }

    public void testCStaS2() {
        int numNodes = 300;
        double avgDegree = 1;
        int sampleSize = 300;

        int numEffects = 10;
        int numSubsamples = 50;

        double penaltyDiscount = 1;
        double selectionAlpha = .3;
        int startIndex = 0;

        CStaS.PatternAlgorithm algorithm = CStaS.PatternAlgorithm.FGES;
        CStaS.SampleStyle sampleStyle = CStaS.SampleStyle.SPLIT;

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", penaltyDiscount);
        parameters.set("numSubsamples", numSubsamples);
        parameters.set("depth", 4);
        parameters.set("selectionAlpha", selectionAlpha);

        parameters.set("parallelism", 40);

        parameters.set("numMeasures", numNodes);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", 200);
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

        List<Node> potentialEffects = new ArrayList<>();

        for (int t = 0; t < numEffects; t++) {
            potentialEffects.add(nodes.get(t + startIndex));
        }

        List<Node> potentialCauses = new ArrayList<>();
        potentialEffects = GraphUtils.replaceNodes(potentialEffects, dataSet.getVariables());

        for (Node target : potentialEffects) {
            List<Node> _selectionVars = DataUtils.selectVariables(dataSet, target, selectionAlpha, 40);

            if (_selectionVars.size() > potentialCauses.size()) {
                potentialCauses = _selectionVars;
            }
        }

        int totalCauseEffect = 0;

        for (Node effect : potentialEffects) {
            for (Node cause : potentialCauses) {
                if (trueDag.isAncestorOf(trueDag.getNode(cause.getName()), trueDag.getNode(effect.getName()))) {
                    totalCauseEffect++;
                }
            }
        }


        System.out.println("Selected # nodes = " + potentialCauses.size());
        System.out.println("Total # cause/effect pairs: " + totalCauseEffect);

        int qFrom = (int)(.5 * totalCauseEffect);
        int qTo = qFrom;
        int qIncrement = 1;

        CStaS cstas = new CStaS();
        cstas.setNumSubsamples(numSubsamples);
        cstas.setPatternAlgorithm(algorithm);
        cstas.setSampleStyle(sampleStyle);
        cstas.setqFrom(qFrom);
        cstas.setqTo(qTo);
        cstas.setqIncrement(qIncrement);
        cstas.setVerbose(true);
        cstas.setTrueDag(trueDag);

        potentialCauses = GraphUtils.replaceNodes(potentialCauses, dataSet.getVariables());
        List<Node> augmented = new ArrayList<>(potentialCauses);

        for (Node target : potentialEffects) {
            final Node variable = dataSet.getVariable(target.getName());
            if (!augmented.contains(variable)) augmented.add(variable);
        }

        augmented = GraphUtils.replaceNodes(augmented, dataSet.getVariables());
        DataSet augmentedData = dataSet.subsetColumns(augmented);

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(augmentedData));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score);

        final LinkedList<LinkedList<CStaS.Record>> allRecords = cstas.getRecords(
                augmentedData, potentialCauses, potentialEffects, test);

        for (LinkedList<CStaS.Record> records : allRecords) {
            System.out.println(cstas.makeTable(records, true));
        }

//        System.out.println("\n\nCStaR table");
//
//        final LinkedList<CStaS.Record> records = CStaS.cStar(allRecords);
//
//        System.out.println(cstas.makeTable(records, false));
    }

    private void testHughes() {
        int numSubsamples = 50;
        int numEffects = 500;
        double penaltyDiscount = 2;
        double minBump = 0.0;
        int qFrom = 100;
        int qTo = 5000;
        int qIncrement = 0;
        CStaS.PatternAlgorithm algorithm = CStaS.PatternAlgorithm.FGES;
        CStaS.SampleStyle sampleStyle = CStaS.SampleStyle.SPLIT;

        try {

            // Load stand.data.exp, mutant, and z.pos.
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
            List<Node> possibleEffects = new ArrayList<>();

            for (int i = 0; i < numEffects; i++) {
                possibleEffects.add(standDataExp.getVariables().get(i));
            }

            List<Node> augmented = new ArrayList<>(possibleCauses);

            for (Node effect : possibleEffects) {
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
            cstas.setqIncrement(qIncrement);
            cstas.setPatternAlgorithm(algorithm);
            cstas.setSampleStyle(sampleStyle);
            cstas.setVerbose(true);

            LinkedList<LinkedList<CStaS.Record>> allRecords
                    = cstas.getRecords(augmentedData, possibleCauses, possibleEffects, test, "/Users/user/Downloads/hughes.out");

            for (LinkedList<CStaS.Record> records : allRecords) {
                System.out.println(cstas.makeTable(records, false));

                List<Double> sortedRatios = new ArrayList<>();

                List<Node> variables = mutant.getVariables();
                double[][] ratios = new double[mutant.getNumRows()][mutant.getNumColumns()];

                for (int cause = 0; cause < mutant.getNumRows(); cause++) {
                    final double causeBump = cell(cause, zPos.get(cause), mutant) - avg(cause, zPos.get(cause), mutant);

                    for (int effect = 0; effect < mutant.getNumColumns(); effect++) {
                        final double effectBump = cell(cause, effect, mutant) - avg(cause, effect, mutant);

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

                for (CStaS.Record record : records) {
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

                    double ratio = ratios[_cause][_effect];

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

            }

            System.out.println("\n\nCStaR table");

            final LinkedList<CStaS.Record> records = CStaS.cStar(allRecords);

            System.out.println(cstas.makeTable(records, false));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double cell(int cause, int effect, DataSet mutants) {
        return mutants.getDouble(cause, effect);
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

    public void testXueEr() {
        try {
            File dir = new File("//Users/user/Downloads");

            Dataset dataset = new ContinuousTabularDataFileReader(new File(dir, "searchexpv1.csv"), Delimiter.TAB).readInData();
            final DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);


            List<Node> possibleCauses = new ArrayList<>();
            List<Node> possibleEffects = new ArrayList<>();

            possibleCauses.add(dataSet.getVariable("G1"));

            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(2);

            IndependenceTest test = new IndTestScore(score);

            int numSubsamples = 50;
            CStaS.PatternAlgorithm algorithm = CStaS.PatternAlgorithm.FGES;
            CStaS.SampleStyle sampleStyle = CStaS.SampleStyle.SPLIT;

            final CStaS cStaS = new CStaS();
            cStaS.setNumSubsamples(numSubsamples);
            cStaS.setPatternAlgorithm(algorithm);
            cStaS.setSampleStyle(sampleStyle);
            cStaS.setqFrom(100);
            cStaS.setqTo(1000);
            cStaS.setqIncrement(100);
            cStaS.setVerbose(true);
            LinkedList<LinkedList<CStaS.Record>> records = cStaS.getRecords(dataSet, possibleCauses, possibleEffects, test);

            for (LinkedList<CStaS.Record> _records : records) {
                cStaS.makeTable(_records, true);
            }

            final LinkedList<CStaS.Record> cstarRecords = CStaS.cStar(records);
            System.out.println(cStaS.makeTable(cstarRecords, true));




            // Run CStaS as above.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        new TestCStaS().testCStaS2();
    }
}



