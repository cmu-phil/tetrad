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
    static final long serialVersionUID = 23L;

    private Map<String, Object[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Object> overriddenParameters = new HashMap<>();

    public Parameters() {
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
            ParamDescription paramDescription = ParamDescriptions.instance().get(param);
            builder.append("\n").append(paramDescription.getDescription()).append(" = ").append(parameters.get(param)[0]);
        }

        return builder.toString();
    }

    /**
     * Returns the integer values of the given parameter.
     *
     * @param name The name parameter.
     * @return The integer value of this parameter.
     */
    public int getInt(String name) {
        return (Integer) get(name);
    }

    public boolean getBoolean(String parameter) {
        return (Boolean) get(parameter);
    }

    /**
     * Returns the double values of the given parameter.
     *
     * @param name The name of the parameter.
     * @return The double value of this parameter.
     */
    public double getDouble(String name) {
        return (Double) get(name);
    }

    /**
     * Returns the string values of the given parameter.
     *
     * @param parameter The parameter.
     * @return The double value of this parameter.
     */
    public String getString(String parameter) {
        return (String) get(parameter);
    }

    /**
     * Returns the object for the given parameter.
     *
     * @param name The name of the parameter.
     * @return the object value.
     */
    public Object get(String name) {
        if (overriddenParameters.containsKey(name)) {
            return overriddenParameters.get(name);
        }

        ParamDescription paramDescription = ParamDescriptions.instance().get(name);

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            set(name, paramDescription.getDefaultValue());
            return paramDescription.getDefaultValue();
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
     * @param name The name of the parameter.
     * @return The array of values.
     */
    public Object[] getValues(String name) {
        if (overriddenParameters.containsKey(name)) {
            return (Object[]) overriddenParameters.get(name);
        }

        Object[] objects = parameters.get(name);

        if (objects == null) {
            ParamDescription paramDescription = ParamDescriptions.instance().get(name);
            if (paramDescription == null) {
                throw new IllegalArgumentException("A description of '" + name + "' has " +
                        "not been given in ParamDescriptions.");
            }
            return new Object[]{paramDescription.getDefaultValue()};
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
     * @param name The name of the parameter.
     * @return The number of values set for that parameter.
     */
    public int getNumValues(String name) {
        Object[] objects = parameters.get(name);
        if (objects == null) {
            return 0;
        }
        return objects.length;
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param name  The name of the parameter.
     * @param value The value of the parameter (a single value).
     */
    public void set(String name, Object value) {
        parameters.put(name, new Object[]{value});
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param name  The name of the parameter.
     * @param value The value of the parameter (a single value).
     */
    public void set(String name, String value) {
        parameters.put(name, new String[]{value});
    }

    public Map<String, Object[]> getParameters() {
        return parameters;
    }
}
