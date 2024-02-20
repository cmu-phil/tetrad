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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Unmarshallable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.text.NumberFormat;

/**
 * Jan 21, 2020 1:27:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class JunctionTreeWrapper implements SessionModel, UpdaterWrapper, Unmarshallable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The updater.
     */
    private JunctionTreeUpdater bayesUpdater;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The parameters.
     */
    private Parameters params;

    /**
     * <p>Constructor for JunctionTreeWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public JunctionTreeWrapper(BayesImWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        BayesIm bayesIm = wrapper.getBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for JunctionTreeWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public JunctionTreeWrapper(DirichletBayesImWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        DirichletBayesIm bayesIm = wrapper.getDirichletBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for JunctionTreeWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public JunctionTreeWrapper(BayesEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        BayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for JunctionTreeWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public JunctionTreeWrapper(DirichletEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        DirichletBayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for JunctionTreeWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.EmBayesEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
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
            this.bayesUpdater = new JunctionTreeUpdater(bayesIm);
        } else {
            this.bayesUpdater = new JunctionTreeUpdater(bayesIm,
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Parameters getParams() {
        return this.params;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ManipulatingBayesUpdater getBayesUpdater() {
        return this.bayesUpdater;
    }

}
