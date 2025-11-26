/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;


import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class converts a SemIm into xml.
 *
 * @author Matt Easterday
 * @version $Id: $Id
 */
public class SemLavaanRenderer {

    /**
     * Prevent instantiation.
     */
    private SemLavaanRenderer() {
    }

    public static String semImToLavaan(SemIm semIm) {
        return semImToLavaan(semIm, true, true, true, false);
    }

    /**
     * Converts a SemIm (parameterized SEM) into lavaan syntax (.lav).
     */
    public static String semImToLavaan(SemIm semIm, boolean includeIntercepts, boolean includeVariances,
                                       boolean includeCovariances, boolean fixParameters) {
        SemPm semPm = semIm.getSemPm();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        // Keep measured + latent variables together in a stable order.
        List<Node> nodes = new ArrayList<>();
        nodes.addAll(semPm.getMeasuredNodes());
        nodes.addAll(semPm.getLatentNodes());

        StringBuilder lavaan = new StringBuilder();

        // ---------------------------------------------------------------------
        // 1. Intercepts:  y ~ intercept*1
        // ---------------------------------------------------------------------
        if (includeIntercepts) {
            for (Node node : nodes) {
                double intercept = semIm.getIntercept(node);

                lavaan.append(node.getName()).append(" ~ ");
                if (fixParameters) {
                    lavaan.append(nf.format(intercept)).append("*1");
                } else {
                    lavaan.append("start(").append(nf.format(intercept)).append(")*1");
                }
                lavaan.append("\n");
            }
            lavaan.append("\n");
        }

        // ---------------------------------------------------------------------
        // 2. Regressions (directed edges):  y ~ b1*x1 + b2*x2 + ...
        //    We group ParamType.COEF parameters by their effect node.
        // ---------------------------------------------------------------------
        Map<Node, List<Parameter>> parentsByChild = new LinkedHashMap<>();

        for (Parameter param : semPm.getParameters()) {
            if (param.getType() == ParamType.COEF) {
                Node parent = param.getNodeA();
                Node child = param.getNodeB();
                parentsByChild.computeIfAbsent(child, k -> new ArrayList<>()).add(param);
            }
        }

        boolean hasDirected = false;

        for (Node child : nodes) {
            List<Parameter> paramsForChild = parentsByChild.get(child);
            if (paramsForChild == null || paramsForChild.isEmpty()) {
                continue;
            }

            hasDirected = true;
            lavaan.append(child.getName()).append(" ~ ");

            for (int i = 0; i < paramsForChild.size(); i++) {
                Parameter param = paramsForChild.get(i);
                Node parent = param.getNodeA();
                double value = semIm.getParamValue(param);

                if (i > 0) {
                    lavaan.append(" + ");
                }

                if (fixParameters) {
                    lavaan.append(nf.format(value)).append("*").append(parent.getName());
                } else {
                    lavaan.append("start(").append(nf.format(value)).append(")*").append(parent.getName());
                }
            }

            lavaan.append("\n");
        }

        if (hasDirected) {
            lavaan.append("\n");
        }

        // ---------------------------------------------------------------------
        // 3. Residual covariances:  x ~~ cov*y
        //    From ParamType.COVAR parameters.
        // ---------------------------------------------------------------------
        if (includeCovariances) {
            boolean hasCovars = false;

            for (Parameter param : semPm.getParameters()) {
                if (param.getType() != ParamType.COVAR) continue;

                hasCovars = true;
                Node n1 = param.getNodeA();
                Node n2 = param.getNodeB();
                double cov = semIm.getParamValue(param);  // use IM value, not starting value

                lavaan.append(n1.getName()).append(" ~~ ");

                if (fixParameters) {
                    lavaan.append(nf.format(cov))
                            .append("*")
                            .append(n2.getName());
                } else {
                    lavaan.append("start(")
                            .append(nf.format(cov))
                            .append(")*")
                            .append(n2.getName());
                }

                lavaan.append("\n");
            }

            if (hasCovars) {
                lavaan.append("\n");
            }
        }

        // ---------------------------------------------------------------------
        // 4. Residual variances:  x ~~ var*x
        //    semIm.getParamValue(node, node) gives the variance.
        // ---------------------------------------------------------------------
        if (includeVariances) {
            for (Node node : nodes) {
                double var = semIm.getParamValue(node, node);

                lavaan.append(node.getName()).append(" ~~ ");
                if (fixParameters) {
                    lavaan.append(nf.format(var)).append("*").append(node.getName());
                } else {
                    lavaan.append("start(").append(nf.format(var)).append(")*").append(node.getName());
                }
                lavaan.append("\n");
            }
        }

        return lavaan.toString();
    }
}




