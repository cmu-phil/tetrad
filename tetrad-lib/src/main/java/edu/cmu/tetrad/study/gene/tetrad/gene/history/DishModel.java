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

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Models the manner in which gene models are initialized differentially depending on the dishes that the cells are in.
 * The basic idea of the model is that cells are grown in different dishes, and as a result may be initialized
 * differently. (They may also grow differently, although that is not being modelled currently.) Causes of
 * differentiation may be nutrition, temperature, etc.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DishModel implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * An array of dish bumps for each dish.
     */
    private final double[] dishBumps;
    /**
     * The number of the getModel dish.
     */
    private int dishNumber;
    /**
     * The standard deviation of the normal distribution from which dish bump values are drawn, in percent. The
     * distribution has a mean of 100%.
     */
    private double dishBumpStDev = 10.0;

    //===============================CONSTRUCTORS========================//

    /**
     * <p>Constructor for DishModel.</p>
     *
     * @param numDishes     a int
     * @param dishBumpStDev a double
     */
    public DishModel(int numDishes, double dishBumpStDev) {

        if (numDishes < 1) {
            throw new IllegalArgumentException(
                    "There must be at least one dish");
        }

        if (dishBumpStDev < 0.0) {
            throw new IllegalArgumentException(
                    "The stdev must be non-negative");
        }

        // numDishes is not stored at the class level, because the
        // length of dishBumps[] already contains that information.
        this.dishBumps = new double[numDishes];
        this.setDishBumpStDev(dishBumpStDev);

        Distribution distribution =
                new Normal(100.0, dishBumpStDev);

        for (int i = 0; i < numDishes; i++) {
            this.dishBumps[i] = distribution.nextRandom();
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.DishModel} object
     */
    public static DishModel serializableInstance() {
        return new DishModel(1, 1.0);
    }

    //===============================PUBLIC METHODS======================//

    /**
     * Returns the number of the getModel dish.
     *
     * @return a int
     */
    public int getDishNumber() {
        return this.dishNumber;
    }

    /**
     * Sets the number of the getModel dish.
     *
     * @param dishNumber a int
     */
    public void setDishNumber(int dishNumber) {

        if ((dishNumber >= 0) && (dishNumber < this.dishBumps.length)) {
            this.dishNumber = dishNumber;
        } else {
            throw new IllegalArgumentException(
                    "Invalid dish number: " + dishNumber);
        }
    }

    /**
     * Bumps the given expression value in the manner prescribed for the getModel dish.
     *
     * @param expressionLevel a double
     * @return a double
     */
    public double bumpInitialization(double expressionLevel) {

        //System.out.println(this.dishBumps[this.dishNumber]);
        return expressionLevel * this.dishBumps[this.dishNumber] / 100.0;
    }

    /**
     * <p>Getter for the field <code>dishBumpStDev</code>.</p>
     *
     * @return a double
     */
    public double getDishBumpStDev() {
        return this.dishBumpStDev;
    }

    /**
     * <p>Setter for the field <code>dishBumpStDev</code>.</p>
     *
     * @param dishBumpStDev a double
     */
    public void setDishBumpStDev(double dishBumpStDev) {
        this.dishBumpStDev = dishBumpStDev;
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





