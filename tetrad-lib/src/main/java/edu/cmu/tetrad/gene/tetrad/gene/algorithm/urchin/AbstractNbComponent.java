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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.urchin;

import edu.cmu.tetrad.util.NamingProtocol;


/**
 * @author Frank Wimberly
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

    public AbstractNbComponent(double factor, double power,
            NbComponent[] parents, int[] inhibitExcite, String name) {
        setFactor(factor);
        setPower(power);
        setParents(parents);
        setInhibitExcite(inhibitExcite);
        setName(name);
        if (parents == null) {
            setNparents(0);
        }
        else {
            setNparents(parents.length);
        }
        setValue(0.0);
        setSd(0.1);
    }

    public double getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public void setValue(double level) {
        this.value = level;
    }

    public void addParent(NbComponent component, int ie) {
        if (getParents() == null) {
            this.setParents(new NbComponent[1]);
            this.getParents()[0] = component;
            this.setInhibitExcite(new int[1]);
            this.getInhibitExcite()[0] = ie;
            this.setNparents(1);
        }
        else {
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

    public void displayParents() {
        for (int i = 0; i < getNparents(); i++) {
            System.out.println(getParents()[i].getName() + " " +
                    getParents()[i].getValue() + " " + getInhibitExcite()[i]);
        }
    }

    public abstract void update();

    public NbComponent[] getParents() {
        return parents;
    }

    public void setParents(NbComponent[] parents) {
        this.parents = parents;
    }

    public int[] getInhibitExcite() {
        return inhibitExcite;
    }

    public void setInhibitExcite(int[] inhibitExcite) {
        this.inhibitExcite = inhibitExcite;
    }

    public int getNparents() {
        return nparents;
    }

    public void setNparents(int nparents) {
        this.nparents = nparents;
    }

    public void setName(String name) {
        if (!NamingProtocol.isLegalName(name)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        this.name = name;
    }

    public double getFactor() {
        return factor;
    }

    public void setFactor(double factor) {
        this.factor = factor;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = power;
    }

    public double getSd() {
        return sd;
    }

    public void setSd(double sd) {
        this.sd = sd;
    }
}





