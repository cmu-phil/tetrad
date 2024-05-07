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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.util.Version;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the Version class, to make sure it can load versions from string representations and generate string
 * representations correctly.
 *
 * @author josephramsey
 */
public final class TestVersion {

    @Test
    public void testRoundtrip() {
        Version version = new Version("4.3.1-5");
        String versionString = version.toString();
        Version version2 = new Version(versionString);
        assertEquals(version, version2);
    }

    @Test
    public void testNextVersion() {
        Version version = new Version("4.3.1-5");

        Version version2 = version.nextMajorVersion();
        assertEquals(version2, new Version("5.0.0-0"));

        Version version3 = version.nextMinorVersion();
        assertEquals(version3, new Version("4.4.0-0"));

        Version version4 = version.nextMinorSubversion();
        assertEquals(version4, new Version("4.3.2-0"));

        Version version5 = version.nextIncrementalRelease();
        assertEquals(version5, new Version("4.3.1-6"));
    }
}





