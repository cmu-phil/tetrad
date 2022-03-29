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

import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Memorable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class BayesImWrapper implements SessionModel, Memorable {

    static final long serialVersionUID = 23L;

    private int numModels = 1;
    private int modelIndex;
    private String modelSourceName;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private List<BayesIm> bayesIms;

    //===========================CONSTRUCTORS===========================//
    public BayesImWrapper(final BayesPmWrapper bayesPmWrapper, final BayesImWrapper oldBayesImwrapper, final Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        final BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());
        final BayesIm oldBayesIm = oldBayesImwrapper.getBayesIm();

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            setBayesIm(bayesPm, oldBayesIm, MlBayesIm.MANUAL);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            setBayesIm(bayesPm, oldBayesIm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            setBayesIm(new MlBayesIm(bayesPm, MlBayesIm.RANDOM));
        }
//        log(bayesIm);
    }

    private void setBayesIm(final BayesPm bayesPm, final BayesIm oldBayesIm, final int manual) {
        this.bayesIms = new ArrayList<>();
        this.bayesIms.add(new MlBayesIm(bayesPm, oldBayesIm, manual));
    }

    public BayesImWrapper(final Simulation simulation) {
        List<BayesIm> bayesIms = null;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        final edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof BayesNetSimulation)) {
            throw new IllegalArgumentException("That was not a discrete Bayes net simulation.");
        }

        bayesIms = ((BayesNetSimulation) _simulation).getBayesIms();

        if (bayesIms == null) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        this.bayesIms = bayesIms;

        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }

//    public BayesImWrapper(BayesEstimatorWrapper wrapper, Parameters parameters) {
//        if (wrapper == null) {
//            throw new NullPointerException();
//        }
//        setBayesIm(wrapper.getEstimatedBayesIm());
////        log(bayesIm);
//    }

//    public BayesImWrapper(DirichletEstimatorWrapper wrapper, Parameters parameters) {
//        if (wrapper == null) {
//            throw new NullPointerException();
//        }
//        setBayesIm(wrapper.getEstimatedBayesIm());
////        log(bayesIm);
//    }

//    public BayesImWrapper(DirichletBayesImWrapper wrapper, Parameters parameters) {
//        if (wrapper == null) {
//            throw new NullPointerException();
//        }
//        setBayesIm(new MlBayesIm(wrapper.getDirichletBayesIm()));
////        log(bayesIm);
//    }

    public BayesImWrapper(final RowSummingExactWrapper wrapper, final Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        setBayesIm(wrapper.getBayesUpdater().getUpdatedBayesIm());
//        log(bayesIm);
    }

    public BayesImWrapper(final CptInvariantUpdaterWrapper wrapper, final Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        setBayesIm(wrapper.getBayesUpdater().getUpdatedBayesIm());
//        log(bayesIm);
    }

    public BayesImWrapper(final ApproximateUpdaterWrapper wrapper, final Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        setBayesIm(wrapper.getBayesUpdater().getUpdatedBayesIm());
//        log(bayesIm);
    }

    public BayesImWrapper(final BayesPmWrapper bayesPmWrapper, final Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        final BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            setBayesIm(new MlBayesIm(bayesPm));
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            setBayesIm(new MlBayesIm(bayesPm, MlBayesIm.RANDOM));
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            setBayesIm(new MlBayesIm(bayesPm, MlBayesIm.RANDOM));
        }
//        log(bayesIm);
    }

//    public BayesImWrapper(BayesImWrapper bayesImWrapper) {
//        if (bayesImWrapper == null) {
//            throw new NullPointerException();
//        }
//
//        setBayesIm(new MlBayesIm(bayesImWrapper.getBayesIm()));
////        log(bayesIm);
//    }


    public BayesImWrapper(final BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }
        setBayesIm(new MlBayesIm(bayesIm));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static BayesImWrapper serializableInstance() {
        return new BayesImWrapper(BayesPmWrapper.serializableInstance(),
                new Parameters());
    }

    //=============================PUBLIC METHODS=========================//
    public BayesIm getBayesIm() {
        return this.bayesIms.get(getModelIndex());
    }

    public Graph getGraph() {
        return getBayesIm().getBayesPm().getDag();
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Graph getSourceGraph() {
        return getGraph();
    }

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    public void setBayesIm(final BayesIm bayesIm) {
        this.bayesIms = new ArrayList<>();
        this.bayesIms.add(bayesIm);
    }

    public int getNumModels() {
        return this.numModels;
    }

    public int getModelIndex() {
        return this.modelIndex;
    }

    public String getModelSourceName() {
        return this.modelSourceName;
    }

    public void setModelIndex(final int modelIndex) {
        this.modelIndex = modelIndex;
    }

    //============================== private methods ============================//
//    private void log(BayesIm im) {
//        TetradLogger.getInstance().log("info", "Maximum likelihood Bayes IM");
//        TetradLogger.getInstance().log("im", im.toString());
//    }

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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

}
