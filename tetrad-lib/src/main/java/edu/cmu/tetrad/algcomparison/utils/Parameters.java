package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Stores a list of named parameters with their values. Stores default values for known
 * parameters. Returns a list of parameters with their values, for the parameters whose
 * values have been retrieved, using the toString method.
 *
 * @author jdramsey
 */
public class Parameters implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private Map<String, Object[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Object> overriddenParameters = new HashMap<>();

    public Parameters() {
        // Defaults
//        set("numMeasures", 10);
//        set("numLatents", 0);
//        set("avgDegree", 2);
//        set("maxDegree", 100);
//        set("maxIndegree", 100);
//        set("maxOutdegree", 100);
//        set("connected", false);
//        set("sampleSize", 1000);
//        set("numRuns", 1);
//        set("alpha", 0.001);
//        set("penaltyDiscount", 4);
//        set("fgsDepth", -1);
//        set("depth", -1);
//        set("coefLow", 0.5);
//        set("coefHigh", 1.5);
//        set("variance", -1);
//        set("varianceLow", 1.0);
//        set("varianceHigh", 3.0);
//        set("printWinners", 0);
//        set("printAverages", 0);
//        set("printAverageTables", 1);
//        set("printGraph", 0);
//        set("percentDiscrete", 50);
//        set("ofInterestCutoff", 0.05);
//        set("printGraphs", 0);
//        set("numCategories", 4);
//        set("samplePrior", 1);
//        set("structurePrior", 1);
//        set("mgmParam1", 0.1);
//        set("mgmParam2", 0.1);
//        set("mgmParam3", 0.1);
//        set("scaleFreeAlpha", 0.9);
//        set("scaleFreeBeta", 0.05);
//        set("scaleFreeDeltaIn", 3);
//        set("scaleFreeDeltaOut", 3);
//        set("generalSemFunctionTemplateMeasured", "TSUM(NEW(B)*$)");
//        set("generalSemFunctionTemplateLatent", "TSUM(NEW(B)*$)");
//        set("generalSemErrorTemplate", "Beta(2, 5)");
//        set("varLow", 1);
//        set("varHigh", 3);
    }

    public Parameters(Parameters parameters) {
        if (parameters == null) throw new NullPointerException();
        this.parameters = new LinkedHashMap<>(parameters.parameters);
        this.usedParameters = new LinkedHashSet<>(parameters.usedParameters);
        this.overriddenParameters = new HashMap<>(parameters.overriddenParameters);
    }

    public static Parameters serializableInstance() {
        return new Parameters();
    }

    /**
     * Returns a list of the parameters whoese values were actually used in the course of
     * the simulatoin.
     *
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
     *
     * @param name         The name of the parameter.
     * @param defaultValue The value of the parameter, if it has not already been set.
     * @return The integer value of this parameter.
     */
    public int getInt(String name, int defaultValue) {
        return (Integer) get(name, defaultValue);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        return (Boolean) get(name, defaultValue);
    }

    /**
     * Returns the double values of the given parameter.
     *
     * @param name         The name of the parameter.
     * @param defaultValue The value of the parameter, if it has not already been set.
     * @return The double value of this parameter.
     */
    public double getDouble(String name, double defaultValue) {
        return (Double) get(name, defaultValue);
    }

    /**
     * Returns the string values of the given parameter.
     *
     * @param name         The name of the parameter.
     * @param defaultValue The value of the parameter, if it has not already been set.
     * @return The double value of this parameter.
     */
    public String getString(String name, String defaultValue) {
        return (String) get(name, defaultValue);
    }

    /**
     * Returns the object for the given parameter.
     *
     * @param name         The name of the parameter.
     * @param defaultValue The value of the parameter, if it has not already been set.
     * @return the object value.
     */
    public Object get(String name, Object defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            return overriddenParameters.get(name);
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            set(name, defaultValue);
            return defaultValue;
        } else {
            if (getNumValues(name) != 1) {
                throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
            }

            return objects[0];
        }
    }

    /**
     * Returns the values set for the given parameter. Usually of length 1.
     *
     * @param name         The name of the parameter.
     * @param defaultValue The value of the parameter, if it has not already been set.
     * @return The array of values.
     */
    public Object[] getValues(String name, Object[] defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            return (Object[]) overriddenParameters.get(name);
        }

        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            return objects;
        }
    }

    /**
     * Sets the value(s) of the given parameter to a list of strings.
     *
     * @param name The name of the parameter.
     * @param n    A list of values for the parameter.
     */
    public void set(String name, Object... n) {
        parameters.put(name, n);
    }

    /**
     * Sets the value(s) of the given parameter to a list of values.
     *
     * @param name The name of the parameter.
     * @param s    A list of strings for the parameter.
     */
    public void set(String name, String... s) {
        parameters.put(name, s);
    }

    /**
     * Returns the number of values for the parameter.
     *
     * @param parameter The parameter of the parameter.
     * @return The number of values set for that parameter.
     */
    public int getNumValues(String parameter) {
        Object[] objects = parameters.get(parameter);
        if (objects == null) {
            return 0;
        }
        return objects.length;
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param parameter The name of the parameter.
     * @param value     The value of the parameter (a single value).
     */
    public void set(String parameter, Object value) {
        parameters.put(parameter, new Object[]{value});
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param parameter The name of the parameter.
     * @param value     The value of the parameter (a single value).
     */
    public void set(String parameter, String value) {
        parameters.put(parameter, new String[]{value});
    }

    /**
     * Sets a map of parameters to override the current ones.
     *
     * @param parameters A map from parameter names to values.
     */
    public void setOverriddenParameters(Map<String, Object> parameters) {
        this.overriddenParameters = parameters;
    }

    public Map<String, Object[]> getParameters() {
        return parameters;
    }

    public Set<String> getUsedParameters() {
        return usedParameters;
    }
}
