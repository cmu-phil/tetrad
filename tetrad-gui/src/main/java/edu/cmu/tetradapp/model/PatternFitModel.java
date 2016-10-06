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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of
 * omission and commission.  (for edge presence only, not orientation).
 *
 * @author Joseph Ramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 */
public final class PatternFitModel implements SessionModel {
    static final long serialVersionUID = 23L;
    private final Parameters parameters;

    private String name;
    private List<BayesIm> bayesIms;
    private List<BayesPm> bayesPms;
    private List<Graph> referenceGraphs;
    private DataModelList dataModelList;
    private List<SemPm> semPms;
    private List<SemIm> semIms;

    //=============================CONSTRUCTORS==========================//


    /**
     * Compares the results of a PC to a reference workbench by counting errors
     * of omission and commission. The counts can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     */
    public PatternFitModel(Simulation simulation, GeneralAlgorithmRunner algorithmRunner, Parameters params) {
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

        if (((DataSet) dataModels.get(0)).isDiscrete()) {
            bayesPms = new ArrayList<>();
            bayesIms = new ArrayList<>();

            for (int i = 0; i < dataModels.size(); i++) {
                DataSet dataSet = (DataSet) dataModels.get(0);
                Graph dag = SearchGraphUtils.dagFromPattern(graphs.get(0));
                BayesPm pm = new BayesPmWrapper(dag, new DataWrapper(dataSet)).getBayesPm();
                bayesPms.add(pm);
                bayesIms.add(estimate(dataSet, pm));
            }
        } else if (((DataSet) dataModels.get(0)).isContinuous()) {
            semPms = new ArrayList<>();
            semIms = new ArrayList<>();

            for (int i = 0; i < dataModels.size(); i++) {
                DataSet dataSet = (DataSet) dataModels.get(0);
                Graph dag = SearchGraphUtils.dagFromPattern(graphs.get(0));

                try {
                    SemPm pm = new SemPm(dag);
                    semPms.add(pm);
                    semIms.add(estimate(dataSet, pm));
                } catch (Exception e) {
                    e.printStackTrace();

                    Graph mag = SearchGraphUtils.pagToMag(graphs.get(0));
//                    Ricf.RicfResult result = estimatePag(dataSet, mag);

                    SemGraph graph = new SemGraph(mag);
                    graph.setShowErrorTerms(false);
                    SemPm pm = new SemPm(graph);
                    semPms.add(pm);
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

        try {
            MlBayesEstimator estimator = new MlBayesEstimator();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BayesIm getBayesIm(int i) {
        return bayesIms.get(i);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public List<Graph> getReferenceGraphs() {
        return referenceGraphs;
    }

    public List<BayesIm> getBayesIms() {
        return bayesIms;
    }

    public DataModelList getDataModelList() {
        return dataModelList;
    }

    public List<BayesPm> getBayesPms() {
        return bayesPms;
    }

    public List<SemPm> getSemPms() {
        return semPms;
    }

    public Parameters getParams() {
        return parameters;
    }
}


