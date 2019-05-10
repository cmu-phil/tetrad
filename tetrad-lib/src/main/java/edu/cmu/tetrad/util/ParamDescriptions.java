package edu.cmu.tetrad.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores descriptions of the parameters for the simulation box. All parameters
 * that go into the interface need to be described here.
 *
 * @author jdramsey
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public final class ParamDescriptions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParamDescriptions.class);

    private static final ParamDescriptions INSTANCE = new ParamDescriptions();

    private final Map<String, ParamDescription> map = new TreeMap<>();

    private List<String> paramsMissingValueType = new ArrayList<>();

    private ParamDescriptions() {
        Document doc = null;

        // Read the copied maunal/index.html from within the jar
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("manual/index.html")) {
            if (inputStream != null) {
                doc = Jsoup.parse(inputStream, "UTF-8", "");
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to read tetrad HTML manual 'maunal/index.html' file from within the jar.", ex);
        }

        // Get the description of each parameter
        if (doc != null) {
            Set<String> allParams = Params.getParameters();

            for (String paramName : allParams) {
                String valueType = doc.getElementById(paramName + "_value_type").text();

                // Add params that don't have value types for spalsh screen error
                if (valueType.equals("")) {
                    paramsMissingValueType.add(paramName);
                } else {
                    String shortDescription = doc.getElementById(paramName + "_short_desc").text();
                    String longDescription = doc.getElementById(paramName + "_long_desc").text();
                    String defaultValue = doc.getElementById(paramName + "_default_value").text();
                    String lowerBound = doc.getElementById(paramName + "_lower_bound").text();
                    String upperBound = doc.getElementById(paramName + "_upper_bound").text();

                    if (shortDescription.equals("")) {
                        shortDescription = String.format("Missing short description for %s", paramName);
                    }

                    if (longDescription.equals("")) {
                        longDescription = String.format("Missing long description for %s", paramName);
                    }

                    ParamDescription paramDescription = null;

                    if (valueType.equalsIgnoreCase("Integer")) {
                        int defaultValueInt = Integer.parseInt(defaultValue);
                        int lowerBoundInt = Integer.parseInt(lowerBound);
                        int upperBoundInt = Integer.parseInt(upperBound);

                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueInt, lowerBoundInt, upperBoundInt);
                    } else if (valueType.equalsIgnoreCase("Double")) {
                        double defaultValueDouble = Double.parseDouble(defaultValue);
                        double lowerBoundDouble = Double.parseDouble(lowerBound);
                        double upperBoundDouble = Double.parseDouble(upperBound);

                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueDouble, lowerBoundDouble, upperBoundDouble);
                    } else if (valueType.equalsIgnoreCase("Boolean")) {
                        boolean defaultValueBoolean = defaultValue.equalsIgnoreCase("true");
                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueBoolean);
                    } else if (valueType.equalsIgnoreCase("String")) {
                        String defaultValueString = defaultValue;
                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueString);
                    } else {
                        Object defaultValueObject = (Object) defaultValue;
                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueObject);
                    }

                    map.put(paramName, paramDescription);
                }
            }
        }

        // add parameters not in documentation
        map.put(Params.PRINT_STREAM, new ParamDescription(Params.PRINT_STREAM, "printStream", "A writer to print output.", System.out));
    }

    public static ParamDescriptions getInstance() {
        return INSTANCE;
    }

    public ParamDescription get(String name) {
        return map.get(name);
    }

    public void put(String name, ParamDescription paramDescription) {
        map.put(name, paramDescription);
    }

    public Set<String> getNames() {
        return map.keySet();
    }

    public List<String> getParamsMissingValueType() {
        return paramsMissingValueType;
    }

}
