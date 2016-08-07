package edu.cmu.tetrad.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores descriptions of the parameters for the simulation box. All parameters that go
 * into the interface need to be described here.
 * @author jdramsey
 */
public class ParamDescriptions {
    private static ParamDescriptions instance = new ParamDescriptions();
    private Map<String, ParamDescription> map = new HashMap<>();

    public ParamDescriptions() {
        put("numMeasures", new ParamDescription("Number of measured variables", 10));
        put("numLatents", new ParamDescription("Number of latent variables", 0));
        put("avgDegree", new ParamDescription("Average degree of graph", 2));
        put("maxDegree", new ParamDescription("Maximum degree of graph", 100));
        put("maxIndegree", new ParamDescription("Maximum indegree of graph", 100));
        put("maxOutdegree", new ParamDescription("Maximum outdegree of graph", 100));
        put("connected", new ParamDescription("Yes if graph should be connected", false));
        put("sampleSize", new ParamDescription("Sample size", 1000));
        put("numRuns", new ParamDescription("Number of runs", 1));
        put("alpha", new ParamDescription("Cutoff for p values (alpha)", 0.001));
        put("penaltyDiscount", new ParamDescription("Penalty discount", 4));
        put("fgsDepth", new ParamDescription("Maximum number of new colliders", -1));
        put("depth", new ParamDescription("Maximum size of conditioning set", -1));
        put("coefLow", new ParamDescription("Low end of coefficient range", 0.5));
        put("coefHigh", new ParamDescription("High end of coefficient range", 1.5));
        put("covLow", new ParamDescription("Low end of coefficient range", 0.5));
        put("covHigh", new ParamDescription("High end of coefficient range", 1.5));
        put("varLow", new ParamDescription("Low end of variance range", 1));
        put("varHigh", new ParamDescription("High end of variance range", 3));
        put("printWinners", new ParamDescription("Yes if winning models should be printed", false));
        put("printAverages", new ParamDescription("Yes if averages should be printed", false));
        put("printAverageTables", new ParamDescription("Yes if average tables should be printed", true));
        put("printGraphs", new ParamDescription("Yes if graphs should be printed", false));
        put("dataType", new ParamDescription("Categorical or discrete", "categorical"));
        put("percentDiscrete", new ParamDescription("Percentage of discrete variables (0 - 100) for mixed data", 50));
        put("ofInterestCutoff", new ParamDescription("Cutoff for graphs considered to be of interest", 0.05));
        put("numCategories", new ParamDescription("Number of categories", 4));
        put("minCategories", new ParamDescription("Minimum number of categories", 2));
        put("maxCategories", new ParamDescription("Maximum number of categories", 2));
        put("samplePrior", new ParamDescription("Sample prior", 1));
        put("structurePrior", new ParamDescription("Structure prior coefficient", 1));
        put("mgmParam1", new ParamDescription("MGM tuning parameter #1", 0.1));
        put("mgmParam2", new ParamDescription("MGM tuning parameter #2", 0.1));
        put("mgmParam3", new ParamDescription("MGM tuning parameter #3", 0.1));
        put("scaleFreeAlpha", new ParamDescription("For scale-free graphs, the parameter alpha", 0.05));
        put("scaleFreeBeta", new ParamDescription("For scale-free graphs, the parameter beta", 0.9));
        put("scaleFreeDeltaIn", new ParamDescription("For scale-free graphs, the parameter      delta_in", 3));
        put("scaleFreeDeltaOut", new ParamDescription("For scale-free graphs, the parameter delta_out", 3));
        put("generalSemFunctionTemplateMeasured",
                new ParamDescription("General function template for measured variables", "TSUM(NEW(B)*$)"));
        put("generalSemFunctionTemplateLatent",
                new ParamDescription("General function template for latent variables", "TSUM(NEW(B)*$)"));
        put("generalSemErrorTemplate",
                new ParamDescription("General function for error terms", "Beta(2, 5)"));
        put("coefSymmetric", new ParamDescription("Yes if negative coefficient " +
                        "values should be considered", true));
        put("covSymmetric", new ParamDescription("Yes if negative covariance " +
                        "values should be considered", true));
        put("retainPreviousValues", new ParamDescription("Retain previous values", false));

        // Boolean Glass parameters.
        put("lagGraphVarsPerInd", new ParamDescription("Number of variables per individual", 5));
        put("lagGraphMlag", new ParamDescription("Maximum lag of graph", 1));
        put("lagGraphIndegree", new ParamDescription("Indegree of graph", 2));
        put("numDishes", new ParamDescription("Number of dishes", 1));
        put("numCellsPerDish",new ParamDescription("Number of cells per dish", 10000));
        put("stepsGenerated",new ParamDescription("Number of steps generated", 4));
        put("firstStepStored",new ParamDescription("The index of the first step stored", 1));
        put("interval",new ParamDescription("The interval between steps stored", 1));
        put("rawDataSaved",new ParamDescription("Yes if the raw data should be saved (otherwise tossed)", false));
        put("measuredDataSaved",new ParamDescription("Yes if the measured data should be saved (otherwise tossed)", true));
        put("initSync",new ParamDescription("Yes if cells should be initialized synchronously, otherwise randomly", true));
        put("antilogCalculated",new ParamDescription("Yes if antilogs of data should be calculated", false));
        put("dishDishVariability",new ParamDescription("Dish to dish variability", 10.0));
        put("numChipsPerDish",new ParamDescription("Number of chips per dish", 4));
        put("sampleSampleVariability",new ParamDescription("Sample to sample variability", 0.025));
        put("chipChipVariability",new ParamDescription("Chip to chip variability", 0.1));
        put("pixelDigitalization",new ParamDescription("Pixel digitalization", 0.025));
        put("includeDishAndChipColumns",new ParamDescription("Yes if Dish and Chip columns should be included in output", true));

    }

    public static ParamDescriptions instance() {
        return instance;
    }

    public ParamDescription get(String name) {
        if (!map.containsKey(name)) {
            return new ParamDescription("Please add a description to ParamDescriptions for " + name, 0);
//            throw new IllegalArgumentException("Expecting a default value for " + name);
        }

        return map.get(name);
    }

    public void put(String name, ParamDescription paramDescription) {
        map.put(name, paramDescription);
    }
}
