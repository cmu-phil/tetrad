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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.Variable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class takes an xml element representing a bayes im and converts it to
 * a BayesIM
 *
 * @author mattheweasterday
 */
public class XdslXmlParser {

    private HashMap<String, Variable> namesToVars;

    private boolean useDisplayNames;

    /**
     * Takes an xml representation of a Bayes IM and reinstantiates the IM
     *
     * @param element the xml of the IM
     * @return the BayesIM
     */
    public BayesIm getBayesIm(final Element element) {
        if (!"smile".equals(element.getQualifiedName())) {
            throw new IllegalArgumentException("Expecting " +
                    "smile" + " element.");
        }

        final Elements elements = element.getChildElements();

        Element element0 = null, element1 = null;

        for (int i = 0; i < elements.size(); i++) {
            final Element _element = elements.get(i);

            if ("nodes".equals(_element.getQualifiedName())) {
                element0 = _element;
            }

            if ("extensions".equals(_element.getQualifiedName())) {
                element1 = _element.getFirstChildElement("genie");
            }
        }

        final Map<String, String> displayNames = mapDisplayNames(element1, this.useDisplayNames);

        final BayesIm bayesIm = buildIM(element0, displayNames);

        return bayesIm;
    }

    private BayesIm buildIM(final Element element0, final Map<String, String> displayNames) {
        final Elements elements = element0.getChildElements();

        for (int i = 0; i < elements.size(); i++) {
            if (!"cpt".equals(elements.get(i).getQualifiedName())) {
                throw new IllegalArgumentException("Expecting cpt element.");
            }
        }

        final Dag dag = new Dag();

        // Get the nodes.
        for (int i = 0; i < elements.size(); i++) {
            final Element cpt = elements.get(i);
            final String name = cpt.getAttribute(0).getValue();

            if (displayNames == null) {
                dag.addNode(new GraphNode(name));
            } else {
                dag.addNode(new GraphNode(displayNames.get(name)));
            }

        }

        // Get the edges.
        for (int i = 0; i < elements.size(); i++) {
            final Element cpt = elements.get(i);

            final Elements cptElements = cpt.getChildElements();

            for (int j = 0; j < cptElements.size(); j++) {
                final Element cptElement = cptElements.get(j);

                if (cptElement.getQualifiedName().equals("parents")) {
                    final String list = cptElement.getValue();
                    final String[] parentNames = list.split(" ");

                    for (final String name : parentNames) {
                        if (displayNames == null) {
                            final edu.cmu.tetrad.graph.Node parent = dag.getNode(name);
                            final edu.cmu.tetrad.graph.Node child = dag.getNode(cpt.getAttribute(0).getValue());
                            dag.addDirectedEdge(parent, child);
                        } else {
                            final edu.cmu.tetrad.graph.Node parent = dag.getNode(displayNames.get(name));
                            final edu.cmu.tetrad.graph.Node child = dag.getNode(displayNames.get(cpt.getAttribute(0).getValue()));
                            dag.addDirectedEdge(parent, child);
                        }
                    }
                }
            }

            final String name;

            if (displayNames == null) {
                name = cpt.getAttribute(0).getValue();
            } else {
                name = displayNames.get(cpt.getAttribute(0).getValue());
            }

            dag.addNode(new GraphNode(name));
        }

        // PM
        final BayesPm pm = new BayesPm(dag);

        for (int i = 0; i < elements.size(); i++) {
            final Element cpt = elements.get(i);

            final String varName = cpt.getAttribute(0).getValue();
            final Node node;

            if (displayNames == null) {
                node = dag.getNode(varName);
            } else {
                node = dag.getNode(displayNames.get(varName));
            }

            final Elements cptElements = cpt.getChildElements();

            final List<String> stateNames = new ArrayList<>();

            for (int j = 0; j < cptElements.size(); j++) {
                final Element cptElement = cptElements.get(j);

                if (cptElement.getQualifiedName().equals("state")) {
                    final Attribute attribute = cptElement.getAttribute(0);
                    final String stateName = attribute.getValue();
                    stateNames.add(stateName);
                }

            }

            pm.setCategories(node, stateNames);
        }

        // IM
        final BayesIm im = new MlBayesIm(pm);

        for (int nodeIndex = 0; nodeIndex < elements.size(); nodeIndex++) {
            final Element cpt = elements.get(nodeIndex);

            final Elements cptElements = cpt.getChildElements();

            for (int j = 0; j < cptElements.size(); j++) {
                final Element cptElement = cptElements.get(j);

                if (cptElement.getQualifiedName().equals("probabilities")) {
                    final String list = cptElement.getValue();
                    final String[] probsStrings = list.split(" ");
                    final List<Double> probs = new ArrayList<>();

                    for (final String probString : probsStrings) {
                        probs.add(Double.parseDouble(probString));
                    }

                    int count = -1;

                    for (int row = 0; row < im.getNumRows(nodeIndex); row++) {
                        for (int col = 0; col < im.getNumColumns(nodeIndex); col++) {
                            im.setProbability(nodeIndex, row, col, probs.get(++count));
                        }
                    }
                }
            }
        }

        return im;
    }

    private Map<String, String> mapDisplayNames(final Element element1, final boolean useDisplayNames) {
        if (useDisplayNames) {
            final Map<String, String> displayNames = new HashMap<>();

            final Elements elements = element1.getChildElements();

            for (int i = 0; i < elements.size(); i++) {
                final Element nodeElement = elements.get(i);

                if (nodeElement.getLocalName().equals("textbox")) continue;

                final String varName = nodeElement.getAttribute(0).getValue();

                final Elements nodeElements = nodeElement.getChildElements();
                String displayName = null;

                for (int j = 0; j < nodeElements.size(); j++) {
                    final Element e = nodeElements.get(j);

                    if (e.getQualifiedName().equals("name")) {
                        String value = e.getValue();
                        value = value.replace(" ", "_");
                        displayName = value;
                        break;
                    }

                }

                displayNames.put(varName, displayName);
            }

            return displayNames;
        } else {
            return null;
        }
    }

    public boolean isUseDisplayNames() {
        return this.useDisplayNames;
    }

    public void setUseDisplayNames(final boolean useDisplayNames) {
        this.useDisplayNames = useDisplayNames;
    }
}




