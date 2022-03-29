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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Text;

/**
 * Renders Bayes nets and related models in XML.
 *
 * @author Joseph Ramsey
 */
public final class BayesXmlRenderer {

    public static Element getElement(final BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        final Element element = new Element("bayesNet");

        element.appendChild(BayesXmlRenderer.getVariablesElement(bayesIm));
        element.appendChild(BayesXmlRenderer.getParentsElement(bayesIm));
        element.appendChild(BayesXmlRenderer.getCptsElement(bayesIm));

        return element;
    }

    private static Element getVariablesElement(final BayesIm bayesIm) {
        final Element element = new Element("bnVariables");

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            final Node node = bayesIm.getNode(i);
            final BayesPm bayesPm = bayesIm.getBayesPm();
            final DiscreteVariable variable =
                    (DiscreteVariable) bayesPm.getVariable(node);
            final Element element1 = new Element("discreteVariable");
            element1.addAttribute(new Attribute("name", variable.getName()));
            element1.addAttribute(new Attribute("index", "" + i));

            final boolean latent = node.getNodeType() == NodeType.LATENT;

            if (latent) {
                element1.addAttribute(new Attribute("latent", "yes"));
            }

            element1.addAttribute(new Attribute("x", "" + node.getCenterX()));
            element1.addAttribute(new Attribute("y", "" + node.getCenterY()));

            for (int j = 0; j < variable.getNumCategories(); j++) {
                final Element category = new Element("category");
                category.addAttribute(
                        new Attribute("name", variable.getCategory(j)));
                category.addAttribute(new Attribute("index", "" + j));
                element1.appendChild(category);
            }

            element.appendChild(element1);
        }

        return element;
    }

    private static Element getParentsElement(final BayesIm bayesIm) {
        final Element parents = new Element("parents");

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            final Element variable = new Element("parentsFor");
            parents.appendChild(variable);

            final String varName = bayesIm.getNode(i).getName();
            variable.addAttribute(new Attribute("name", varName));

            final int[] parentIndices = bayesIm.getParents(i);

            for (int j = 0; j < parentIndices.length; j++) {
                final Element parent = new Element("parent");
                variable.appendChild(parent);

                final Node parentNode = bayesIm.getNode(parentIndices[j]);
                parent.addAttribute(
                        new Attribute("name", parentNode.getName()));
                parent.addAttribute(new Attribute("index", "" + j));
            }


        }

        return parents;
    }

    private static Element getCptsElement(final BayesIm bayesIm) {
        final Element cpts = new Element("cpts");
        cpts.addAttribute(new Attribute("rowSumTolerance", "0.0001"));

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            final Element cpt = new Element("cpt");
            cpts.appendChild(cpt);

            final String varName = bayesIm.getNode(i).getName();
            final int numRows = bayesIm.getNumRows(i);
            final int numCols = bayesIm.getNumColumns(i);

            cpt.addAttribute(new Attribute("variable", varName));
            cpt.addAttribute(new Attribute("numRows", "" + numRows));
            cpt.addAttribute(new Attribute("numCols", "" + numCols));

            for (int j = 0; j < numRows; j++) {
                final Element row = new Element("row");
                cpt.appendChild(row);

                final StringBuilder buf = new StringBuilder();

                for (int k = 0; k < numCols; k++) {
                    final double probability = bayesIm.getProbability(i, j, k);
                    buf.append(NumberFormatUtil.getInstance().getNumberFormat().format(probability)).append(" ");
                }

                final String s = buf.toString();
                row.appendChild(new Text(s.trim()));
            }
        }

        return cpts;
    }
}




