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
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Memorable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class BayesImWrapper implements SessionModel, Memorable, GraphSource, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private BayesIm bayesIm;

    //===========================CONSTRUCTORS===========================//

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
            this.bayesIm = new MlBayesIm(bayesPm, oldBayesIm, MlBayesIm.MANUAL);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            this.bayesIm = new MlBayesIm(bayesPm, oldBayesIm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            this.bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        }
        log(bayesIm);
    }

    public BayesImWrapper(Simulation simulation) {
        BayesIm bayesIm = null;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof BayesNetSimulation)) {
            throw new IllegalArgumentException("The given simulation is not a linear, Gaussain SEM simulation.");
        }

        bayesIm = ((BayesNetSimulation) _simulation).getBayesIm();

        if (bayesIm == null) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        this.bayesIm = bayesIm;
    }

    public BayesImWrapper(BayesEstimatorWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getEstimatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapper(DirichletEstimatorWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getEstimatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapper(DirichletBayesImWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = new MlBayesIm(wrapper.getDirichletBayesIm());
        log(bayesIm);
    }

    public BayesImWrapper(RowSummingExactWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapper(CptInvariantUpdaterWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapper(ApproximateUpdaterWrapper wrapper, Parameters parameters) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapper(BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            this.bayesIm = new MlBayesIm(bayesPm);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            this.bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            this.bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        }
        log(bayesIm);
    }

    public BayesImWrapper(BayesImWrapper bayesImWrapper) {
        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        this.bayesIm = new MlBayesIm(bayesImWrapper.getBayesIm());
        log(bayesIm);
    }

    public BayesImWrapper() {
        Dag graph = new Dag();
        BayesPm pm = new BayesPm(graph);
        this.bayesIm = new MlBayesIm(pm);
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
        return this.bayesIm;
    }


    public Graph getGraph() {
        return bayesIm.getBayesPm().getDag();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //============================== private methods ============================//

    private void log(BayesIm im) {
        TetradLogger.getInstance().log("info", "Maximum likelihood Bayes IM");
        TetradLogger.getInstance().log("im", im.toString());
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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (bayesIm == null) {
            throw new NullPointerException();
        }
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


    public void setBayesIm(BayesIm bayesIm) {
        this.bayesIm = bayesIm;
    }
}






