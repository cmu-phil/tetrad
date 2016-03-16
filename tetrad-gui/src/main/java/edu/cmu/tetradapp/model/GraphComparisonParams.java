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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.session.ExecutionRestarter;
import edu.cmu.tetrad.session.SessionAdapter;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Stores a reference to a file to which records can be appended.
 *
 * @author Joseph Ramsey
 */
public class GraphComparisonParams extends SessionAdapter
        implements Params, ExecutionRestarter {
    static final long serialVersionUID = 23L;

    /**
     * The data set to which records are appended.
     *
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    /**
     * True iff the data table should be reset every time. Must be true by
     * default so dataSet will be initialized.
     *
     * @serial True, false both OK.
     */
    private boolean resetTableOnExecute = true;

    /**
     * True if the user wants to compare with the exact reference graph instead
     * of removing the latent variables.
     *
     * @serial True, false both OK.
     */
    private boolean keepLatents = false;

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
    public GraphComparisonParams() {
        newExecution();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     * <p>
     * //     * @see edu.cmu.TestSerialization
     *
     * @see TetradSerializableUtils
     */
    public static GraphComparisonParams serializableInstance() {
        return new GraphComparisonParams();
    }

    //==========================PUBLIC METHODS===========================//

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

//        ContinuousVariable twoCycleCorrect = new ContinuousVariable("TC_COR");
//        ContinuousVariable twoCycleFn = new ContinuousVariable("TC_FN");
//        ContinuousVariable twoCycleFp = new ContinuousVariable("TC_FP");

        List<Node> variables = new LinkedList<Node>();
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
//        variables.add(twoCycleCorrect);
//        variables.add(twoCycleFn);
//        variables.add(twoCycleFp);

        dataSet = new ColtDataSet(0, variables);
        dataSet.setNumberFormat(new DecimalFormat("0"));
    }

    public void addRecord(GraphUtils.GraphComparison comparison) {
        int newRow = dataSet.getNumRows();
        dataSet.setDouble(newRow, 0, comparison.getAdjCorrect());
        dataSet.setDouble(newRow, 1, comparison.getAdjFn());
        dataSet.setDouble(newRow, 2, comparison.getAdjFp());
        dataSet.setDouble(newRow, 3, comparison.getArrowptCorrect());
        dataSet.setDouble(newRow, 4, comparison.getArrowptFn());
        dataSet.setDouble(newRow, 5, comparison.getArrowptFp());
        dataSet.setDouble(newRow, 6, comparison.getAdjPrec());
        dataSet.setDouble(newRow, 7, comparison.getAdjRec());
        dataSet.setDouble(newRow, 8, comparison.getArrowptPrec());
        dataSet.setDouble(newRow, 9, comparison.getArrowptRec());
        dataSet.setDouble(newRow, 10, comparison.getShd());
    }

    public DataSet getDataSet() {
        return dataSet;
    }


    public boolean isResetTableOnExecute() {
        return resetTableOnExecute;
    }

    public void setResetTableOnExecute(boolean resetTableOnExecute) {
        this.resetTableOnExecute = resetTableOnExecute;
    }

    public boolean isKeepLatents() {
        return keepLatents;
    }

    public void setKeepLatents(boolean keepLatents) {
        this.keepLatents = keepLatents;
    }

    public void setReferenceGraphName(String name) {
        this.referenceGraphName = name;
    }

    public String getReferenceGraphName() {
        return referenceGraphName;
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
    }

    public void setTargetGraphName(String targetGraphName) {
        this.targetGraphName = targetGraphName;
    }

    public String getTargetGraphName() {
        return targetGraphName;
    }
}





