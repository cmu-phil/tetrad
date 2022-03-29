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

package edu.cmu.tetrad.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.prefs.Preferences;

/**
 * Provides an application-wide "memory" of the number format to be used.
 *
 * @author Joseph Ramsey
 */
public class NumberFormatUtil {
    private static final NumberFormatUtil INSTANCE = new NumberFormatUtil();
    private NumberFormat nf;

    private NumberFormatUtil() {

        try {
            nf = new DecimalFormat(Preferences.userRoot().get("numberFormat", "0.0000"));
        } catch (Exception e) {
            nf = new DecimalFormat("0.0000");
        }

    }

    /**
     * @return Ibid.
     */
    public static NumberFormatUtil getInstance() {
        return INSTANCE;
    }

    /**
     * Sets the number format, <code>nf</code>.
     *
     * @param nf Ibid.
     * @throws NullPointerException if nf is null.
     */
    public void setNumberFormat(NumberFormat nf) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.nf = nf;
    }

    /**
     * @return Ibid.
     */
    public NumberFormat getNumberFormat() {
        return nf;
    }
}



