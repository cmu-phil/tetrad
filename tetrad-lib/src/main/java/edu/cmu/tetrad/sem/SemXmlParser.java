///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.*;
import nu.xom.Element;
import nu.xom.Elements;

import java.util.HashMap;
import java.util.Map;

/**
 * This class takes an xml element representing a SEM semIm and converts it to a SemIM
 *
 * @author Matt Easterday
 * @version $Id: $Id
 */
public class SemXmlParser {

    /**
     * This class represents a parser for SEM XML representation. It provides methods to parse XML and reinstantiate SEM
     * IM objects. The class does not require any input parameters.
     */
    public SemXmlParser() {
    }

    /**
     * Takes an xml representation of a SEM IM and reinstantiates the IM
     *
     * @param semImElement the xml of the IM
     * @return the SemIM
     */
    public static SemIm getSemIm(Element semImElement) {
        if (!SemXmlConstants.SEM.equals(semImElement.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting '" + SemXmlConstants.SEM + "' element"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        Element variablesElement = semImElement.getFirstChildElement(SemXmlConstants.SEM_VARIABLES);
        Element edgesElement = semImElement.getFirstChildElement(SemXmlConstants.EDGES);
        Element marginalDistributionElement = semImElement.getFirstChildElement(SemXmlConstants.MARGINAL_ERROR_DISTRIBUTION);
        Element jointDistributionElement = semImElement.getFirstChildElement(SemXmlConstants.JOINT_ERROR_DISTRIBUTION);


        Graph graph = SemXmlParser.makeVariables(variablesElement);
        SemIm im = SemXmlParser.makeEdges(edgesElement, jointDistributionElement, graph);
        SemXmlParser.addMarginalErrorDistribution(marginalDistributionElement, im);
        SemXmlParser.addJointErrorDistribution(jointDistributionElement, im);

        // The intercepts go in last, since the means they imply depend on the coefficients.
        SemXmlParser.setIntercepts(variablesElement, im);

        return im;
    }


    private static Graph makeVariables(Element variablesElement) {
        if (variablesElement == null || !SemXmlConstants.SEM_VARIABLES.equals(variablesElement.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting '" + SemXmlConstants.SEM_VARIABLES + "' element"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Element var;
        GraphNode node;
        int x, y;

        // Not a Dag, since a SEM IM may have correlated errors and may be cyclic.
        Graph semGraph = new EdgeListGraph();
        Elements vars = variablesElement.getChildElements(SemXmlConstants.CONTINUOUS_VARIABLE);

        for (int i = 0; i < vars.size(); i++) {
            var = vars.get(i);
            node = new GraphNode(var.getAttributeValue(SemXmlConstants.NAME));
            if ("yes".equals(var.getAttributeValue(SemXmlConstants.IS_LATENT))) { //$NON-NLS-1$
                node.setNodeType(NodeType.LATENT);
            } else {
                node.setNodeType(NodeType.MEASURED);
            }
            x = Integer.parseInt(var.getAttributeValue(SemXmlConstants.X));
            y = Integer.parseInt(var.getAttributeValue(SemXmlConstants.Y));
            node.setCenterX(x);
            node.setCenterY(y);
            semGraph.addNode(node);
        }
        return semGraph;
    }

    private static SemIm makeEdges(Element edgesElement, Element jointDistributionElement, Graph semGraph) {
        if (edgesElement == null || !SemXmlConstants.EDGES.equals(edgesElement.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting '" + SemXmlConstants.EDGES + "' element"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Element edge;
        Node causeNode, effectNode;

        Elements edges = edgesElement.getChildElements(SemXmlConstants.EDGE);

        for (int i = 0; i < edges.size(); i++) {
            edge = edges.get(i);
            causeNode = SemXmlParser.getNode(semGraph, edge.getAttributeValue(SemXmlConstants.CAUSE_NODE));
            effectNode = SemXmlParser.getNode(semGraph, edge.getAttributeValue(SemXmlConstants.EFFECT_NODE));
            semGraph.addDirectedEdge(causeNode, effectNode);
        }

        // The error covariances are bidirected edges in the graph, which need to be there for the SEM PM to have
        // parameters for them.
        if (jointDistributionElement != null) {
            Elements normals = jointDistributionElement.getChildElements(SemXmlConstants.NORMAL);

            for (int i = 0; i < normals.size(); i++) {
                Node node1 = SemXmlParser.getVariable(semGraph, normals.get(i).getAttributeValue(SemXmlConstants.NODE_1));
                Node node2 = SemXmlParser.getVariable(semGraph, normals.get(i).getAttributeValue(SemXmlConstants.NODE_2));
                semGraph.addBidirectedEdge(node1, node2);
            }
        }

        // A parameter needs to be fixed in the SEM PM before the SEM IM is made, for the SEM IM to treat it as fixed.
        SemPm semPm = new SemPm(semGraph);

        for (int i = 0; i < edges.size(); i++) {
            edge = edges.get(i);
            causeNode = SemXmlParser.getNode(semPm.getGraph(), edge.getAttributeValue(SemXmlConstants.CAUSE_NODE));
            effectNode = SemXmlParser.getNode(semPm.getGraph(), edge.getAttributeValue(SemXmlConstants.EFFECT_NODE));

            if (Boolean.parseBoolean(edge.getAttributeValue(SemXmlConstants.FIXED))) {
                semPm.getCoefficientParameter(causeNode, effectNode).setFixed(true);
            }
        }

        SemIm semIm = new SemIm(semPm);
        SemGraph graph = semIm.getSemPm().getGraph();

        for (int i = 0; i < edges.size(); i++) {
            edge = edges.get(i);
            causeNode = SemXmlParser.getNode(graph, edge.getAttributeValue(SemXmlConstants.CAUSE_NODE));
            effectNode = SemXmlParser.getNode(graph, edge.getAttributeValue(SemXmlConstants.EFFECT_NODE));
            SemXmlParser.setValue(semIm, semIm.getSemPm().getCoefficientParameter(causeNode, effectNode),
                    Double.parseDouble(edge.getAttributeValue(SemXmlConstants.COEF)));
        }

        return semIm;
    }

    private static void setIntercepts(Element variablesElement, SemIm im) {
        Elements vars = variablesElement.getChildElements(SemXmlConstants.CONTINUOUS_VARIABLE);
        Map<Node, Double> intercepts = new HashMap<>();

        for (int i = 0; i < vars.size(); i++) {
            Element var = vars.get(i);
            Node node = SemXmlParser.getNode(im.getSemPm().getGraph(), var.getAttributeValue(SemXmlConstants.NAME));
            String intercept = var.getAttributeValue(SemXmlConstants.INTERCEPT);

            if (intercept != null && !Double.isNaN(Double.parseDouble(intercept))) {
                intercepts.put(node, Double.parseDouble(intercept));
            }
        }

        // Intercepts are not defined for a cyclic model (they are written as NaN), so its means are left at zero.
        if (!intercepts.isEmpty() && !im.isCyclic()) {
            im.setIntercepts(intercepts);
        }
    }

    private static void addMarginalErrorDistribution(Element marginalDistributionElement, SemIm semIm) {
        if (marginalDistributionElement == null) {
            return;
        }

        if (!SemXmlConstants.MARGINAL_ERROR_DISTRIBUTION.equals(marginalDistributionElement.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting '" + SemXmlConstants.MARGINAL_ERROR_DISTRIBUTION + "' element"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Element normal;
        Node node;
        Elements normals = marginalDistributionElement.getChildElements(SemXmlConstants.NORMAL);

        for (int i = 0; i < normals.size(); i++) {
            normal = normals.get(i);
            node = SemXmlParser.getVariable(semIm.getSemPm().getGraph(), normal.getAttributeValue(SemXmlConstants.VARIABLE));
            SemXmlParser.setValue(semIm, semIm.getSemPm().getVarianceParameter(node),
                    Double.parseDouble(normal.getAttributeValue(SemXmlConstants.VARIANCE)));
        }
    }

    private static void addJointErrorDistribution(Element jointDistributionElement, SemIm semIm) {
        if (jointDistributionElement == null) {
            return;
        }

        if (!SemXmlConstants.JOINT_ERROR_DISTRIBUTION.equals(jointDistributionElement.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting '" + SemXmlConstants.JOINT_ERROR_DISTRIBUTION + "' element"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        Element normal;
        Node node1, node2;
        Elements normals = jointDistributionElement.getChildElements(SemXmlConstants.NORMAL);

        for (int i = 0; i < normals.size(); i++) {
            normal = normals.get(i);
            node1 = SemXmlParser.getVariable(semIm.getSemPm().getGraph(), normal.getAttributeValue(SemXmlConstants.NODE_1));
            node2 = SemXmlParser.getVariable(semIm.getSemPm().getGraph(), normal.getAttributeValue(SemXmlConstants.NODE_2));
            SemXmlParser.setValue(semIm, SemXmlParser.getCovarianceParameter(semIm, node1, node2),
                    Double.parseDouble(normal.getAttributeValue(SemXmlConstants.COVARIANCE)));
        }
    }

    private static Node getNode(Graph graph, String name) {
        Node node = name == null ? null : graph.getNode(name);

        if (node == null) {
            throw new IllegalArgumentException("There is no variable named '" + name + "' in the '"
                                               + SemXmlConstants.SEM_VARIABLES + "' element.");
        }

        return node;
    }

    /**
     * Returns the variable for the given name, which may be the name of the variable itself or the name of its error
     * term, E_ followed by the name of the variable, which is how the variances are saved.
     */
    private static Node getVariable(Graph graph, String name) {
        if (name != null && name.startsWith("E_") && graph.getNode(name.substring(2)) != null) {
            return graph.getNode(name.substring(2));
        }

        return SemXmlParser.getNode(graph, name);
    }

    private static Parameter getCovarianceParameter(SemIm semIm, Node node1, Node node2) {
        for (Parameter parameter : semIm.getSemPm().getParameters()) {
            if (parameter.getType() == ParamType.COVAR
                && ((parameter.getNodeA() == node1 && parameter.getNodeB() == node2)
                    || (parameter.getNodeA() == node2 && parameter.getNodeB() == node1))) {
                return parameter;
            }
        }

        throw new IllegalArgumentException("There is no error covariance for " + node1 + " and " + node2 + ".");
    }

    private static void setValue(SemIm semIm, Parameter parameter, double value) {
        if (semIm.getFixedParameters().contains(parameter)) {
            semIm.setFixedParamValue(parameter, value);
        } else {
            semIm.setParamValue(parameter, value);
        }
    }

}




