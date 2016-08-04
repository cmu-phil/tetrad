package edu.cmu.tetrad.algcomparison.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jdramsey on 8/3/16.
 */
public class ParamDescriptions {
    private static ParamDescriptions instance = new ParamDescriptions();
    private Map<String, ParamDescription> map = new HashMap<>();

    public ParamDescriptions() {
        addParam("numMeasures", new ParamDescription("Number of measured variables", 10));
        addParam("numLatents", new ParamDescription("Number of latent variables", 0));
        addParam("avgDegree", new ParamDescription("Average degree of graph", 2));
        addParam("maxDegree", new ParamDescription("Maximum degree of graph", 100));
        addParam("maxIndegree", new ParamDescription("Maximum indegree of graph", 100));
        addParam("maxOutdegree", new ParamDescription("Maximum outdegree of graph", 100));
        addParam("connected", new ParamDescription("True if graph should be connected", 0));
        addParam("sampleSize", new ParamDescription("Sample size", 1000));
        addParam("numRuns", new ParamDescription("Number of runs", 1));
        addParam("alpha", new ParamDescription("Cutoff for p values (alpha)", 0.001));
        addParam("penaltyDiscount", new ParamDescription("Penalty discount", 4));
        addParam("fgsDepth", new ParamDescription("Maximum number of new colliders", -1));
        addParam("depth", new ParamDescription("Maximum size of conditioning set", -1));
        addParam("coefLow", new ParamDescription("Low end of coefficient range", 0.5));
        addParam("coefHigh", new ParamDescription("High end of coefficient range", 1.5));
        addParam("variance", new ParamDescription("Variance", 1.0));
        addParam("varLow", new ParamDescription("Low end of variance range", 1));
        addParam("varHigh", new ParamDescription("High end of variance range", 3));
        addParam("printWinners", new ParamDescription("True if winning models should be printed", false));
        addParam("printAverages", new ParamDescription("True if averages should be printed", false));
        addParam("printAverageTables", new ParamDescription("True if average tables should be printed", true));
        addParam("printGraphs", new ParamDescription("True if graphs should be printed", false));
        addParam("percentDiscrete", new ParamDescription("Percentage of discrete variables (0 - 100) for mixed data", 50));
        addParam("ofInterestCutoff", new ParamDescription("Cutoff for graphs considered to be of interest", 0.05));
        addParam("numCategories", new ParamDescription("Number of categories", 4));
        addParam("samplePrior", new ParamDescription("Sample prior", 1));
        addParam("structurePrior", new ParamDescription("Structure prior coefficient", 1));
        addParam("mgmParam1", new ParamDescription("MGM tuning parameter #1", 0.1));
        addParam("mgmParam2", new ParamDescription("MGM tuning parameter #2", 0.1));
        addParam("mgmParam3", new ParamDescription("MGM tuning parameter #3", 0.1));
        addParam("scaleFreeAlpha", new ParamDescription("For scale-free graphs, the parameter alpha", 0.05));
        addParam("scaleFreeBeta", new ParamDescription("For scale-free graphs, the parameter beta", 0.05));
        addParam("scaleFreeDeltaIn", new ParamDescription("For scale-free graphs, the parameter delta_in", 3));
        addParam("scaleFreeDeltaOut", new ParamDescription("For scale-free graphs, the parameter delta_out", 3));
        addParam("generalSemFunctionTemplateMeasured", 
                new ParamDescription("General function template for measured variables", "TSUM(NEW(B)*$)"));
        addParam("generalSemFunctionTemplateLatent", 
                new ParamDescription("General function template for latent variables", "TSUM(NEW(B)*$)"));
        addParam("generalSemErrorTemplate", 
                new ParamDescription("General function for error terms", "Beta(2, 5)"));

    }

    public static ParamDescriptions instance() {
        return instance;
    }

    public ParamDescription get(String name) {
        return map.get(name);
    }

    public void addParam(String name, ParamDescription paramDescription) {
        map.put(name, paramDescription);
    }
}
