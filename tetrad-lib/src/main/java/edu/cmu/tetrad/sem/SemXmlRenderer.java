package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import nu.xom.Attribute;
import nu.xom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * This class converts a SemIm into xml.
 *
 * @author Matt Easterday
 * @version $Id: $Id
 */
public class SemXmlRenderer {

    /**
     * Prevent instantiation.
     */
    private SemXmlRenderer() {
    }

    /**
     * Converts a Sem Im into xml.
     *
     * @param semIm the instantiated structural equation model to convert
     * @return xml representation
     */
    public static Element getElement(SemIm semIm) {
        Element semElement = new Element(SemXmlConstants.SEM);
        semElement.appendChild(SemXmlRenderer.makeVariables(semIm));
        semElement.appendChild(SemXmlRenderer.makeEdges(semIm));
        semElement.appendChild(SemXmlRenderer.makeMarginalErrorDistribution(semIm));
        semElement.appendChild(SemXmlRenderer.makeJointErrorDistribution(semIm));
        return semElement;
    }


    private static Element makeVariables(SemIm semIm) {
        Element variablesElement = new Element(SemXmlConstants.SEM_VARIABLES);
        Element variable;
        Node measuredNode, latentNode;
        for (Node node1 : semIm.getSemPm().getMeasuredNodes()) {
            measuredNode = node1;
            variable = new Element(SemXmlConstants.CONTINUOUS_VARIABLE);
            variable.addAttribute(new Attribute(SemXmlConstants.NAME, measuredNode.getName()));
            variable.addAttribute(new Attribute(SemXmlConstants.IS_LATENT, "no"));
            variable.addAttribute(new Attribute(SemXmlConstants.INTERCEPT, Double.toString(semIm.getIntercept(measuredNode))));
            variable.addAttribute(new Attribute(SemXmlConstants.X, Integer.toString(measuredNode.getCenterX())));
            variable.addAttribute(new Attribute(SemXmlConstants.Y, Integer.toString(measuredNode.getCenterY())));
            variablesElement.appendChild(variable);
        }
        for (Node node : semIm.getSemPm().getLatentNodes()) {
            latentNode = node;
            variable = new Element(SemXmlConstants.CONTINUOUS_VARIABLE);
            variable.addAttribute(new Attribute(SemXmlConstants.NAME, latentNode.getName()));
            variable.addAttribute(new Attribute(SemXmlConstants.IS_LATENT, "yes"));
            variable.addAttribute(new Attribute(SemXmlConstants.INTERCEPT, Double.toString(semIm.getIntercept(latentNode))));
            variable.addAttribute(new Attribute(SemXmlConstants.X, Integer.toString(latentNode.getCenterX())));
            variable.addAttribute(new Attribute(SemXmlConstants.Y, Integer.toString(latentNode.getCenterY())));
            variablesElement.appendChild(variable);
        }
        return variablesElement;
    }

    private static Element makeEdges(SemIm semIm) {
        Element edgesElement = new Element(SemXmlConstants.EDGES);
        Parameter param;
        Element edge;

        for (Parameter parameter : semIm.getSemPm().getParameters()) {
            param = parameter;
            if (param.getType() == ParamType.COEF) {
                edge = new Element(SemXmlConstants.EDGE);
                edge.addAttribute(new Attribute(SemXmlConstants.CAUSE_NODE, param.getNodeA().getName()));
                edge.addAttribute(new Attribute(SemXmlConstants.EFFECT_NODE, param.getNodeB().getName()));
                edge.addAttribute(new Attribute(SemXmlConstants.COEF, Double.toString(semIm.getParamValue(param))));
                edge.addAttribute(new Attribute(SemXmlConstants.FIXED, Boolean.valueOf(param.isFixed()).toString()));
                edgesElement.appendChild(edge);
            }
        }
        return edgesElement;
    }


    private static Element makeMarginalErrorDistribution(SemIm semIm) {
        Element marginalErrorElement = new Element(SemXmlConstants.MARGINAL_ERROR_DISTRIBUTION);
        Element normal;

        SemGraph semGraph = semIm.getSemPm().getGraph();
        semGraph.setShowErrorTerms(true);

        for (Node node : SemXmlRenderer.getExogenousNodes(semGraph)) {
//            Node graphNode = semGraph.getChildren(node).getFirst();
            normal = new Element(SemXmlConstants.NORMAL);
            normal.addAttribute(new Attribute(SemXmlConstants.VARIABLE, node.getName()));
//            normal.addAttribute(new Attribute(SemXmlConstants.INTERCEPT, Double.toString(semIm.getIntercept(graphNode))));
            normal.addAttribute(new Attribute(SemXmlConstants.VARIANCE, Double.toString(semIm.getParamValue(node, node))));
            marginalErrorElement.appendChild(normal);
        }
        return marginalErrorElement;
    }

    private static List<Node> getExogenousNodes(SemGraph graph) {
        List<Node> exogenousNodes = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            exogenousNodes.add(graph.getExogenous(node));
        }

        return exogenousNodes;
    }


    private static Element makeJointErrorDistribution(SemIm semIm) {
        Element jointErrorElement = new Element(SemXmlConstants.JOINT_ERROR_DISTRIBUTION);
        Element normal;
        Parameter param;

        for (Parameter parameter : semIm.getSemPm().getParameters()) {
            param = parameter;
            if (param.getType() == ParamType.COVAR) {
                normal = new Element(SemXmlConstants.NORMAL);
                normal.addAttribute(new Attribute(SemXmlConstants.NODE_1, param.getNodeA().getName()));
                normal.addAttribute(new Attribute(SemXmlConstants.NODE_2, param.getNodeB().getName()));
                normal.addAttribute(new Attribute(SemXmlConstants.COVARIANCE, Double.toString(param.getStartingValue())));
                jointErrorElement.appendChild(normal);
            }
        }

        return jointErrorElement;
    }

}



