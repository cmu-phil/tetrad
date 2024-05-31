package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GridSearchModel;

import java.util.List;

public class TestAlgorithmModel {


    public static void main(String[] args) {
        new TestAlgorithmModel().test1();
    }

    private void test1() {

        GridSearchModel gridSearchModel = new GridSearchModel(new Parameters());

        List<String> simulations = gridSearchModel.getSimulationName();
        List<String> algorithms =  gridSearchModel.getAlgorithmsName();
        List<String> statistics = gridSearchModel.getStatisticsNames();

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
