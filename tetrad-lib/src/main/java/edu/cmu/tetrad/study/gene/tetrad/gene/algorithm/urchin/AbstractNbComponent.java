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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin;

import edu.cmu.tetrad.util.NamingProtocol;


/**
 * Abstract NB component.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public abstract class AbstractNbComponent implements NbComponent {
    private NbComponent[] parents;
    private int[] inhibitExcite;
    private int nparents;
    private double value;
    private String name;

    private double factor;
    private double power;

    private double sd;

    /**
     * Constructs a new component with the given factor, power, parents, and
     *
     * @param factor        the factor
     * @param power         the power
     * @param parents       the parents
     * @param inhibitExcite the inhibit/excite
     * @param name          the namew
     */
    public AbstractNbComponent(double factor, double power,
                               NbComponent[] parents, int[] inhibitExcite, String name) {
        setFactor(factor);
        setPower(power);
        setParents(parents);
        setInhibitExcite(inhibitExcite);
        setName(name);
        if (parents == null) {
            setNparents(0);
        } else {
            setNparents(parents.length);
        }
        setValue(0.0);
        setSd(0.1);
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a double
     */
    public double getValue() {
        return this.value;
    }

    /**
     * {@inheritDoc}
     */
    public void setValue(double level) {
        this.value = level;
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
     * Sets the name.
     *
     * @param name the name.
     */
    public void setName(String name) {
        if (!NamingProtocol.isLegalName(name)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public void addParent(NbComponent component, int ie) {
        if (getParents() == null) {
            this.setParents(new NbComponent[1]);
            this.getParents()[0] = component;
            this.setInhibitExcite(new int[1]);
            this.getInhibitExcite()[0] = ie;
            this.setNparents(1);
        } else {
            NbComponent[] newParents = new NbComponent[getParents().length + 1];
            int[] newInhibitExcite = new int[getParents().length + 1];
            newParents[getParents().length] = component;
            newInhibitExcite[getParents().length] = ie;

            for (int i = 0; i < getParents().length; i++) {
                newParents[i] = getParents()[i];
                newInhibitExcite[i] = getInhibitExcite()[i];
            }

            setParents(newParents);
            setInhibitExcite(newInhibitExcite);
            setNparents(getNparents() + 1);
        }
    }

    /**
     * Displays the parents.
     */
    public void displayParents() {
        for (int i = 0; i < getNparents(); i++) {
            System.out.println(getParents()[i].getName() + " " +
                    getParents()[i].getValue() + " " + getInhibitExcite()[i]);
        }
    }

    /**
     * Updates.
     */
    public abstract void update();

    /**
     * Returns the parents.
     *
     * @return These parents.
     */
    public NbComponent[] getParents() {
        return this.parents;
    }

    /**
     * Sets the parents.
     *
     * @param parents the parents.
     */
    public void setParents(NbComponent[] parents) {
        this.parents = parents;
    }

    /**
     * Returns the inhibit/excite.
     *
     * @return the inhibit/excite.
     */
    public int[] getInhibitExcite() {
        return this.inhibitExcite;
    }

    /**
     * Sets the inhibit/excite.
     *
     * @param inhibitExcite the inhibit/excite.
     */
    public void setInhibitExcite(int[] inhibitExcite) {
        this.inhibitExcite = inhibitExcite;
    }

    /**
     * Sets the number of parents.
     *
     * @return the number of parents.
     */
    public int getNparents() {
        return this.nparents;
    }

    /**
     * Sets the number of parents.
     *
     * @param nparents the number of parents.
     */
    public void setNparents(int nparents) {
        this.nparents = nparents;
    }

    /**
     * Returns the factor.
     *
     * @return the factor.
     */
    public double getFactor() {
        return this.factor;
    }

    /**
     * Sets the factor.
     *
     * @param factor the factor.
     */
    public void setFactor(double factor) {
        this.factor = factor;
    }

    /**
     * Returns the power.
     *
     * @return the power.
     */
    public double getPower() {
        return this.power;
    }

    /**
     * Sets the power.
     *
     * @param power the power.
     */
    public void setPower(double power) {
        this.power = power;
    }

    /**
     * Returns the standard deviation.
     *
     * @return the standard deviation.
     */
    public double getSd() {
        return this.sd;
    }

    /**
     * Sets the standard deviation.
     *
     * @param sd the standard deviation.
     */
    public void setSd(double sd) {
        this.sd = sd;
    }
}





