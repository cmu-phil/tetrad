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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

/**
 * Implements the basic machinery used by all history objects.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneHistory implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The initializer for the history.
     */
    private final Initializer initializer;

    /**
     * The update function for the history.
     */
    private final UpdateFunction updateFunction;

    /**
     * To simulate asynchronous updating, update periods for each factor are allowed to be different. (Note: this was a
     * brilliant idea somebody had a long time ago that has never yet been used. jdramsey 2/22/02)
     */
    private final int[] updatePeriods;

    /**
     * The getModel time step, which is the number of steps <i>after</i> the initialization period. In other words, time
     * step 0 is the first update step after the initialization, or the first step at which the Glass updating function
     * has been applied. (Markov process.)
     */
    private int step;

    /**
     * A history of time slices of values for each factor, extending back as far as is necessary for the update function
     * to be applied properly (that is, from maxlag up to 0, the getModel time step). Note that the firs subscript is
     * the time slice, whereas the second subscript is the expression level for each gene.
     */
    private double[][] historyArray;

    /**
     * Indicates whether initialization should be synchronized or not. If it's synchronized, then the same (or almost
     * the same) set of initial random values are used each time the initialize() method is called. Otherwise, a new set
     * of random values is chosen each time. Note that this is a "first pass" attempt at "shocking" the simulated
     * cells.
     */
    private boolean initSync = true;

    /**
     * A stored copy of the initial values for the history array, to be used if synchronized initialization is desired.
     * If synchonized initialization is selected, then on the first pass through the initialization method, this array
     * is calculated, and for each individual simulated, the history array for that individual is initialized using
     * values from this array.
     */
    private double[][] syncInitialization;

    /**
     * A model of the differences in expression levels due to the particular dish a sample is taken from.
     */
    private DishModel dishModel;

    //==============================CONSTRUCTORS==========================//

    /**
     * Constructs a new history with the given initializer and the given update function.
     *
     * @param initializer    a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.Initializer} object
     * @param updateFunction a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.UpdateFunction} object
     */
    public GeneHistory(Initializer initializer, UpdateFunction updateFunction) {
        if (initializer == null) {
            throw new NullPointerException("Initializer cannot be null.");
        }
        if (updateFunction == null) {
            throw new NullPointerException("Updater cannot be null.");
        }
        this.initializer = initializer;
        this.updateFunction = updateFunction;

        this.updatePeriods = new int[updateFunction.getNumFactors()];

        for (int i = 0; i < this.updatePeriods.length; i++) {
            this.updatePeriods[i] = 1;
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory} object
     */
    public static GeneHistory serializableInstance() {
        return new GeneHistory(BasalInitializer.serializableInstance(),
                BooleanGlassFunction.serializableInstance());

    }

    //================================PUBLIC METHODS======================//

    /**
     * Returns the initializer.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.Initializer} object
     */
    public Initializer getInitializer() {
        return this.initializer;
    }

    /**
     * Returns the update function.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.UpdateFunction} object
     */
    public UpdateFunction getUpdateFunction() {
        return this.updateFunction;
    }

    /**
     * Returns the getModel step.
     *
     * @return a int
     */
    public int getStep() {
        return this.step;
    }

    /**
     * Returns the getModel history array.  In the array, historyArray[0] represents the getModel time step,
     * historyArray[1] the time step one lag back, historyArray[2] the time step two lags back, etc., up to the maximum
     * time lag.
     *
     * @return this array.
     */
    public double[][] getHistoryArray() {
        return this.historyArray;
    }

    /**
     * Determines whether initialization is synchronized.
     *
     * @return the getModel value of <code>initSync</code>.
     */
    public boolean getInitSync() {
        return this.initSync;
    }

    /**
     * Sets whether initialization should be synchronized.
     *
     * @param initSync a boolean
     */
    public void setInitSync(boolean initSync) {
        this.initSync = initSync;
    }

    /**
     * Resets the history initialization array to that a new data set can be generated.
     */
    public void reset() {
        this.syncInitialization = null;
    }

    /**
     * Gets the dish model.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.DishModel} object
     */
    public DishModel getDishModel() {
        return this.dishModel;
    }

    /**
     * Sets the dish model.
     *
     * @param dishModel a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.DishModel} object
     */
    public void setDishModel(DishModel dishModel) {
        this.dishModel = dishModel;
    }

    /**
     * Updates the history to the next time slice using some formula.
     */
    public void update() {

        double[] last = this.historyArray[this.historyArray.length - 1];

        System.arraycopy(this.historyArray, 0, this.historyArray, 1, this.historyArray.length - 1);

        this.historyArray[0] = last;

        ++this.step;

        for (int i = 0; i < this.updateFunction.getNumFactors(); i++) {
            if (this.step % this.updatePeriods[i] == 0) {
                this.historyArray[0][i] =
                        this.updateFunction.getValue(i, this.historyArray);
            }
        }
    }

    /**
     * Initializes the history array. If <code>syncInit</code> is true, stored initialization values are used.
     * Otherwise, the history array is randomly initialized.
     */
    public void initialize() {

        int numFactors = this.updateFunction.getNumFactors();
        int maxLag = this.updateFunction.getMaxLag();

        if (this.initSync) {
            if (this.syncInitialization == null) {
                this.syncInitialization = new double[maxLag + 1][numFactors];
                this.historyArray = new double[maxLag + 1][numFactors];

                getInitializer().initialize(this.syncInitialization);
            }

            // copy values from the stored initialization array to the real
            // history array.
            for (int i = 0; i < this.historyArray.length; i++) {
                for (int j = 0; j < this.historyArray[0].length; j++) {
                    if (getDishModel() == null) {
                        this.historyArray[i][j] = this.syncInitialization[i][j];
                    } else {
                        this.historyArray[i][j] = getDishModel().bumpInitialization(
                                this.syncInitialization[i][j]);
                    }
                }
            }
        } else {
            if (this.historyArray == null) {
                this.historyArray = new double[maxLag + 1][numFactors];
            }

            getInitializer().initialize(this.historyArray);
        }

        // PrintUtil out the history array.
        if (false) {
            System.out.println("\nHistory array:");

            for (double[] aHistoryArray : this.historyArray) {
                for (int j = 0; j < this.historyArray[0].length; j++) {
                    System.out.print(aHistoryArray[j] + "\t");
                }

                System.out.println();
            }

            System.out.println();
        }

        this.step = -1;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s an {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.initializer == null) {
            throw new NullPointerException();
        }

        if (this.updateFunction == null) {
            throw new NullPointerException();
        }

        if (this.updatePeriods == null) {
            throw new NullPointerException();
        }

        //The above are the only member variables which are created by the constructor.
        //Many others are created in the initialize method which is not called by the constructor.
    }

}





