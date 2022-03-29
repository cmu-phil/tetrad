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

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Unmarshallable;

import java.text.NumberFormat;

/**
 * Jan 21, 2020 1:27:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class JunctionTreeWrapper implements SessionModel, UpdaterWrapper, Unmarshallable {

    static final long serialVersionUID = 23L;

    private JunctionTreeUpdater bayesUpdater;

    private String name;

    private Parameters params;

    public JunctionTreeWrapper(final BayesImWrapper wrapper, final Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        final BayesIm bayesIm = wrapper.getBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(final DirichletBayesImWrapper wrapper, final Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        final DirichletBayesIm bayesIm = wrapper.getDirichletBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(final BayesEstimatorWrapper wrapper, final Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        final BayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(final DirichletEstimatorWrapper wrapper, final Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        final DirichletBayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    public JunctionTreeWrapper(final EmBayesEstimatorWrapper wrapper, final Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        final BayesIm bayesIm = wrapper.getEstimateBayesIm();
        setup(bayesIm, params);
    }

    private void setup(final BayesIm bayesIm, final Parameters params) {
        TetradLogger.getInstance().setConfigForClass(this.getClass());
        this.params = params;
        if (params.get("evidence", null) == null || ((Evidence) params.get("evidence", null)).isIncompatibleWith(bayesIm)) {
            this.bayesUpdater = new JunctionTreeUpdater(bayesIm);
        } else {
            this.bayesUpdater = new JunctionTreeUpdater(bayesIm,
                    (Evidence) params.get("evidence", null));
        }

        final Node node = (Node) getParams().get("variable", null);

        if (node != null) {
            final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

            TetradLogger.getInstance().log("info", "\nRow Summing Exact Updater");

            final String nodeName = node.getName();
            final int nodeIndex = bayesIm.getNodeIndex(bayesIm.getNode(nodeName));
            final double[] priors = getBayesUpdater().calculatePriorMarginals(nodeIndex);
            final double[] marginals = getBayesUpdater().calculateUpdatedMarginals(nodeIndex);

            TetradLogger.getInstance().log("details", "\nVariable = " + nodeName);
            TetradLogger.getInstance().log("details", "\nEvidence:");
            final Evidence evidence = (Evidence) getParams().get("evidence", null);
            final Proposition proposition = evidence.getProposition();

            for (int i = 0; i < proposition.getNumVariables(); i++) {
                final Node variable = proposition.getVariableSource().getVariables().get(i);
                final int category = proposition.getSingleCategory(i);

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

    private DiscreteVariable discreteVariable(final Evidence evidence, final String nodeName) {
        return evidence.getVariable(nodeName);
    }

    private String category(final Evidence evidence, final String nodeName, final int i) {
        final DiscreteVariable variable = discreteVariable(evidence, nodeName);
        return variable.getCategory(i);
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Parameters getParams() {
        return this.params;
    }

    @Override
    public ManipulatingBayesUpdater getBayesUpdater() {
        return this.bayesUpdater;
    }

}
