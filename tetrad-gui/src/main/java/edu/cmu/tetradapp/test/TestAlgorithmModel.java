package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.AlgcomparisonModel;
import edu.cmu.tetradapp.model.Simulation;

import java.util.List;

public class TestAlgorithmModel {


    public static void main(String[] args) {
        new TestAlgorithmModel().test1();
    }

    private void test1() {

        AlgcomparisonModel algcomparisonModel = new AlgcomparisonModel(new Parameters());

        List<String> simulations = algcomparisonModel.getSimulationsNames();
        List<String> algorithms =  algcomparisonModel.getAlgorithmsNames();
        List<String> statistics = algcomparisonModel.getStatisticsNames();

        System.out.println("Simulations: ");

        for (int i = 0; i < simulations.size(); i++) {
            String name = simulations.get(i);
            System.out.println((i + 1) + ". " + name);
        }

        System.out.println("Algorithms: ");

        for (int i = 0; i < algorithms.size(); i++) {
            String name = algorithms.get(i);
            System.out.println((i + 1) + ". " + name);
        }

        System.out.println("Statistics: ");

        for (int i = 0; i < statistics.size(); i++) {
            String name = statistics.get(i);
            System.out.println((i + 1) + ". " + name);
        }
    }
}
