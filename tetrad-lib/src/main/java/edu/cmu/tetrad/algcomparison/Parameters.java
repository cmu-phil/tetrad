package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.sem.Parameter;

import java.util.*;

/**
 * Created by jdramsey on 7/2/16.
 */
public class Parameters {
    private Map<String, Number> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();

    public Parameters() {

        // Defaults
        parameters.put("numMeasures", 100);
        parameters.put("numEdges", 100);
        parameters.put("numLatents", 0);
        parameters.put("maxDegree", 10);
        parameters.put("maxIndegree", 10);
        parameters.put("maxOutdegree", 10);
        parameters.put("connected", 0);
        parameters.put("sampleSize", 1000);
        parameters.put("numRuns", 5);
        parameters.put("alpha", 0.001);
        parameters.put("penaltyDiscount", 4);
        parameters.put("fgsDepth", -1);
        parameters.put("depth", -1);
        parameters.put("printWinners", 0);
        parameters.put("printAverages", 0);
        parameters.put("printAverageTables", 1);
        parameters.put("printGraph", 0);
        parameters.put("percentDiscreteForMixedSimulation", 50);
        parameters.put("ofInterestCutoff", 0.05);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (String param : usedParameters) {
            builder.append("\n" + param + " = " + parameters.get(param));
        }

        return builder.toString();
    }

    public int getInt(String name) {
        return parameters.get(name).intValue();
    }

    public double getDouble(String name) {
        return parameters.get(name).doubleValue();
    }

    public void putInt(String name, int i) {
        parameters.put(name, i);
        usedParameters.add(name);
    }

    public void putDouble(String name, double d) {
        parameters.put(name, d);
        usedParameters.add(name);
    }
}
