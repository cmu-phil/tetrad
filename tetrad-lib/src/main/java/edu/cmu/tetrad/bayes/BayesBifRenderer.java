///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders Bayes nets and related models in XML.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class BayesBifRenderer {

    /**
     * Private constructor to prevent instantiation.
     */
    private BayesBifRenderer() {
    }

    /**
     * Renders the given BayesIm object as a Bayesian network in the BIF (Bayesian Interchange Format) format.
     *
     * @param bayesIm the BayesIm object representing the Bayesian network
     * @return the Bayesian network in BIF format as a string
     */
    public static String render(BayesIm bayesIm) {
        StringBuilder builder = new StringBuilder();

        // Write the name
        builder.append("network unknown {\n}\n");

        // Write the variables

        for (int nodeIndex = 0; nodeIndex < bayesIm.getNumNodes(); nodeIndex++) {
            DiscreteVariable node = (DiscreteVariable) bayesIm.getNode(nodeIndex);
            builder.append("variable ");
            builder.append(node.getName());
            builder.append(" {\n");
            builder.append("  type discrete [ ");
            builder.append(node.getNumCategories());
            builder.append(" ] { ");
            for (int i = 0; i < node.getNumCategories(); i++) {
                builder.append(node.getCategories().get(i));
                if (i < node.getNumCategories() - 1) {
                    builder.append(", ");
                }
            }
            builder.append(" };\n");
            builder.append("}\n");
        }

        // Write the probability distributions

        for (int nodeIndex = 0; nodeIndex < bayesIm.getNumNodes(); nodeIndex++) {
            DiscreteVariable child = (DiscreteVariable) bayesIm.getNode(nodeIndex);

            builder.append("probability ( ");
            builder.append(child.getName());

            if (bayesIm.getNumParents(nodeIndex) > 0) {
                builder.append(" | ");
            }

            int[] parents = bayesIm.getParents(nodeIndex);
            List<DiscreteVariable> _parents = new ArrayList<>();

            for (int i = 0; i < parents.length; i++) {
                DiscreteVariable parent = (DiscreteVariable) bayesIm.getNode(parents[i]);
                _parents.add(parent);
                builder.append(parent.getName());

                if (i < parents.length - 1) {
                    builder.append(", ");
                }
            }

            builder.append(" ) {\n");

            for (int row = 0; row < bayesIm.getNumRows(nodeIndex); row++) {
                int[] parentValues = bayesIm.getParentValues(nodeIndex, row);

                if (parentValues.length == 0) {
                    builder.append("  table ");

                    for (int j = 0; j < bayesIm.getNumColumns(nodeIndex); j++) {
                        double p = bayesIm.getProbability(nodeIndex, row, j);
                        builder.append(NumberFormatUtil.getInstance().getNumberFormat().format(p));
                        if (j < bayesIm.getNumColumns(nodeIndex) - 1) {
                            builder.append(", ");
                        }
                    }

                    builder.append(";\n");
                } else {
                    builder.append("  ( ");

                    for (int i = 0; i < parentValues.length; i++) {
                        builder.append(_parents.get(i).getCategory(parentValues[i]));
                        if (i < parentValues.length - 1) {
                            builder.append(", ");
                        }
                    }

                    builder.append(" ) ");

                    for (int j = 0; j < bayesIm.getNumColumns(nodeIndex); j++) {
                        double p = bayesIm.getProbability(nodeIndex, row, j);
                        builder.append(NumberFormatUtil.getInstance().getNumberFormat().format(p));
                        if (j < bayesIm.getNumColumns(nodeIndex) - 1) {
                            builder.append(", ");
                        }
                    }

                    builder.append(";\n");
                }
            }

            builder.append("}\n");
        }

        return builder.toString();
    }
}




