package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.*;

/**
 * Stores a list of named parameters with their values. Stores default values for known
 * parameters. Returns a list of parameters with their values, for the parameters whose
 * values have been retrieved, using the toString method.
 *
 * @author jdramsey
 */
public class Parameters implements TetradSerializable {
    private Map<String, Object[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Object> overriddenParameters = new HashMap<>();

    public Parameters() {
    }

    public Parameters(Parameters parameters) {
        this.parameters = new LinkedHashMap<>(parameters.parameters);
        this.usedParameters = new LinkedHashSet<>(parameters.usedParameters);
        this.overriddenParameters = new HashMap<>(parameters.overriddenParameters);
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
     * @param defaultValue
     * @return The integer value of this parameter.
     */
    public int getInt(String name, int defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return ((Number) o).intValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return ((Number) o).intValue();
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return (Boolean) o;
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return (Boolean) o;
        }
    }

    /**
     * Returns the double values of the given parameter.
     *
     * @param name         The name of the parameter.
     * @param defaultValue
     * @return The double value of this parameter.
     */
    public double getDouble(String name, double defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return ((Number) o).doubleValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return ((Number) o).intValue();
        }
    }

    /**
     * Returns the string values of the given parameter.
     *
     * @param name         The name of the parameter.
     * @param defaultValue
     * @return The double value of this parameter.
     */
    public String getString(String name, String defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return (String) o;
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }
        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return (String) o;
        }
    }

    /**
     * Returns the object for the given parameter.
     *
     * @param name         The name of the parameter.
     * @param defaultValue
     * @return the object value.
     */
    public Object get(String name, Object defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            return overriddenParameters.get(name);
        }

        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            return objects[0];
        }
    }

    /**
     * Returns the values set for the given parameter. Usually of length 1.
     *
     * @param name         The name of the parameter.
     * @param defaultValue
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
            throw new IllegalArgumentException("Expecting a value for parameter '" + parameter + "'");
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
