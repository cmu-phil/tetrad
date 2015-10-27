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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemEstimator;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;

import javax.swing.text.Document;
import javax.xml.soap.Text;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;


/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedSemEstimatorWrapper implements SessionModel, GraphSource, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    private GeneralizedSemPm semPm = null;

    private DataSet data = null;

    /**
     * True just in case errors should be shown in the interface.
     */
    private boolean showErrors;
    private GeneralizedSemIm estIm = null;
    private String report = "";

    //==============================CONSTRUCTORS==========================//

    public GeneralizedSemEstimatorWrapper(GeneralizedSemPmWrapper semPm, DataWrapper data) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must not be null.");
        }

        this.semPm = semPm.getSemPm();
        this.data = (DataSet) data.getSelectedDataModel();

        execute();
    }

    public void execute() {
        GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        estIm = estimator.estimate(this.semPm, this.data);
        this.report = estimator.getReport();
    }

    public static Node serializableInstance() {return new GraphNode("X");} //TODO


    //============================PUBLIC METHODS=========================//

    public GeneralizedSemIm getSemIm() {
        return this.estIm;
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

    public Graph getGraph() {
        return semPm.getGraph();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isShowErrors() {
        return showErrors;
    }

    public void setShowErrors(boolean showErrors) {
        this.showErrors = showErrors;
    }

    //======================= Private methods ====================//

    private void log(GeneralizedSemIm im){
        TetradLogger.getInstance().log("info", "Generalized SEM IM");
        TetradLogger.getInstance().log("im", im.toString());
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


    /**
     * The wrapped SemPm.
     *
     * @serial Cannot be null.
     */
    public GeneralizedSemPm getSemPm() {
        return semPm;
    }

    public void setSemPm(GeneralizedSemPm semPm) {
        this.semPm = semPm;
    }

    public String getReport() {
        return report;
    }
}


