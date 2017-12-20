package edu.cmu.tetrad.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stores a list of named parameters with their values. Stores default values
 * for known parameters. Returns a list of parameters with their values, for the
 * parameters whose values have been retrieved, using the toString method.
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
        if (parameters == null) {
            throw new NullPointerException();
        }
        this.parameters = new LinkedHashMap<>(parameters.parameters);
        this.usedParameters = new LinkedHashSet<>(parameters.usedParameters);
        this.overriddenParameters = new HashMap<>(parameters.overriddenParameters);
    }

    public static Parameters serializableInstance() {
        return new Parameters();
    }

    // Includes all of the given parameters setting with the current parameter settings.
    public void putAll(Parameters parameters) {
        if (parameters == null) {
            throw new NullPointerException();
        }
        this.parameters.putAll(parameters.parameters);
        this.usedParameters.addAll(parameters.usedParameters);
        this.overriddenParameters.putAll(parameters.overriddenParameters);
    }

    /**
     * Returns a list of the parameters whoese values were actually used in the
     * course of the simulation.
     *
     * @return This list, in String form.
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (String param : usedParameters) {
//            ParamDescription paramDescription = ParamDescriptions.getInstance().get(param);
//            builder.append("\n").append(paramDescription.getDescription()).append(" = ").append(parameters.get(param)[0]);
            builder.append("\n").append(param).append(" = ").append(parameters.get(param)[0]);
        }

        return builder.toString();
    }

    /**
     * Returns the integer value of the given parameter, looking up its default
     * in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The integer value of this parameter.
     */
    public int getInt(String name) {
        return ((Number) get(name, ParamDescriptions.getInstance().get(name).getDefaultValue())).intValue();
    }

    /**
     * Returns the boolean value of the given parameter, looking up its default
     * in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The boolean value of this parameter.
     */
    public boolean getBoolean(String name) {
        try {
            return (Boolean) get(name, ParamDescriptions.getInstance().get(name).getDefaultValue());
        } catch (Exception e) {
            throw new RuntimeException("ERROR: Parameter " + name + " was not actually boolean.");
        }
    }

    /**
     * Returns the double value of the given parameter, looking up its default
     * in the ParamDescriptions map.
     *
     * @param name The name or the parameter.
     * @return The double value of this parameter.
     */
    public double getDouble(String name) {
        return ((Number) get(name, ParamDescriptions.getInstance().get(name).getDefaultValue())).doubleValue();
    }

    /**
     * Returns the string value of the given parameter, looking up its default
     * in the ParamDescriptions map.
     *
     * @param name The name or the parameter.
     * @return The string value of this parameter.
     */
    public String getString(String name) {
        return String.valueOf(get(name, ParamDescriptions.getInstance().get(name).getDefaultValue()));
    }

    /**
     * Returns the object value of the given parameter, looking up its default
     * in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The object value of this parameter.
     */
    public Object get(String name) {
        return get(name, ParamDescriptions.getInstance().get(name).getDefaultValue());
    }

    /**
     * Returns the integer value of the given parameter, looking up its default
     * in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The integer value of this parameter.
     */
    public int getInt(String name, int defaultValue) {
        return ((Number) get(name, defaultValue)).intValue();
    }

    /**
     * Returns the boolean value of the given parameter, using the given
     * default.
     *
     * @param name The name of the parameter.
     * @return The boolean value of this parameter.
     */
    public boolean getBoolean(String name, boolean defaultValue) {
        Object b = get(name, defaultValue);

        if (b == null || !(b instanceof Boolean)) {
            return false;
        }

        return (Boolean) b;
    }

    /**
     * Returns the object value of the given parameter, using the given default.
     *
     * @param name The name of the parameter.
     * @return The double value of this parameter.
     */
    public double getDouble(String name, double defaultValue) {
        return ((Number) get(name, defaultValue)).doubleValue();
    }

    /**
     * Returns the string value of the given parameter, using the given default.
     *
     * @param name The name of the parameter.
     * @return The string value of this parameter.
     */
    public String getString(String name, String defaultValue) {
        return String.valueOf(get(name, defaultValue));
    }

    /**
     * Returns the object value of the given parameter, using the given default.
     *
     * @param name The name of the parameter.
     * @return The object value of this parameter.
     */
    public Object get(String name, Object defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            return overriddenParameters.get(name);
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
//            if (defaultValue != null) {
            set(name, defaultValue);
            return defaultValue;
//            } else {
//                throw new IllegalArgumentException("Parameter '" + name + "' has no default value.");
//            }
        } else {
            if (getNumValues(name) != 1) {
                System.out.println("ERROR. Parameter '" + name + "' was not listed among the algorithm parameters "
                        + "for this algorithm. Skipping this run.\n");
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
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
            if (paramDescription == null) {
                throw new IllegalArgumentException("A description of '" + name + "' has "
                        + "not been given in ParamDescriptions.");
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
     * @param n A list of values for the parameter.
     */
    public void set(String name, Object... n) {
        parameters.put(name, n);
    }

    /**
     * Sets the value(s) of the given parameter to a list of values.
     *
     * @param name The name of the parameter.
     * @param s A list of strings for the parameter.
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
     * @param name The name of the parameter.
     * @param value The value of the parameter (a single value).
     */
    public void set(String name, Object value) {
        if (value == null) {
            return;
//            throw new IllegalArgumentException("Parameter '" + name + "' has no default value.");
        }

        parameters.put(name, new Object[]{value});
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param name The name of the parameter.
     * @param value The value of the parameter (a single value).
     */
    public void set(String name, String value) {
        parameters.put(name, new String[]{value});
    }

    public Set<String> getParametersNames() {
        return parameters.keySet();
    }
}
