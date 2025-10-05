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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Text;

/**
 * Renders Bayes nets and related models in XML.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class BayesXmlRenderer {

    /**
     * Private constructor to prevent instantiation.
     */
    private BayesXmlRenderer() {
    }

    /**
     * Private constructor to prevent instantiation.
     *
     * @param bayesIm the Bayes net
     * @return the XML element
     */
    public static Element getElement(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        Element element = new Element("bayesNet");

        element.appendChild(BayesXmlRenderer.getVariablesElement(bayesIm));
        element.appendChild(BayesXmlRenderer.getParentsElement(bayesIm));
        element.appendChild(BayesXmlRenderer.getCptsElement(bayesIm));

        return element;
    }

    private static Element getVariablesElement(BayesIm bayesIm) {
        Element element = new Element("bnVariables");

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            Node node = bayesIm.getNode(i);
            BayesPm bayesPm = bayesIm.getBayesPm();
            DiscreteVariable variable =
                    (DiscreteVariable) bayesPm.getVariable(node);
            Element element1 = new Element("discreteVariable");
            element1.addAttribute(new Attribute("name", variable.getName()));
            element1.addAttribute(new Attribute("index", "" + i));

            boolean latent = node.getNodeType() == NodeType.LATENT;

            if (latent) {
                element1.addAttribute(new Attribute("latent", "yes"));
            }

            element1.addAttribute(new Attribute("x", "" + node.getCenterX()));
            element1.addAttribute(new Attribute("y", "" + node.getCenterY()));

            for (int j = 0; j < variable.getNumCategories(); j++) {
                Element category = new Element("category");
                category.addAttribute(
                        new Attribute("name", variable.getCategory(j)));
                category.addAttribute(new Attribute("index", "" + j));
                element1.appendChild(category);
            }

            element.appendChild(element1);
        }

        return element;
    }

    private static Element getParentsElement(BayesIm bayesIm) {
        Element parents = new Element("parents");

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            Element variable = new Element("parentsFor");
            parents.appendChild(variable);

            String varName = bayesIm.getNode(i).getName();
            variable.addAttribute(new Attribute("name", varName));

            int[] parentIndices = bayesIm.getParents(i);

            for (int j = 0; j < parentIndices.length; j++) {
                Element parent = new Element("parent");
                variable.appendChild(parent);

                Node parentNode = bayesIm.getNode(parentIndices[j]);
                parent.addAttribute(
                        new Attribute("name", parentNode.getName()));
                parent.addAttribute(new Attribute("index", "" + j));
            }


        }

        return parents;
    }

    private static Element getCptsElement(BayesIm bayesIm) {
        Element cpts = new Element("cpts");
        cpts.addAttribute(new Attribute("rowSumTolerance", "0.0001"));

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            Element cpt = new Element("cpt");
            cpts.appendChild(cpt);

            String varName = bayesIm.getNode(i).getName();
            int numRows = bayesIm.getNumRows(i);
            int numCols = bayesIm.getNumColumns(i);

            cpt.addAttribute(new Attribute("variable", varName));
            cpt.addAttribute(new Attribute("numRows", "" + numRows));
            cpt.addAttribute(new Attribute("numCols", "" + numCols));

            for (int j = 0; j < numRows; j++) {
                Element row = new Element("row");
                cpt.appendChild(row);

                StringBuilder buf = new StringBuilder();

                for (int k = 0; k < numCols; k++) {
                    double probability = bayesIm.getProbability(i, j, k);
                    buf.append(NumberFormatUtil.getInstance().getNumberFormat().format(probability)).append(" ");
                }

                String s = buf.toString();
                row.appendChild(new Text(s.trim()));
            }
        }

        return cpts;
    }
}





