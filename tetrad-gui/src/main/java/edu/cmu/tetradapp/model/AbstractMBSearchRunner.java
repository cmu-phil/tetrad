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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract subclass for Markov Blanket searches. This should be used so that the markov blanket search
 * can also be used as input for a search box.
 *
 * @author Tyler Gibson
 */
public abstract class AbstractMBSearchRunner extends DataWrapper implements MarkovBlanketSearchRunner {
    static final long serialVersionUID = 23L;

    /**
     * Data model.
     *
     * @serial may be null.
     */
    private DataSet dataModel;


    /**
     * The variables in the markov blanket.
     *
     * @serial may be null.
     */
    private List<Node> variables;


    /**
     * The source data model.
     *
     * @serial not null.
     */
    private DataSet source;

    /**
     * The search params.
     *
     * @serial not null.
     */
    private MbSearchParams params;




    /**
     * The name of the search algorithm
     *
     * @serial may be null.
     */
    private String searchName;


    /**
     * Conctructs the abstract search runner.
     *
     * @param source - The source data the search is acting on.
     * @param params - The params for the search.
     */
    public AbstractMBSearchRunner(DataModel source, MbSearchParams params) {
        super(castData(source));
        if (source == null) {
            throw new NullPointerException("The source data was null.");
        }
        if (params == null) {
            throw new NullPointerException("Search params were null.");
        }
        this.params = params;
        this.source = (DataSet) source;
    }


    /**
     * @return the parameters for the search.
     */
    public MbSearchParams getParams() {
        return this.params;
    }


    /**
     * @return the data model for the variables in the Markov blanket or null if
     * the runner has not executed yet.
     */
    public DataSet getDataModelForMarkovBlanket() {
        return this.dataModel;
    }


    /**
     * @return the variables in the MB searhc.
     */
    public List<Node> getMarkovBlanket() {
        return this.variables;
    }


    /**
     * @return the source of the search.
     */
    public DataSet getSource() {
        return this.source;
    }


    public void setSearchName(String n) {
        this.searchName = n;
    }

    /**
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
     * Makes sure the data is not empty.
     */
    protected void validate() {
        if (this.source.getNumColumns() == 0 || this.source.getNumRows() == 0) {
            throw new IllegalStateException("Cannot run algorithm on an empty data set.");
        }
    }


    /**
     * Sets the results of the search.
     */
    protected void setSearchResults(List<Node> nodes) {
        if (nodes == null) {
            throw new NullPointerException("nodes were null.");
        }
        this.variables = new ArrayList<Node>(nodes);
        if (nodes.isEmpty()) {
            this.dataModel = new ColtDataSet(source.getNumRows(), nodes);
        } else {
            this.dataModel = this.source.subsetColumns(nodes);
        }
        this.setDataModel(this.dataModel);
    }


    /**
     * @return an appropriate independence test given the type of data set and values
     * in the params.
     */
    protected IndependenceTest getIndependenceTest() {
        IndTestType type = params.getIndTestType();
        if (this.source.isContinuous() || this.source.getNumColumns() == 0) {
//            if (IndTestType.CORRELATION_T == type) {
//                return new IndTestCramerT(this.source, params.getParameter1());
//            }
            if (IndTestType.FISHER_Z == type) {
                return new IndTestFisherZ(this.source, params.getAlpha());
            }
            if (IndTestType.FISHER_ZD == type) {
                return new IndTestFisherZGeneralizedInverse(this.source, params.getAlpha());
            }
            if (IndTestType.FISHER_Z_BOOTSTRAP == type) {
                return new IndTestFisherZBootstrap(this.source, params.getAlpha(), 15, this.source.getNumRows() / 2);
            }
            if (IndTestType.LINEAR_REGRESSION == type) {
                return new IndTestRegression(this.source, params.getAlpha());
            } else {
                params.setIndTestType(IndTestType.FISHER_Z);
                return new IndTestFisherZ(this.source, params.getAlpha());
            }
        }
        if (this.source.isDiscrete()) {
            if (IndTestType.G_SQUARE == type) {
                return new IndTestGSquare(this.source, params.getAlpha());
            }
            if (IndTestType.CHI_SQUARE == type) {
                return new IndTestChiSquare(this.source, params.getAlpha());
            } else {
                params.setIndTestType(IndTestType.CHI_SQUARE);
                return new IndTestChiSquare(this.source, params.getAlpha());
            }
        }

        throw new IllegalStateException("Cannot find Independence for Data source.");
    }

    //==================== Private Methods ===========================//


    private static DataSet castData(DataModel model){
        if(model instanceof DataSet){
            return (DataSet)model;
        }
        throw new IllegalStateException("The data model must be a rectangular data set.");
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
    @SuppressWarnings({"UnusedDeclaration"})
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (params == null) {
            throw new NullPointerException();
        }
        if (this.source == null) {
            throw new NullPointerException();
        }
    }


}




