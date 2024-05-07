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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of omission and commission.  (for edge
 * presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 * @version $Id: $Id
 */
public final class CPDAGFitModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The parameters for the check.
     */
    private final Parameters parameters;

    /**
     * The data models to be checked.
     */
    private final DataModelList dataModelList;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The Bayes IMs to be checked.
     */
    private List<BayesIm> bayesIms;

    /**
     * The Bayes PMs to be checked.
     */
    private List<BayesPm> bayesPms;

    /**
     * The SEM PMs to be checked.
     */
    private List<Graph> referenceGraphs;

    /**
     * The SEM PMs to be checked.
     */
    private List<SemPm> semPms;

    //=============================CONSTRUCTORS==========================//


    /**
     * Compares the results of a PC to a reference workbench by counting errors of omission and commission. The counts
     * can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     *
     * @param simulation      a {@link edu.cmu.tetradapp.model.Simulation} object
     * @param algorithmRunner a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param params          a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CPDAGFitModel(Simulation simulation, GeneralAlgorithmRunner algorithmRunner, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        this.parameters = params;

        DataModelList dataModels = simulation.getDataModelList();
        this.dataModelList = dataModels;
        List<Graph> graphs = algorithmRunner.getGraphs();

        if (dataModels.size() != graphs.size()) {
            throw new IllegalArgumentException("Sorry, I was expecting the same number of data sets as result graphs.");
        }

        if (dataModels.get(0).isDiscrete()) {
            this.bayesPms = new ArrayList<>();
            this.bayesIms = new ArrayList<>();

            for (int i = 0; i < dataModels.size(); i++) {
                DataSet dataSet = (DataSet) dataModels.get(0);
                Graph dag = GraphTransforms.dagFromCpdag(graphs.get(0), null);
                BayesPm pm = new BayesPmWrapper(dag, new DataWrapper(dataSet)).getBayesPm();
                this.bayesPms.add(pm);
                this.bayesIms.add(estimate(dataSet, pm));
            }
        } else if (dataModels.get(0).isContinuous()) {
            this.semPms = new ArrayList<>();
            List<SemIm> semIms = new ArrayList<>();

            for (int i = 0; i < dataModels.size(); i++) {
                DataSet dataSet = (DataSet) dataModels.get(0);
                Graph dag = GraphTransforms.dagFromCpdag(graphs.get(0), null);

                try {
                    SemPm pm = new SemPm(dag);
                    this.semPms.add(pm);
                    semIms.add(estimate(dataSet, pm));
                } catch (Exception e) {
                    e.printStackTrace();

                    Graph mag = GraphTransforms.zhangMagFromPag(graphs.get(0));
//                    Ricf.RicfResult result = estimatePag(dataSet, mag);

                    SemGraph graph = new SemGraph(mag);
                    graph.setShowErrorTerms(false);
                    SemPm pm = new SemPm(graph);
                    this.semPms.add(pm);
                    semIms.add(estimatePag(dataSet, pm));
                }
            }
        }
    }

    private BayesIm estimate(DataSet dataSet, BayesPm bayesPm) {
        Graph graph = bayesPm.getDag();

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (node.getNodeType() == NodeType.LATENT) {
                throw new IllegalArgumentException("Estimation of Bayes IM's " +
                                                   "with latents is not supported.");
            }
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        double prior = parameters.getDouble("bayesEstimatorCellPrior", 1.0);

        try {
            MlBayesEstimator estimator = new MlBayesEstimator(prior);
            return estimator.estimate(bayesPm, dataSet);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                                       "and discrete data set do not match.");
        }
    }

    private SemIm estimate(DataSet dataSet, SemPm semPm) {
        Graph graph = semPm.getGraph();

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (node.getNodeType() == NodeType.LATENT) {
                throw new IllegalArgumentException("Estimation of Bayes IM's " +
                                                   "with latents is not supported.");
            }
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        try {
            SemEstimator estimator = new SemEstimator(dataSet, semPm);
            return estimator.estimate();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                                       "and discrete data set do not match.");
        }
    }

    private SemIm estimatePag(DataSet dataSet, SemPm pm) {
        SemGraph graph = pm.getGraph();

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (node.getNodeType() == NodeType.LATENT) {
                throw new IllegalArgumentException("Estimation of Bayes IM's " +
                                                   "with latents is not supported.");
            }
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        try {
            SemOptimizer optimizer = new SemOptimizerRicf();
            SemEstimator estimator = new SemEstimator(dataSet, pm, optimizer);
            return estimator.estimate();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                                       "and discrete data set do not match.");
        }
    }

    //==============================PUBLIC METHODS========================//

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>getBayesIm.</p>
     *
     * @param i a int
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getBayesIm(int i) {
        return this.bayesIms.get(i);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * <p>Getter for the field <code>referenceGraphs</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> getReferenceGraphs() {
        return this.referenceGraphs;
    }

    /**
     * <p>Getter for the field <code>bayesIms</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<BayesIm> getBayesIms() {
        return this.bayesIms;
    }

    /**
     * <p>Getter for the field <code>dataModelList</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public DataModelList getDataModelList() {
        return this.dataModelList;
    }

    /**
     * <p>Getter for the field <code>bayesPms</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<BayesPm> getBayesPms() {
        return this.bayesPms;
    }

    /**
     * <p>Getter for the field <code>semPms</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<SemPm> getSemPms() {
        return this.semPms;
    }

    /**
     * <p>getParams.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.parameters;
    }
}


