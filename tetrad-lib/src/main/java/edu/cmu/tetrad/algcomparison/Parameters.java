package edu.cmu.tetrad.algcomparison;

import java.util.*;

/**
 * Stores a list of named parameters with their values. Stores default values for known
 * parameters. Returns a list of parameters with their values, for the parameters whose
 * values have been retrieved, using the toString method.
 * @author jdramsey
 */
public class Parameters {
    private Map<String, Number[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Number> overriddenParameter = new HashMap<>();

    public Parameters() {

        // Defaults
        put("numMeasures", 10);
        put("numEdges", 10);
        put("numLatents", 0);
        put("maxDegree", 10);
        put("maxIndegree", 10);
        put("maxOutdegree", 10);
        put("connected", 0);
        put("sampleSize", 1000);
        put("numRuns", 1);
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

    /**
     * Returns a list of the parameters whoese values were actually used in the course of
     * the simulatoin.
     * @return This list, in String form.
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (String param : usedParameters) {
            builder.append("\n").append(param).append(" = ").append(parameters.get(param)[0]);
        }

        return builder.toString();
    }

    /**
     * Returns the integer values of the given parameter.
     * @param name The name of the parameter.
     * @return The integer value of this parameter.
     */
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

    /**
     * Returns the double values of the given parameter.
     * @param name The name of the parameter.
     * @return The double value of this parameter.
     */
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

    /**
     * Sets the value(s) of the given parameter to a list of values.
     * @param name The name of the parameter.
     * @param n A lsit of values for the parameter.
     */
    public void put(String name, Number...n) {
        parameters.put(name, n);
    }

    /**
     * Returns the number of values for the parameter.
     * @param name The name of the parameter.
     * @return The number of values set for that parameter.
     */
    public int getNumValues(String name) {
        return  parameters.get(name).length;
    }

    /**
     * Returns the values set for the given parameter. Usually of length 1.
     * @param parameter The name of the parameter.
     * @return The array of values.
     */
    public Number[] getValues(String parameter) {
        return parameters.get(parameter);
    }

    /**
     * Sets the given parameter to the given value.
     * @param parameter The name of the parameter.
     * @param value The value of the parameter (a single value).
     */
    public void setValue(String parameter, Number value) {
        parameters.put(parameter, new Number[]{value});
    }

    /**
     * Sets a map of parameters to override the current ones.
     * @param parameters A map from parameter names to values.
     */
    public void setOverriddenParameters(Map<String, Number> parameters) {
        this.overriddenParameter = parameters;
    }
}
