package edu.cmu.tetrad.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores descriptions of the parameters for the simulation box. All parameters
 * that go into the interface need to be described here.
 *
 * @author jdramsey
 * @author Zhou Yuan <zhy19@pitt.edu>
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class ParamDescriptions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParamDescriptions.class);

    private static final ParamDescriptions INSTANCE = new ParamDescriptions();

    private final Map<String, ParamDescription> map = new TreeMap<>();

    private List<String> paramsWithUnsupportedValueType = new ArrayList<>();

    private ParamDescriptions() {
        Document doc = null;

        // Currently supported parameter value types
        // In HTML manual, must use one of the following types for parameter descriptions
        final String VALUE_TYPE_STRING = "String";
        final String VALUE_TYPE_INTEGER = "Integer";
        final String VALUE_TYPE_DOUBLE = "Double";
        final String VALUE_TYPE_BOOLEAN = "Boolean";

        final Set<String> PARAM_VALUE_TYPES = new HashSet<>(Arrays.asList(
                VALUE_TYPE_STRING,
                VALUE_TYPE_INTEGER,
                VALUE_TYPE_DOUBLE,
                VALUE_TYPE_BOOLEAN
        ));

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
            Elements elements = doc.getElementsByClass("parameter_description");

            for (Element element : elements) {
                String paramName = element.id();
                String valueType = doc.getElementById(paramName + "_value_type").text().trim();

                // Add params that don't have value types for spalsh screen error
                if (!PARAM_VALUE_TYPES.contains(valueType)) {
                    paramsWithUnsupportedValueType.add(paramName);
                } else {
                    String shortDescription = doc.getElementById(paramName + "_short_desc").text().trim();
                    String longDescription = doc.getElementById(paramName + "_long_desc").text().trim();
                    String defaultValue = doc.getElementById(paramName + "_default_value").text().trim();
                    String lowerBound = doc.getElementById(paramName + "_lower_bound").text().trim();
                    String upperBound = doc.getElementById(paramName + "_upper_bound").text().trim();

                    if (shortDescription.equals("")) {
                        shortDescription = String.format("Missing short description for %s", paramName);
                    }

                    if (longDescription.equals("")) {
                        longDescription = String.format("Missing long description for %s", paramName);
                    }

                    if (!valueType.equals(VALUE_TYPE_STRING) && defaultValue.equals("")) {
                        System.out.println("Invalid default value of parameter: " + paramName);
                    }

                    ParamDescription paramDescription = null;

                    if (valueType.equalsIgnoreCase(VALUE_TYPE_INTEGER)) {
                        Integer defaultValueInt = Integer.parseInt(defaultValue);
                        int lowerBoundInt = Integer.parseInt(lowerBound);
                        int upperBoundInt = Integer.parseInt(upperBound);

                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueInt, lowerBoundInt, upperBoundInt);
                    } else if (valueType.equalsIgnoreCase(VALUE_TYPE_DOUBLE)) {
                        Double defaultValueDouble = Double.parseDouble(defaultValue);
                        double lowerBoundDouble = Double.parseDouble(lowerBound);
                        double upperBoundDouble = Double.parseDouble(upperBound);

                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueDouble, lowerBoundDouble, upperBoundDouble);
                    } else if (valueType.equalsIgnoreCase(VALUE_TYPE_BOOLEAN)) {
                        Boolean defaultValueBoolean = defaultValue.equalsIgnoreCase("true");
                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueBoolean);
                    } else if (valueType.equalsIgnoreCase(VALUE_TYPE_STRING)) {
                        String defaultValueString = defaultValue;
                        paramDescription = new ParamDescription(paramName, shortDescription, longDescription, defaultValueString);
                    }

                    map.put(paramName, paramDescription);
                }
            }
        }

        // add parameters not in documentation
        map.put(Params.PRINT_STREAM, new ParamDescription(Params.PRINT_STREAM, "printStream", "A writer to print output messages.", ""));
    }

    public static ParamDescriptions getInstance() {
        return INSTANCE;
    }

    public ParamDescription get(String name) {
        ParamDescription paramDesc = map.get(name);

        return (paramDesc == null)
                ? new ParamDescription(name, String.format("Please add a description for %s to the manual.", name), "", 0)
                : paramDesc;
    }

    public void put(String name, ParamDescription paramDescription) {
        map.put(name, paramDescription);
    }

    public Set<String> getNames() {
        return map.keySet();
    }

    public List<String> getParamsWithUnsupportedValueType() {
        return paramsWithUnsupportedValueType;
    }

}
