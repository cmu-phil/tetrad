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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores time series data as a list of continuous columns.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class TimeSeriesData implements DataModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data.
     */
    private final Matrix data2;
    /**
     * The names of the variables.
     */
    private final List<String> varNames;
    /**
     * The name of the data.
     */
    private String name;
    /**
     * The knowledge about the data.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a new time series data contains for the given row-major data array and the given list of variables.
     * Each row of the data, data[i], contains a measured for each variable (in order) for a particular time. The series
     * of times is in increasing order.
     *
     * @param matrix   a {@link edu.cmu.tetrad.util.Matrix} object
     * @param varNames a {@link java.util.List} object
     */
    public TimeSeriesData(Matrix matrix, List<String> varNames) {
        if (matrix == null) {
            throw new NullPointerException("Data must not be null.");
        }

        if (varNames == null) {
            throw new NullPointerException("Variables must not be null.");
        }
        for (int i = 0; i < varNames.size(); i++) {
            if (varNames.get(i) == null) {
                throw new NullPointerException(
                        "Variable at index " + i + "is null.");
            }
        }
        this.data2 = matrix;
        if (varNames.size() != matrix.getNumColumns()) {
            throw new IllegalArgumentException(
                    "Number of columns in the data " +
                    "must match the number of variables.");
        }
        this.varNames = varNames;
        this.name = "Time Series Data";
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.TimeSeriesData} object
     */
    public static TimeSeriesData serializableInstance() {
        List<String> varNames = new ArrayList<>();
        varNames.add("X");
        varNames.add("Y");
        return new TimeSeriesData(new Matrix(2, 2), varNames);
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
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContinuous() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDiscrete() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMixed() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel copy() {
        return null;
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        List<String> varNames = getVariableNames();
        List<Node> vars = new LinkedList<>();

        for (String varName : varNames) {
            vars.add(new ContinuousVariable(varName));
        }

        return vars;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        System.out.println();

        return this.knowledge.copy();
    }

    /**
     * {@inheritDoc}
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return (a copy of) the List of Variables for the data set, in the order of their columns.
     */
    public List<String> getVariableNames() {
        return this.varNames;
    }

    /**
     * <p>getData.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getData() {
        return this.data2.copy();
    }

    /**
     * <p>getNumTimePoints.</p>
     *
     * @return an int
     */
    public int getNumTimePoints() {
        return getData().getNumRows();
    }

    /**
     * <p>getNumVars.</p>
     *
     * @return an int
     */
    public int getNumVars() {
        return getVariableNames().size();
    }

    /**
     * <p>getDatum.</p>
     *
     * @param row an int
     * @param col an int
     * @return a double
     */
    public double getDatum(int row, int col) {
        return this.data2.get(row, col);
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
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

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
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
}




