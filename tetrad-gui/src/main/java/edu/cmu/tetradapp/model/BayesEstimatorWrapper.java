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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
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
public class BayesEstimatorWrapper implements SessionModel {

    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private BayesIm bayesIm;

    /**
     * @serial Cannot be null.
     */
    private DataSet dataSet;
    private DataWrapper dataWrapper;

    private int numModels = 1;
    private int modelIndex = 0;
    private List<BayesIm> bayesIms = new ArrayList<>();
    
    //=================================CONSTRUCTORS========================//
    public BayesEstimatorWrapper(DataWrapper dataWrapper,
            BayesPmWrapper bayesPmWrapper) {

        if (dataWrapper == null) {
            throw new NullPointerException(
                    "BayesDataWrapper must not be null.");
        }

        this.dataWrapper = dataWrapper;
        
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null");
        }
        
        DataModelList dataModel = dataWrapper.getDataModelList();
        
        if (dataModel != null) {
            for (int i = 0; i < dataWrapper.getDataModelList().size(); i++) {
                DataModel model = dataWrapper.getDataModelList().get(i);
            	DataSet dataSet = (DataSet) model;
            	bayesPmWrapper.setModelIndex(i);
            	BayesPm bayesPm = bayesPmWrapper.getBayesPm();
            	
            	estimate(dataSet, bayesPm);
            	bayesIms.add(this.bayesIm);
            }
            
            this.bayesIm = bayesIms.get(0);
            log(bayesIm);

        } else {
            throw new IllegalArgumentException("Data must consist of discrete data sets.");       	
        }

        this.name = bayesPmWrapper.getName();
        this.numModels = bayesIms.size();
        this.modelIndex = 0;
        this.bayesIm = bayesIms.get(modelIndex);
        DataModel model = dataModel.get(modelIndex);
        this.dataSet = (DataSet)model;
    }

    public BayesEstimatorWrapper(DataWrapper dataWrapper,
                                 BayesImWrapper bayesImWrapper) {
    	this(dataWrapper, new BayesPmWrapper(bayesImWrapper));
    }
    
    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //==============================PUBLIC METHODS========================//
    public BayesIm getEstimatedBayesIm() {
    	return bayesIm;
    }

    public void setBayesIm(BayesIm bayesIm) {
    	bayesIms.clear();
        bayesIms.add(bayesIm);
    }

    public DataSet getDataSet() {
        return dataSet;
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

	public int getNumModels() {
		return numModels;
	}

	public void setNumModels(int numModels) {
		this.numModels = numModels;
	}

	public int getModelIndex() {
		return modelIndex;
	}

	public void setModelIndex(int modelIndex) {
		this.modelIndex = modelIndex;
		this.bayesIm = bayesIms.get(modelIndex);
		
		DataModel dataModel = dataWrapper.getDataModelList();

		this.dataSet = (DataSet) ((DataModelList)dataModel).get(modelIndex);
		
	}

    //======================== Private Methods ======================//
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

    private void log(BayesIm im) {
        TetradLogger.getInstance().log("info", "ML estimated Bayes IM.");
        TetradLogger.getInstance().log("im", im.toString());
    }

    private void estimate(DataSet dataSet, BayesPm bayesPm) {
        Graph graph = bayesPm.getDag();

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (node.getNodeType() == NodeType.LATENT) {
                throw new IllegalArgumentException("Estimation of Bayes IM's "
                        + "with latents is not supported.");
            }
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        try {
            MlBayesEstimator estimator = new MlBayesEstimator();
            this.bayesIm = estimator.estimate(bayesPm, dataSet);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM "
                    + "and discrete data set do not match.");
        }
    }

}
