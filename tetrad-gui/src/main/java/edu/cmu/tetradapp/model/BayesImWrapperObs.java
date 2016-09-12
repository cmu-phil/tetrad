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
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.bayes.MlBayesImObs;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Memorable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

///////////////////////////////////////////////////////////
// Wraps a Bayes Im (observed variables only) for use 
// in the Tetrad application.
//
// @author Joseph Ramsey
///////////////////////////////////////////////////////////

public class BayesImWrapperObs implements SessionModel, Memorable, GraphSource, KnowledgeBoxInput {
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

	/////////////////////////////////////////////////////////////////
	// Disregard all other methods of instantiating an IM
	// Only constructed from a PM or from another BayesIm
	//
	// If from a regular BayesIm, the new probability values are 
	// the marginalized values of the allowUnfaithfulness probability values in
	// the old BayesIm, stored in a JPD
	//

    public BayesImWrapperObs(BayesPmWrapper bayesPmWrapper, 
							 BayesImWrapperObs oldBayesImwrapper, 
							 Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());
        BayesIm oldBayesIm = oldBayesImwrapper.getBayesIm();

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            this.bayesIm = new MlBayesImObs(bayesPm, oldBayesIm, MlBayesIm.MANUAL);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            this.bayesIm = new MlBayesImObs(bayesPm, oldBayesIm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            this.bayesIm = new MlBayesImObs(bayesPm, MlBayesIm.RANDOM);
        }
		
        log(bayesIm);
    }

	/*
    public BayesImWrapperObs(BayesEstimatorWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getEstimatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapperObs(DirichletEstimatorWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getEstimatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapperObs(DirichletBayesImWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = new MlBayesIm(wrapper.getDirichletBayesIm());
        log(bayesIm);
    }

    public BayesImWrapperObs(RowSummingExactWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapperObs(CptInvariantUpdaterWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        log(bayesIm);
    }

    public BayesImWrapperObs(ApproximateUpdaterWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        log(bayesIm);
    }
	 */
	
    public BayesImWrapperObs(BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            this.bayesIm = new MlBayesImObs(bayesPm);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            this.bayesIm = new MlBayesImObs(bayesPm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            this.bayesIm = new MlBayesImObs(bayesPm, MlBayesIm.RANDOM);
        }

        log(bayesIm);
    }
	
	// from regular allowUnfaithfulness BayesIm
	// marginalize the probability values from the old BayesIm
    public BayesImWrapperObs(BayesImWrapper bayesImWrapper) {
        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }
		
        this.bayesIm = new MlBayesImObs(bayesImWrapper.getBayesIm());
		        
		log(bayesIm);
    }

	// from BayesIm with only observed variables
    public BayesImWrapperObs(BayesImWrapperObs bayesImWrapperObs) {
        if (bayesImWrapperObs == null) {
            throw new NullPointerException();
        }

        this.bayesIm = new MlBayesImObs(bayesImWrapperObs.getBayesIm());

        log(bayesIm);
    }

//	// brand new BayesIm
//    public BayesImWrapperObs() {
//        Dag graph = new Dag();
//        BayesPm pm = new BayesPm(graph);
//
//        this.bayesIm = new MlBayesImObs(pm);
//
//        log(bayesIm);
//    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
	 public static BayesImWrapperObs serializableInstance() {
        return new BayesImWrapperObs(BayesImWrapper.serializableInstance());
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
        TetradLogger.getInstance().log("info", 
						"Maximum likelihood Bayes IM: Observed Variables Only");
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






