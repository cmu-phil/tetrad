/*
 * Copyright (C) 2020 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.Evidence;
import edu.cmu.tetrad.bayes.JunctionTreeUpdater;
import edu.cmu.tetrad.bayes.ManipulatingBayesUpdater;
import edu.cmu.tetrad.bayes.Proposition;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Unmarshallable;
import java.text.NumberFormat;

/**
 *
 * Jan 21, 2020 1:27:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class JunctionTreeWrapper implements SessionModel, UpdaterWrapper, Unmarshallable {

    static final long serialVersionUID = 23L;

    private JunctionTreeUpdater bayesUpdater;

    private String name;

    private Parameters params;

    public JunctionTreeWrapper(BayesImWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        BayesIm bayesIm = wrapper.getBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(DirichletBayesImWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        DirichletBayesIm bayesIm = wrapper.getDirichletBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(BayesEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        BayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(DirichletEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        DirichletBayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(EmBayesEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        BayesIm bayesIm = wrapper.getEstimateBayesIm();
        setup(bayesIm, params);
    }

    private void setup(BayesIm bayesIm, Parameters params) {
        TetradLogger.getInstance().setConfigForClass(this.getClass());
        this.params = params;
        if (params.get("evidence", null) == null || ((Evidence) params.get("evidence", null)).isIncompatibleWith(bayesIm)) {
            bayesUpdater = new JunctionTreeUpdater(bayesIm);
        } else {
            bayesUpdater = new JunctionTreeUpdater(bayesIm,
                    (Evidence) params.get("evidence", null));
        }

        Node node = (Node) getParams().get("variable", null);

        if (node != null) {
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

            TetradLogger.getInstance().log("info", "\nRow Summing Exact Updater");

            String nodeName = node.getName();
            int nodeIndex = bayesIm.getNodeIndex(bayesIm.getNode(nodeName));
            double[] priors = getBayesUpdater().calculatePriorMarginals(nodeIndex);
            double[] marginals = getBayesUpdater().calculateUpdatedMarginals(nodeIndex);

            TetradLogger.getInstance().log("details", "\nVariable = " + nodeName);
            TetradLogger.getInstance().log("details", "\nEvidence:");
            Evidence evidence = (Evidence) getParams().get("evidence", null);
            Proposition proposition = evidence.getProposition();

            for (int i = 0; i < proposition.getNumVariables(); i++) {
                Node variable = proposition.getVariableSource().getVariables().get(i);
                int category = proposition.getSingleCategory(i);

                if (category != -1) {
                    TetradLogger.getInstance().log("details", "\t" + variable + " = " + category);
                }
            }

            TetradLogger.getInstance().log("details", "\nCat.\tPrior\tMarginal");

            for (int i = 0; i < priors.length; i++) {
                TetradLogger.getInstance().log("details", category(evidence, nodeName, i) + "\t"
                        + nf.format(priors[i]) + "\t" + nf.format(marginals[i]));
            }
        }
        TetradLogger.getInstance().reset();
    }

    private DiscreteVariable discreteVariable(Evidence evidence, String nodeName) {
        return evidence.getVariable(nodeName);
    }

    private String category(Evidence evidence, String nodeName, int i) {
        DiscreteVariable variable = discreteVariable(evidence, nodeName);
        return variable.getCategory(i);
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Parameters getParams() {
        return params;
    }

    @Override
    public ManipulatingBayesUpdater getBayesUpdater() {
        return bayesUpdater;
    }

}
