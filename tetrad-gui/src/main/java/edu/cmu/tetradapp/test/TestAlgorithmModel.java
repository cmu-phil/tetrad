package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetradapp.model.AlgcomparisonModel;
import edu.cmu.tetradapp.model.Simulation;

import java.util.List;

public class TestAlgorithmModel {


    public static void main(String[] args) {
        new TestAlgorithmModel().test1();
    }

    private void test1() {

        AlgcomparisonModel algcomparisonModel = new AlgcomparisonModel();

        List<Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation>> simulations
                = algcomparisonModel.getSimulations();
        List<Class<? extends Algorithm>> algorithms =  algcomparisonModel.getAlgorithms();
        List<Class<? extends Statistic>> statistics = algcomparisonModel.getStatistics();

        System.out.println("Simulations: ");

        for (int i = 0; i < simulations.size(); i++) {
            String[] split = simulations.get(i).getName().split("\\.");
            String name = split[split.length - 1];
            System.out.println((i + 1) + ". " + name);
        }

        System.out.println("Algorithms: ");

        for (int i = 0; i < algorithms.size(); i++) {
            String[] split = algorithms.get(i).getName().split("\\.");
            String name = split[split.length - 1];
            System.out.println((i + 1) + ". " + name);
        }

        System.out.println("Statistics: ");

        for (int i = 0; i < statistics.size(); i++) {
            String[] split = statistics.get(i).getName().split("\\.");
            String name = split[split.length - 1];
            System.out.println((i + 1) + ". " + name);
        }
    }
}
