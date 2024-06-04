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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndTestGSquare;
import edu.cmu.tetrad.search.test.IndTestRegression;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IndTestType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract subclass for Markov Blanket searches. This should be used so that the markov blanket search can also be used
 * as input for a search box.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public abstract class AbstractMBSearchRunner extends DataWrapper implements MarkovBlanketSearchRunner {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The source data model.
     */
    private final DataSet source;
    /**
     * The search params.
     */
    private final Parameters params;
    /**
     * Data model.
     */
    private DataSet dataModel;
    /**
     * The variables in the markov blanket.
     */
    private List<Node> variables;
    /**
     * The name of the search algorithm
     */
    private String searchName;

    /**
     * Conctructs the abstract search runner.
     *
     * @param source - The source data the search is acting on.
     * @param params - The params for the search.
     * @serial may be null.
     */
    AbstractMBSearchRunner(DataModel source, Parameters params) {
        super(AbstractMBSearchRunner.castData(source));
        if (source == null) {
            throw new NullPointerException("The source data was null.");
        }
        if (params == null) {
            throw new NullPointerException("Search params were null.");
        }
        this.params = params;
        this.source = (DataSet) source;
    }

    private static DataSet castData(DataModel model) {
        if (model instanceof DataSet) {
            return (DataSet) model;
        }
        throw new IllegalStateException("The data model must be a rectangular data set.");
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return the parameters for the search.
     */
    public Parameters getParams() {
        return this.params;
    }

    /**
     * <p>getDataModelForMarkovBlanket.</p>
     *
     * @return the data model for the variables in the Markov blanket or null if the runner has not executed yet.
     */
    public DataSet getDataModelForMarkovBlanket() {
        return this.dataModel;
    }

    /**
     * <p>getMarkovBlanket.</p>
     *
     * @return the variables in the MB searhc.
     */
    public List<Node> getMarkovBlanket() {
        return this.variables;
    }

    /**
     * <p>Getter for the field <code>source</code>.</p>
     *
     * @return the source of the search.
     */
    public DataSet getSource() {
        return this.source;
    }

    /**
     * <p>Getter for the field <code>searchName</code>.</p>
     *
     * @return the search name, or "Markov Blanket Search" by default.
     */
    public String getSearchName() {
        if (this.searchName == null) {
            return "Markov Blanket Search";
        }
        return this.searchName;
    }

    //============== Protected methods ===============================//

    /**
     * {@inheritDoc}
     */
    public void setSearchName(String n) {
        this.searchName = n;
    }

    /**
     * Makes sure the data is not empty.
     */
    void validate() {
        if (this.source.getNumColumns() == 0 || this.source.getNumRows() == 0) {
            throw new IllegalStateException("Cannot run algorithm on an empty data set.");
        }
    }

    /**
     * Sets the results of the search.
     */
    void setSearchResults(List<Node> nodes) {
        if (nodes == null) {
            throw new NullPointerException("nodes were null.");
        }
        this.variables = new ArrayList<>(nodes);
        if (nodes.isEmpty()) {
            this.dataModel = new BoxDataSet(new DoubleDataBox(this.source.getNumRows(), nodes.size()), nodes);
        } else {
            this.dataModel = this.source.subsetColumns(nodes);
        }
        this.setDataModel(this.dataModel);
    }

    //==================== Private Methods ===========================//

    /**
     * @return an appropriate independence test given the type of data set and values in the params.
     */
    IndependenceTest getIndependenceTest() {
        IndTestType type = (IndTestType) this.params.get("indTestType", IndTestType.FISHER_Z);
        if (this.source.isContinuous() || this.source.getNumColumns() == 0) {
            if (IndTestType.FISHER_Z == type) {
                return new IndTestFisherZ(this.source, this.params.getDouble("alpha", 0.001));
            }
//            if (IndTestType.FISHER_ZD == type) {
//                IndTestFisherZ test = new IndTestFisherZ(this.source, this.params.getDouble("alpha", 0.001));
////                test.setUsePseudoinverse(true);
//                return test;
//            }
            if (IndTestType.LINEAR_REGRESSION == type) {
                return new IndTestRegression(this.source, this.params.getDouble("alpha", 0.001));
            } else {
                this.params.set("indTestType", IndTestType.FISHER_Z);
                return new IndTestFisherZ(this.source, this.params.getDouble("alpha", 0.001));
            }
        }
        if (this.source.isDiscrete()) {
            if (IndTestType.G_SQUARE == type) {
                return new IndTestGSquare(this.source, this.params.getDouble("alpha", 0.001));
            }
            if (IndTestType.CHI_SQUARE != type) {
                this.params.set("indTestType", IndTestType.CHI_SQUARE);
            }
            return new IndTestChiSquare(this.source, this.params.getDouble("alpha", 0.001));
        }

        throw new IllegalStateException("Cannot find Independence for Data source.");
    }

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


}




