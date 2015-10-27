///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Text;

import java.util.*;

/**
 * Parses Bayes elements back to objects.
 *
 * @author Joseph Ramsey
 */
public final class BayesXmlParser {
    private Map<String, Node> namesToVars;

    public BayesIm getBayesIm(Element element) {
        if (!"bayesNet".equals(element.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting 'bayesNet' element.");
        }

        Elements elements = element.getChildElements();

        Element element0 = elements.get(0);
        Element element1 = elements.get(1);
        Element element2 = elements.get(2);

        List<Node> variables = getVariables(element0);
        BayesPm bayesPm = makeBayesPm(variables, element1);

        return makeBayesIm(bayesPm, element2);
    }

    private List<Node> getVariables(Element element0) {
        if (!"bnVariables".equals(element0.getQualifiedName())) {
            throw new IllegalArgumentException(
                    "Expecting 'bnVariables' element.");
        }

        List<Node> variables = new LinkedList<Node>();

        Elements elements = element0.getChildElements();

        for (int i = 0; i < elements.size(); i++) {
            Element e1 = elements.get(i);
            Elements e2Elements = e1.getChildElements();


            if (!"discreteVariable".equals(e1.getQualifiedName())) {
                throw new IllegalArgumentException(
                        "Expecting 'discreteVariable' " + "element.");
            }

            String name = e1.getAttributeValue("name");

            String isLatentVal = e1.getAttributeValue("latent");
            boolean isLatent =
                    (isLatentVal != null) && ((isLatentVal.equals("yes")));
            Integer x = new Integer(e1.getAttributeValue("x"));
            Integer y = new Integer(e1.getAttributeValue("y"));

            int numCategories = e2Elements.size();
            List<String> categories = new LinkedList<String>();


            for (int j = 0; j < numCategories; j++) {
                Element e2 = e2Elements.get(j);

                if (!"category".equals(e2.getQualifiedName())) {
                    throw new IllegalArgumentException(
                            "Expecting 'category' " + "element.");
                }

                categories.add(e2.getAttributeValue("name"));
            }

            DiscreteVariable var = new DiscreteVariable(name, categories);
            if (isLatent) {
                var.setNodeType(NodeType.LATENT);
            }

            var.setCenterX(x);
            var.setCenterY(y);
            variables.add(var);
        }

        namesToVars = new HashMap<String, Node>();

        for (Node v : variables) {
            String name = v.getName();
            namesToVars.put(name, v);
        }

        return variables;
    }

    private BayesPm makeBayesPm(List<Node> variables, Element element1) {
        if (!"parents".equals(element1.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting 'parents' element.");
        }

        Dag graph = new Dag();

        for (Node variable : variables) {
            graph.addNode(variable);
        }

        Elements elements = element1.getChildElements();

        for (int i = 0; i < elements.size(); i++) {
            Element e1 = elements.get(i);

            if (!"parentsFor".equals(e1.getQualifiedName())) {
                throw new IllegalArgumentException(
                        "Expecting 'parentsFor' element.");
            }

            String varName = e1.getAttributeValue("name");
            Node var = namesToVars.get(varName);

            Elements elements1 = e1.getChildElements();

            for (int j = 0; j < elements1.size(); j++) {
                Element e2 = elements1.get(j);

                if (!"parent".equals(e2.getQualifiedName())) {
                    throw new IllegalArgumentException(
                            "Expecting 'parent' element.");
                }

                String parentName = e2.getAttributeValue("name");
                Node parent = namesToVars.get(parentName);

                graph.addDirectedEdge(parent, var);
            }
        }

        BayesPm bayesPm = new BayesPm(graph);

        for (Node variable1 : variables) {
            DiscreteVariable graphVariable = (DiscreteVariable) variable1;
            List<String> categories = graphVariable.getCategories();
            bayesPm.setCategories(graphVariable, categories);
        }

        return bayesPm;
    }

    private static BayesIm makeBayesIm(BayesPm bayesPm, Element element2) {
        if (!"cpts".equals(element2.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting 'cpts' element.");
        }

        MlBayesIm bayesIm = new MlBayesIm(bayesPm);

        Elements elements2 = element2.getChildElements();

        for (int nodeIndex = 0; nodeIndex < elements2.size(); nodeIndex++) {
            Element e1 = elements2.get(nodeIndex);

            if (!"cpt".equals(e1.getQualifiedName())) {
                throw new IllegalArgumentException("Expecting 'cpt' element.");
            }

            String numRowsString = e1.getAttributeValue("numRows");
            String numColsString = e1.getAttributeValue("numCols");

            int numRows = Integer.parseInt(numRowsString);
            int numCols = Integer.parseInt(numColsString);

            Elements e1Elements = e1.getChildElements();

            if (e1Elements.size() != numRows) {
                throw new IllegalArgumentException("Element cpt claimed " +
                        +numRows + " rows, but there are only " +
                        e1Elements.size() + " rows in the file.");
            }

            for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
                Element e2 = e1Elements.get(rowIndex);

                if (!"row".equals(e2.getQualifiedName())) {
                    throw new IllegalArgumentException(
                            "Expecting 'parent' element.");
                }

                Text rowNode = (Text) e2.getChild(0);
                String rowString = rowNode.getValue();

                StringTokenizer t = new StringTokenizer(rowString);

                for (int colIndex = 0; colIndex < numCols; colIndex++) {
                    String token = t.nextToken();

                    try {
                        double value = Double.parseDouble(token);
                        bayesIm.setProbability(nodeIndex, rowIndex, colIndex,
                                value);
                    }
                    catch (NumberFormatException e) {
                        // Skip.
                    }
                }

                if (t.hasMoreTokens()) {
                    throw new IllegalArgumentException("Element cpt claimed " +
                            numCols +
                            " columnns , but there are more that that " +
                            "in the file.");
                }
            }
        }

        return bayesIm;
    }
}





