/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.ExecutionRestarter;
import edu.cmu.tetradapp.session.SessionAdapter;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a reference to a file to which records can be appended.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphComparisonParams extends SessionAdapter
        implements ExecutionRestarter {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set to which records are appended.
     *
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    /**
     * True iff the data table should be reset every time. Must be true by default so dataSet will be initialized.
     *
     * @serial True, false both OK.
     */
    private boolean resetTableOnExecute = true;

    /**
     * True if the user wants to compare with the exact reference graph instead of removing the latent variables.
     *
     * @serial True, false both OK.
     */
    private boolean keepLatents;

    /**
     * The name of the session model that has the true graph in it.
     *
     * @serial Can be null.
     */
    private String referenceGraphName;


    /**
     * The name of the session model that has the true graph in it.
     *
     * @serial Can be null.
     */
    private String targetGraphName;

    //===========================CONSTRUCTORS============================//

    /**
     * Constructs a getMappings object with no file set.
     */
    private GraphComparisonParams() {
        newExecution();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.GraphComparisonParams} object
     */
    public static GraphComparisonParams serializableInstance() {
        return new GraphComparisonParams();
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * <p>newExecution.</p>
     */
    public final void newExecution() {
        ContinuousVariable adjCorrect = new ContinuousVariable("ADJ_COR");
        ContinuousVariable adjFn = new ContinuousVariable("ADJ_FN");
        ContinuousVariable adjFp = new ContinuousVariable("ADJ_FP");

        ContinuousVariable arrowptCorrect = new ContinuousVariable("AHD_COR");
        ContinuousVariable arrowptFn = new ContinuousVariable("AHD_FN");
        ContinuousVariable arrowptFp = new ContinuousVariable("AHD_FP");

        ContinuousVariable adjPrec = new ContinuousVariable("ADJ_PREC");
        ContinuousVariable adjRec = new ContinuousVariable("ADJ_REC");
        ContinuousVariable arrowptPrec = new ContinuousVariable("ARROWPT_PREC");
        ContinuousVariable arrowptRec = new ContinuousVariable("ARROWPT_REC");
        ContinuousVariable shd = new ContinuousVariable("SHD");

        List<Node> variables = new LinkedList<>();
        variables.add(adjCorrect);
        variables.add(adjFn);
        variables.add(adjFp);
        variables.add(arrowptCorrect);
        variables.add(arrowptFn);
        variables.add(arrowptFp);
        variables.add(adjPrec);
        variables.add(adjRec);
        variables.add(arrowptPrec);
        variables.add(arrowptRec);
        variables.add(shd);

        this.dataSet = new BoxDataSet(new VerticalDoubleDataBox(0, variables.size()), variables);
        this.dataSet.setNumberFormat(new DecimalFormat("0"));
    }

    /**
     * <p>addRecord.</p>
     *
     * @param comparison a {@link edu.cmu.tetrad.graph.GraphUtils.GraphComparison} object
     */
    public void addRecord(GraphUtils.GraphComparison comparison) {
        int newRow = this.dataSet.getNumRows();
        this.dataSet.setDouble(newRow, 0, comparison.getAdjCor());
        this.dataSet.setDouble(newRow, 1, comparison.getAdjFn());
        this.dataSet.setDouble(newRow, 2, comparison.getAdjFp());
        this.dataSet.setDouble(newRow, 3, comparison.getAhdCor());
        this.dataSet.setDouble(newRow, 4, comparison.getAhdFn());
        this.dataSet.setDouble(newRow, 5, comparison.getAhdFp());
        this.dataSet.setDouble(newRow, 6, comparison.getAdjPrec());
        this.dataSet.setDouble(newRow, 7, comparison.getAdjRec());
        this.dataSet.setDouble(newRow, 8, comparison.getAhdPrec());
        this.dataSet.setDouble(newRow, 9, comparison.getAhdRec());
        this.dataSet.setDouble(newRow, 10, comparison.getShd());
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }


    /**
     * <p>isResetTableOnExecute.</p>
     *
     * @return a boolean
     */
    public boolean isResetTableOnExecute() {
        return this.resetTableOnExecute;
    }

    /**
     * <p>Setter for the field <code>resetTableOnExecute</code>.</p>
     *
     * @param resetTableOnExecute a boolean
     */
    public void setResetTableOnExecute(boolean resetTableOnExecute) {
        this.resetTableOnExecute = resetTableOnExecute;
    }

    /**
     * <p>isKeepLatents.</p>
     *
     * @return a boolean
     */
    public boolean isKeepLatents() {
        return this.keepLatents;
    }

    /**
     * <p>Setter for the field <code>keepLatents</code>.</p>
     *
     * @param keepLatents a boolean
     */
    public void setKeepLatents(boolean keepLatents) {
        this.keepLatents = keepLatents;
    }

    /**
     * <p>Getter for the field <code>referenceGraphName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getReferenceGraphName() {
        return this.referenceGraphName;
    }

    /**
     * <p>Setter for the field <code>referenceGraphName</code>.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setReferenceGraphName(String name) {
        this.referenceGraphName = name;
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

    /**
     * <p>Getter for the field <code>targetGraphName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getTargetGraphName() {
        return this.targetGraphName;
    }

    /**
     * <p>Setter for the field <code>targetGraphName</code>.</p>
     *
     * @param targetGraphName a {@link java.lang.String} object
     */
    public void setTargetGraphName(String targetGraphName) {
        this.targetGraphName = targetGraphName;
    }
}





