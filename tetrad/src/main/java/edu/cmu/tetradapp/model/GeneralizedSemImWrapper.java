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

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.session.SessionModel;
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
public class GeneralizedSemImWrapper implements SessionModel, GraphSource, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * The wrapped SemPm.
     *
     * @serial Cannot be null.
     */
    GeneralizedSemIm semIm = null;

    /**
     * True just in case errors should be shown in the interface.
     */
    private boolean showErrors;

    //==============================CONSTRUCTORS==========================//

    private GeneralizedSemImWrapper(GeneralizedSemPm semPm) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must not be null.");
        }

        semIm = new GeneralizedSemIm(semPm);
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemImWrapper(GeneralizedSemPmWrapper wrapper) {
        this(wrapper.getSemPm());
    }

    public GeneralizedSemImWrapper(GeneralizedSemPmWrapper genSemPm, SemImWrapper imWrapper) {
        semIm = new GeneralizedSemIm(genSemPm.getSemPm(), imWrapper.getSemIm());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static GeneralizedSemImWrapper serializableInstance() {
        return new GeneralizedSemImWrapper(GeneralizedSemPmWrapper.serializableInstance());
    }

    //============================PUBLIC METHODS=========================//

    public GeneralizedSemIm getSemIm() {
        return this.semIm;
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

        if (semIm == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return semIm.getSemPm().getGraph();
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


}


