package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.BuildPureClusters;
import edu.cmu.tetrad.search.FindOneFactorClusters;
import edu.cmu.tetrad.search.MimUtils;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.sem.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.*;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousNonlinearNongaussianSimulation implements Simulation {
    private Graph graph;
    private DataSet dataSet;

    public ContinuousNonlinearNongaussianSimulation() {
    }

    public void simulate(Map<String, Number> parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.get("numMeasures").intValue(),
                parameters.get("numLatents").intValue(),
                parameters.get("numEdges").intValue(),
                parameters.get("maxDegree").intValue(),
                parameters.get("maxIndegree").intValue(),
                parameters.get("maxOutdegree").intValue(),
                parameters.get("connected").intValue() == 1);
        GeneralizedSemPm pm = getPm(graph);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        this.dataSet = im.simulateData(parameters.get("sampleSize").intValue(), false);
    }

    public Graph getDag() {
        return graph;
    }

    public DataSet getData() {
        return dataSet;
    }

    public String toString() {
        return "Nonlinear, non-Gaussian SEM simulation";
    }

    private GeneralizedSemPm getPm(Graph graph) {

        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        List<Node> errorNodes = pm.getErrorNodes();

        Map<String, String> paramMap = new HashMap<String, String>();
        String[] funcs = {"TSUM(NEW(B)*$)", "TSUM(NEW(B)*$+NEW(C)*sin(NEW(T)*$+NEW(A)))",
                "TSUM(NEW(B)*(.5*$ + .5*(sqrt(abs(NEW(b)*$+NEW(exoErrorType))) ) ) )"};
        paramMap.put("s", "U(1,3)");
        paramMap.put("B", "Split(-1.5,-.5,.5,1.5)");
        paramMap.put("C", "Split(-1.5,-.5,.5,1.5)");
        paramMap.put("T", "U(.5,1.5)");
        paramMap.put("A", "U(0,.25)");
        paramMap.put("exoErrorType", "U(-.5,.5)");
        paramMap.put("funcType", "U(1,5)");

        String nonlinearStructuralEdgesFunction = funcs[0];
        String nonlinearFactorMeasureEdgesFunction = funcs[0];

        try {
            for (Node node : variablesNodes) {
                if (node.getNodeType() == NodeType.LATENT) {
                    String _template = TemplateExpander.getInstance().expandTemplate(
                            nonlinearStructuralEdgesFunction, pm, node);
                    pm.setNodeExpression(node, _template);
                }
                else {
                    String _template = TemplateExpander.getInstance().expandTemplate(
                            nonlinearFactorMeasureEdgesFunction, pm, node);
                    pm.setNodeExpression(node, _template);
                }
            }

            for (Node node : errorNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate("U(-.5,.5)", pm, node);
                pm.setNodeExpression(node, _template);
            }

            Set<String> parameters = pm.getParameters();

            for (String parameter : parameters) {
                for (String type : paramMap.keySet()) {
                    if (parameter.startsWith(type)) {
                        pm.setParameterExpression(parameter, paramMap.get(type));
                    }
                }
            }
        } catch (ParseException e) {
            System.out.println(e);
        }

        return pm;
    }

    private Graph getMim(Graph structuralGraph, int clusterSize, int numClusters) {

        //        addImpurities(numClusters, clusterSize, impuritiesOption, graph);
        return DataGraphUtils.randomMim(structuralGraph, clusterSize, 0, 0, 0, true);
    }

    private Graph getStructuralGraph(int numClusters) {
        return new Dag(GraphUtils.randomGraph(numClusters, 0, numClusters, 30, 15, 15, false));
    }


    public static void main(String[] args) {
//        new ExploreKummerfeldRamseyTetradPaper().loop1c();
//        new ExploreKummerfeldRamseyTetradPaper2().testOMSSpecial();
//        new ExploreKummerfeldRamseyTetradPaper().testOMS();
    }

    public boolean isContinuous() {
        return true;
    }
}
