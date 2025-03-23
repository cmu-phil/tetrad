package edu.cmu.tetrad.util;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stores a list of named parameters with their values. Stores default values for known parameters. Returns a list of
 * parameters with their values, for the parameters whose values have been retrieved, using the toString method.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Parameters implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The parameters.
     */
    private Map<String, Object[]> parameters = new LinkedHashMap<>();

    /**
     * The used parameters.
     */
    private Set<String> usedParameters = new LinkedHashSet<>();

    /**
     * The overridden parameters.
     */
    private Map<String, Object> overriddenParameters = new HashMap<>();

    /**
     * <p>Constructor for Parameters.</p>
     */
    public Parameters() {
    }

    /**
     * <p>Constructor for Parameters.</p>
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters(Parameters parameters) {
        if (parameters == null) {
            throw new NullPointerException();
        }
        this.parameters = new LinkedHashMap<>(parameters.parameters);
        this.usedParameters = new LinkedHashSet<>(parameters.usedParameters);
        this.overriddenParameters = new HashMap<>(parameters.overriddenParameters);
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public static Parameters serializableInstance() {
        return new Parameters();
    }

    // Includes all of the given parameters setting with the current parameter settings.

    /**
     * <p>putAll.</p>
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public void putAll(Parameters parameters) {
        if (parameters == null) {
            throw new NullPointerException();
        }
        this.parameters.putAll(parameters.parameters);
        this.usedParameters.addAll(parameters.usedParameters);
        this.overriddenParameters.putAll(parameters.overriddenParameters);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a list of the parameters whose values were actually used in the course of the simulation.
     */
    @Override
    public String toString() {
        return this.usedParameters.stream()
                .map(e -> String.format("%s = %s", e, this.parameters.get(e)[0]))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Returns the integer value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The integer value of this parameter.
     */
    public int getInt(String name) {
        return ((Number) get(name, ParamDescriptions.getInstance().get(name).getDefaultValue())).intValue();
    }

    /**
     * Returns the long value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The long value of this parameter.
     */
    public long getLong(String name) {
        return ((Number) get(name, ParamDescriptions.getInstance().get(name).getDefaultValue())).longValue();
    }

    /**
     * Returns the boolean value of the given parameter, looking up its default in the ParamDescriptions map.
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
     * Returns the double value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name The name or the parameter.
     * @return The double value of this parameter.
     */
    public double getDouble(String name) {
        return ((Number) get(name, ParamDescriptions.getInstance().get(name).getDefaultValue())).doubleValue();
    }

    /**
     * Returns the string value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name The name or the parameter.
     * @return The string value of this parameter.
     */
    public String getString(String name) {
        return String.valueOf(get(name, ParamDescriptions.getInstance().get(name).getDefaultValue()));
    }

    /**
     * Returns the object value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name The name of the parameter.
     * @return The object value of this parameter.
     */
    public Object get(String name) {
        return get(name, ParamDescriptions.getInstance().get(name).getDefaultValue());
    }

    /**
     * Returns the integer value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name         The name of the parameter.
     * @param defaultValue a int
     * @return The integer value of this parameter.
     */
    public int getInt(String name, int defaultValue) {
        return ((Number) get(name, defaultValue)).intValue();
    }

    /**
     * Returns the long value of the given parameter, looking up its default in the ParamDescriptions map.
     *
     * @param name         The name of the parameter.
     * @param defaultValue a long
     * @return The long value of this parameter.
     */
    public long getLong(String name, long defaultValue) {
        return ((Number) get(name, defaultValue)).longValue();
    }

    /**
     * Returns the boolean value of the given parameter, using the given default.
     *
     * @param name         The name of the parameter.
     * @param defaultValue a boolean
     * @return The boolean value of this parameter.
     */
    public boolean getBoolean(String name, boolean defaultValue) {
        Object b = get(name, defaultValue);

        if (!(b instanceof Boolean)) {
            return false;
        }

        return (Boolean) b;
    }

    /**
     * Returns the object value of the given parameter, using the given default.
     *
     * @param name         The name of the parameter.
     * @param defaultValue a double
     * @return The double value of this parameter.
     */
    public double getDouble(String name, double defaultValue) {
        return ((Number) get(name, defaultValue)).doubleValue();
    }

    /**
     * Returns the string value of the given parameter, using the given default.
     *
     * @param name         The name of the parameter.
     * @param defaultValue a {@link java.lang.String} object
     * @return The string value of this parameter.
     */
    public String getString(String name, String defaultValue) {
        return String.valueOf(get(name, defaultValue));
    }

    /**
     * Returns the object value of the given parameter, using the given default.
     *
     * @param name         The name of the parameter.
     * @param defaultValue a {@link java.lang.Object} object
     * @return The object value of this parameter.
     */
    public Object get(String name, Object defaultValue) {
        if (this.overriddenParameters.containsKey(name)) {
            return this.overriddenParameters.get(name);
        }

        this.usedParameters.add(name);
        Object[] objects = this.parameters.get(name);

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
        if (this.overriddenParameters.containsKey(name)) {
            return (Object[]) this.overriddenParameters.get(name);
        }

        Object[] objects = this.parameters.get(name);

        if (objects == null) {
            ParamDescription paramDescription = ParamDescriptions.getInstance().get(name);
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

        // Check if the values are serializable, so that a Parameters object can be saved as
        // a field.
        for (Object o : n) {
            if (!(o instanceof Serializable || o instanceof PrintStream)) {
                throw new IllegalArgumentException("Parameter '" + name + "' is being set to an array containing a non-serizable value.");
            }
        }

        this.parameters.put(name, n);
    }

    /**
     * Sets the value(s) of the given parameter to a list of values.
     *
     * @param name The name of the parameter.
     * @param s    A list of strings for the parameter.
     */
    public void set(String name, String... s) {
        this.parameters.put(name, s);
    }

    /**
     * Returns the number of values for the parameter.
     *
     * @param name The name of the parameter.
     * @return The number of values set for that parameter.
     */
    public int getNumValues(String name) {
        Object[] objects = this.parameters.get(name);
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
        if (value == null) {
            return;
        }

        // Check if the values are serializable, so that a Parameters object can be saved as
        // a field.
        if (!(value instanceof Serializable || value instanceof PrintStream)) {
            throw new IllegalArgumentException("Parameter '" + name + "' is being assigned a value that is not serializable.");
        }

        this.parameters.put(name, new Object[]{value});
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param name  The name of the parameter.
     * @param value The value of the parameter (a single value).
     */
    public void set(String name, String value) {
        this.parameters.put(name, new String[]{value});
    }

    /**
     * <p>getParametersNames.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<String> getParametersNames() {
        return this.parameters.keySet();
    }

    /**
     * Removes the specified parameter from the list of parameters.
     *
     * @param parameter The parameter to remove.
     */
    public void remove(String parameter) {
        parameters.remove(parameter);
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in. defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}
