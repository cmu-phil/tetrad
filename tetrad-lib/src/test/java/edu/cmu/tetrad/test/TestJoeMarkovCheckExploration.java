package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.statistic.BicDiff;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.*;

public class TestJoeMarkovCheckExploration {

    public static void main(String... args) {
        new TestJoeMarkovCheckExploration().test1();
    }

    public void test1() {
        Graph trueGraph = RandomGraph.randomGraph(15, 0, 30, 100,
                100, 100, false);

        SemPm pm = new SemPm(trueGraph);

        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

//        for (double penalty = 0.01; penalty <= .2; penalty += 0.1) {
            for (double penalty = 0.5; penalty <= 10; penalty += 0.1) {
                penalty = Math.round(penalty * 10) / 10.0;
            SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
            score.setPenaltyDiscount(penalty);

//            IndTestFisherZ test = new IndTestFisherZ(dataSet, penalty);

            for (int i = 0; i < 10; i++) {
                Graph cpdag = null;
                try {
                    cpdag = new PermutationSearch(new Boss(score)).search();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
//                Graph cpdag = new Fges(score).search();
//                Graph cpdag = new Pc(test).search();
                printLine(trueGraph, cpdag, dataSet, penalty, false);
            }
        }

        System.out.println("\n\nTrue CPDAG\n");

        Graph trueCpdag = GraphTransforms.dagToCpdag(trueGraph);

        for (int i = 0; i < 30; i++) {
            printLine(trueCpdag, trueCpdag, dataSet, 1, true);
        }
    }

    private void printLine(Graph trueGraph, Graph cpdag, DataSet dataSet, double penalty, boolean override) {
        Pair<List<Pair<IndependenceFact, Double>>, Graph> ret = getPValues(cpdag, dataSet);

        List<Pair<IndependenceFact, Double>> pValues = ret.getLeft();

        // Sort pValues low to high.
        pValues.sort(Comparator.comparingDouble(Pair::getRight));

        List<Double> pValuesArray = new ArrayList<>();
        for (Pair<IndependenceFact, Double> pValue : pValues) {
            pValuesArray.add(pValue.getRight());
        }

        int fdr = StatUtils.fdr(0.05, pValuesArray);
        double _pValue;

        if (fdr == -1) {
            _pValue = 0;
        } else {
            Pair<IndependenceFact, Double> independenceFactDoublePair = pValues.get(fdr);
            _pValue = independenceFactDoublePair.getRight();
        }

        double ad = checkAgainstAndersonDarlingTest(pValuesArray);
        double ks = getKsPValue(pValuesArray);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

//        if (ad < 0.001) return;

        double bicDiffValue = new BicDiff().getValue(trueGraph, cpdag, dataSet, new Parameters());

        System.out.println("penalty " + penalty + " p-value = " + nf.format(_pValue)
                           + " FDR = " + fdr + " AD = " + nf.format(ad) + " KS = "
                           + nf.format(ks) + " # tests = " + pValues.size() + " # edges = "
                           + cpdag.getNumEdges() + " bicDiff = " + nf.format(bicDiffValue));
    }

    private static @NotNull Pair<List<Pair<IndependenceFact, Double>>, Graph> getPValues(Graph cpdag, DataSet dataSet) {
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05);
        List<Pair<IndependenceFact, Double>> pValues = new ArrayList<>();

        List<Integer> all = new ArrayList<>();

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            all.add(i);
        }

        test.setRows(all);

        Set<IndependenceFact> facts = new HashSet<>();

        MsepTest msepTest = new MsepTest(cpdag);

        for (Node x : cpdag.getNodes()) {
            for (Node y : cpdag.getNodes()) {
                if (x.equals(y)) {
                    continue;
                }

                IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(cpdag.getParents(x)));

                if (!facts.contains(fact)) {
                    boolean msep = msepTest.checkIndependence(fact.getX(), fact.getY(), fact.getZ()).isIndependent();

                    if (msep) {
                        Collections.shuffle(all);

                        List<Integer> rows = all.subList(0, (int) (dataSet.getNumRows() * 0.8));
                        test.setRows(rows);

                        double pValue = test.checkIndependence(fact.getX(), fact.getY(), fact.getZ()).getPValue();
                        pValues.add(Pair.of(fact, pValue));
                    }
                }

                facts.add(fact);
            }
        }

        return Pair.of(pValues, cpdag);
    }

    public Double checkAgainstAndersonDarlingTest(List<Double> pValues) {
        double min = pValues.stream().min(Double::compareTo).orElseThrow(NoSuchElementException::new);
        double max = pValues.stream().max(Double::compareTo).orElseThrow(NoSuchElementException::new);

        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        return generalAndersonDarlingTest.getP();
    }

    /**
     * Calculates the Kolmogorov-Smirnov (KS) p-value for a list of independence test results.
     *
     * @param pValues the list of independence test results
     * @return the KS p-value calculated using the list of independence test results
     */
    public double getKsPValue(List<Double> pValues) {
        return UniformityTest.getKsPValue(pValues, 0.0, 1.0);
    }
}
