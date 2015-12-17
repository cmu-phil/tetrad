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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Test;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public final class TestDataReader {

    @Test
    public void test1() {
        TetradLogger.getInstance().addOutputStream(System.out);

        File file = new File("src/test/resources/cheese.txt");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);
        reader.setIdsSupplied(true);
        reader.setIdLabel("Case");

        DataSet data = reader.parseTabular(chars);

        TetradLogger.getInstance().removeOutputStream(System.out);

        assertEquals(12.3, data.getDouble(0, 0), 0.1);
    }



    // Without the ar names.
    @Test
    public void test1b() {
        TetradLogger.getInstance().addOutputStream(System.out);

        File file = new File("src/test/resources/cheese2.txt");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);
        reader.setVariablesSupplied(false);
        reader.setIdsSupplied(true);
        reader.setIdLabel(null);

        DataSet data = reader.parseTabular(chars);

        TetradLogger.getInstance().removeOutputStream(System.out);

        assertEquals(12.3, data.getDouble(0, 0), 0.1);
    }

    @Test
    public void test10() {
        TetradLogger.getInstance().addOutputStream(System.out);
        File file = new File("src/test/resources/bollen.txt");
        char[] chars = fileToCharArray(file);

        DataReader reader = new DataReader();
        ICovarianceMatrix data = reader.parseCovariance(chars);

        TetradLogger.getInstance().removeOutputStream(System.out);

        assertEquals(6.43, data.getValue(0, 0), 0.1);

    }

    private char[] fileToCharArray(File file) {
        try {
            FileReader reader = new FileReader(file);
            CharArrayWriter writer = new CharArrayWriter();
            int c;

            while ((c = reader.read()) != -1) {
                writer.write(c);
            }

            return writer.toCharArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}



