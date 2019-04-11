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
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NamingProtocol;

/**
 * Base class for variable specifications for DataSet. These objects govern the
 * types of values which may be recorded in a Column of data and provide
 * information about the interpretation of these values. Variables of every type
 * must provide a marker which is recorded in a column of data for that variable
 * when the value is missing; this missing data marker should not be used for
 * other purposes.
 *
 * @author Willie Wheeler 7/99
 * @author Joseph Ramsey modifications 12/00
 */
public abstract class AbstractVariable implements Variable {

    static final long serialVersionUID = 23L;

    public static int LAST_ID = 0;

    /**
     * Name of this variable.
     *
     * @serial
     */
    private String name = "??";

    /**
     * Builds a variable having the specified name.
     */
    AbstractVariable(String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        name = name.trim();

//        if (!NamingProtocol.isLegalName(name)) {
//            throw new NullPointerException(
//                    NamingProtocol.getProtocolDescription() + ": " + name);
//        }
        this.name = name;
    }

    /**
     * @return the missing value marker as an Object.
     */
    public abstract Object getMissingValueMarker();

    /**
     * Tests whether the given value is the missing data marker.
     */
    public abstract boolean isMissingValue(Object value);

    /**
     * Sets the name of this variable.
     */
    public final void setName(String name) {
        if (name == null) {
            throw new NullPointerException(
                    "AbstractVariable name must not be null.");
        }

        if (!NamingProtocol.isLegalName(name)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        this.name = name;
    }

    /**
     * @return the name of this variable.
     */
    public final String getName() {
        return name;
    }

    /**
     * Checks to see whether the passed value is an acceptable value for
     * <tt>this</tt> variable. For <tt>AbstractVariable</tt>, this method always
     * returns <tt>true</tt>. </p> Subclasses should override
     * <tt>checkValue()</tt> in order to provide for subclass-specific value
     * checking. The value should pass the test if it can be converted into an
     * equivalent object of the correct class type (see
     * <tt>getValueClass()</tt> for this variable; otherwise, it should fail. In
     * general, <tt>checkValue()</tt> should not fail a value for simply not
     * being an instance of a particular class. </p> Since this method is not
     * <tt>static</tt>, subclasses may (but need not) provide for
     * instance-specific value checking.
     *
     * @param value a value
     * @return <tt>true</tt> if the value is an acceptable value for
     * <tt>this</tt> variable, and <tt>false</tt> otherwise
     */
    public boolean checkValue(Object value) {
        return true;
    }

    /**
     * @return a String representation of this variable. Specifically, the name
     * of the variable is returned.
     */
    public String toString() {
        return name;
    }

    public abstract Node like(String name);

    @Override
    public int compareTo(Node node) {
        String node1 = getName();
        String node2 = node.getName();

        boolean isAlpha1 = ALPHA.matcher(node1).matches();
        boolean isAlpha2 = ALPHA.matcher(node2).matches();
        boolean isAlphaNum1 = ALPHA_NUM.matcher(node1).matches();
        boolean isAlphaNum2 = ALPHA_NUM.matcher(node2).matches();
        boolean isLag1 = LAG.matcher(node1).matches();
        boolean isLag2 = LAG.matcher(node2).matches();

        if (isAlpha1) {
            if (isLag2) {
                return -1;
            }
        } else if (isAlphaNum1) {
            if (isAlphaNum2) {
                String s1 = node1.replaceAll("\\d+", "");
                String s2 = node2.replaceAll("\\d+", "");
                if (s1.equals(s2)) {
                    String n1 = node1.replaceAll("\\D+", "");
                    String n2 = node2.replaceAll("\\D+", "");

                    return Integer.valueOf(n1).compareTo(Integer.valueOf(n2));
                } else {
                    return s1.compareTo(s2);
                }
            } else if (isLag2) {
                return -1;
            }
        } else if (isLag1) {
            if (isAlpha2 || isAlphaNum2) {
                return 1;
            } else if (isLag2) {
                String l1 = node1.replaceAll(":", "");
                String l2 = node2.replaceAll(":", "");
                String s1 = l1.replaceAll("\\d+", "");
                String s2 = l2.replaceAll("\\d+", "");
                if (s1.equals(s2)) {
                    String n1 = l1.replaceAll("\\D+", "");
                    String n2 = l2.replaceAll("\\D+", "");

                    return Integer.valueOf(n1).compareTo(Integer.valueOf(n2));
                } else {
                    return s1.compareTo(s2);
                }
            }
        }

        return node1.compareTo(node2);
    }

}
