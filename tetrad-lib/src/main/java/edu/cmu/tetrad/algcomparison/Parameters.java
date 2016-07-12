package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;

import java.util.*;

/**
 * Stores a list of named parameters with their values. Stores default values for known
 * parameters. Returns a list of parameters with their values, for the parameters whose
 * values have been retrieved, using the toString method.
 * @author Joseph Ramsey
 */
public class Parameters {
    private Map<String, Number[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Number> overriddenParameter = new HashMap<>();

    public Parameters() {

        // Defaults
        put("numMeasures", 100);
        put("numEdges", 100);
        put("numLatents", 0);
        put("maxDegree", 10);
        put("maxIndegree", 10);
        put("maxOutdegree", 10);
        put("connected", 0);
        put("sampleSize", 1000);
        put("numRuns", 5);
        put("alpha", 0.001);
        put("penaltyDiscount", 4);
        put("fgsDepth", -1);
        put("depth", -1);
        put("printWinners", 0);
        put("printAverages", 0);
        put("printAverageTables", 1);
        put("printGraph", 0);
        put("percentDiscreteForMixedSimulation", 50);
        put("ofInterestCutoff", 0.05);
        put("printGraphs", 0);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (String param : usedParameters) {
            builder.append("\n").append(param).append(" = ").append(parameters.get(param));
        }

        return builder.toString();
    }

    public int getInt(String name) {
        if (overriddenParameter.containsKey(name)) {
            return overriddenParameter.get(name).intValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }
        usedParameters.add(name);
        return parameters.get(name)[0].intValue();
    }

    public double getDouble(String name) {
        if (overriddenParameter.containsKey(name)) {
            return overriddenParameter.get(name).doubleValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }
        usedParameters.add(name);
        return parameters.get(name)[0].doubleValue();
    }

    public void put(String name, Number...n) {
        parameters.put(name, n);
    }

    public int getNumValues(String name) {
        return  parameters.get(name).length;
    }

    public Number[] getValues(String parameter) {
        return parameters.get(parameter);
    }

    public void setValue(String p, Number value) {
        parameters.put(p, new Number[]{value});
    }

    public void setOverriddenParameters(Map<String, Number> parameters) {
        this.overriddenParameter = parameters;
    }
}
