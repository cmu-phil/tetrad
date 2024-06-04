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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StandardizedSemImWrapper implements KnowledgeBoxInput {

    private static final long serialVersionUID = 23L;
    /**
     * @serial Cannot be null.
     */
    private final StandardizedSemIm standardizedSemIm;
    /**
     * @serial Can be null.
     */
    private String name;
    /**
     * True just in case errors should be shown in the interface.
     */
    private boolean showErrors;

    //============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for StandardizedSemImWrapper.</p>
     *
     * @param semImWrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public StandardizedSemImWrapper(SemImWrapper semImWrapper, Parameters parameters) {
        if (semImWrapper == null) {
            throw new NullPointerException();
        }

        this.standardizedSemIm = new StandardizedSemIm(semImWrapper.getSemIm(), parameters);
        log(this.standardizedSemIm);
    }

    /**
     * <p>Constructor for StandardizedSemImWrapper.</p>
     *
     * @param semPmWrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public StandardizedSemImWrapper(SemPmWrapper semPmWrapper, Parameters parameters) {
        if (semPmWrapper == null) {
            throw new NullPointerException();
        }

        SemIm semIm = new SemIm(semPmWrapper.getSemPm());
        this.standardizedSemIm = new StandardizedSemIm(semIm, parameters);
        log(this.standardizedSemIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
//        return new StandardizedSemImWrapper(SemImWrapper.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * <p>Getter for the field <code>standardizedSemIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.StandardizedSemIm} object
     */
    public StandardizedSemIm getStandardizedSemIm() {
        return this.standardizedSemIm;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.standardizedSemIm.getSemPm().getGraph();
    }

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
     * <p>isShowErrors.</p>
     *
     * @return a boolean
     */
    public boolean isShowErrors() {
        return this.showErrors;
    }

    /**
     * <p>Setter for the field <code>showErrors</code>.</p>
     *
     * @param showErrors a boolean
     */
    public void setShowErrors(boolean showErrors) {
        this.showErrors = showErrors;
    }

    //======================== Private methods =======================//

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return getGraph();
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    private void log(StandardizedSemIm pm) {
        TetradLogger.getInstance().log("Standardized SEM IM");
        String message = pm.toString();
        TetradLogger.getInstance().log(message);
    }
}
