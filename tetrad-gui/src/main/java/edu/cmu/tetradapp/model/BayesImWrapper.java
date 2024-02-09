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
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesImWrapper implements SessionModel, Memorable {

    @Serial
    private static final long serialVersionUID = 23L;
    // The number of models in the simulation.
    private int numModels = 1;
    // The index of the model to be used.
    private int modelIndex;
    // The name of the model source.
    private String modelSourceName;
    // The name of the Bayes IM.
    private String name;
    // The Bayes IM.
    private List<BayesIm> bayesIms;

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs a new BayesImWrapper.
     *
     * @param bayesPmWrapper    the Bayes Pm wrapper
     * @param oldBayesImwrapper the old Bayes Im wrapper
     * @param params            the parameters
     */
    public BayesImWrapper(BayesPmWrapper bayesPmWrapper, BayesImWrapper oldBayesImwrapper, Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());
        BayesIm oldBayesIm = oldBayesImwrapper.getBayesIm();

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            setBayesIm(bayesPm, oldBayesIm, MlBayesIm.MANUAL);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            setBayesIm(bayesPm, oldBayesIm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            setBayesIm(new MlBayesIm(bayesPm, MlBayesIm.RANDOM));
        }
    }

    /**
     * Constructs a new BayesImWrapper.
     *
     * @param simulation the simulation
     */
    public BayesImWrapper(Simulation simulation) {
        List<BayesIm> bayesIms = null;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

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

    /**
     * Constructs a new BayesImWrapper for a RowSummingExactUpdaterWrapper.
     *
     * @param wrapper    the wrapper
     * @param parameters the parameters
     */
    public BayesImWrapper(RowSummingExactWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        setBayesIm(wrapper.getBayesUpdater().getUpdatedBayesIm());
    }

    /**
     * Constructs a new BayesImWrapper for a CptInvariantUpdaterWrapper.
     *
     * @param wrapper    the wrapper
     * @param parameters the parameters
     */
    public BayesImWrapper(CptInvariantUpdaterWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        setBayesIm(wrapper.getBayesUpdater().getUpdatedBayesIm());
    }

    /**
     * <p>Constructor for BayesImWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.ApproximateUpdaterWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesImWrapper(ApproximateUpdaterWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        setBayesIm(wrapper.getBayesUpdater().getUpdatedBayesIm());
    }

    /**
     * <p>Constructor for BayesImWrapper.</p>
     *
     * @param bayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesImWrapper(BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            setBayesIm(new MlBayesIm(bayesPm));
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            setBayesIm(new MlBayesIm(bayesPm, MlBayesIm.RANDOM));
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            setBayesIm(new MlBayesIm(bayesPm, MlBayesIm.RANDOM));
        }
    }

    /**
     * Constructs a new BayesImWrapper for a BayesIm.
     *
     * @param bayesIm the BayesIm
     */
    public BayesImWrapper(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }
        setBayesIm(new MlBayesIm(bayesIm));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     * @return a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     */
    public static BayesImWrapper serializableInstance() {
        return new BayesImWrapper(BayesPmWrapper.serializableInstance(),
                new Parameters());
    }

    /**
     * Returns the BayesIm.
     *
     * @return the BayesIm
     */
    public BayesIm getBayesIm() {
        return this.bayesIms.get(getModelIndex());
    }

    /**
     * Sets the BayesIm.
     *
     * @param bayesIm the BayesIm
     */
    public void setBayesIm(BayesIm bayesIm) {
        this.bayesIms = new ArrayList<>();
        this.bayesIms.add(bayesIm);
    }

    /**
     * Returns the graph.
     *
     * @return the graph
     */
    public Graph getGraph() {
        return getBayesIm().getBayesPm().getDag();
    }

    /**
     * Returns the name of the BayesIm.
     *
     * @return the name of the BayesIm
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     *
     * Sets the name of the BayesIm.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the source graph.
     *
     * @return the source graph
     */
    public Graph getSourceGraph() {
        return getGraph();
    }

    /**
     * Returns the result graph.
     *
     * @return the result graph
     */
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * Returns the variable names.
     *
     * @return the variable names
     */
    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    /**
     * Returns the variables.
     *
     * @return the variables
     */
    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    /**
     * Returns the number of models.
     *
     * @return the number of models
     */
    public int getNumModels() {
        return this.numModels;
    }

    /**
     * Returns the index of the model to be used.
     *
     * @return the index of the model to be used
     */
    public int getModelIndex() {
        return this.modelIndex;
    }

    /**
     * Sets the index of the model to be used.
     *
     * @param modelIndex the index of the model to be used
     */
    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }

    /**
     * Returns the name of the model source.
     *
     * @return the name of the model source
     */
    public String getModelSourceName() {
        return this.modelSourceName;
    }

    //============================== private methods ============================//

    private void setBayesIm(BayesPm bayesPm, BayesIm oldBayesIm, int manual) {
        this.bayesIms = new ArrayList<>();
        this.bayesIms.add(new MlBayesIm(bayesPm, oldBayesIm, manual));
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}
