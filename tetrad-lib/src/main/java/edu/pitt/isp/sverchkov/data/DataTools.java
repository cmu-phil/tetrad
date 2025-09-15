/// ////////////////////////////////////////////////////////////////////////////
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

package edu.pitt.isp.sverchkov.data;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Data tools.
 *
 * @author YUS24
 * @version $Id: $Id
 */
public class DataTools {

    /**
     * Constant <code>NEWLINE="System.getProperty(line.separator)"</code>
     */
    public static final String NEWLINE = System.getProperty("line.separator");
    /**
     * Constant <code>DELIMITER_REGEX=" *, *"</code>
     */
    public static final String DELIMITER_REGEX = " *, *";
    /**
     * Constant <code>DELIMITER=", "</code>
     */
    public static final String DELIMITER = ", ";

    /**
     * Prevents instantiation.
     */
    private DataTools() {
    }

    /**
     * Reads a data table from a file.
     *
     * @param file The file to read from
     * @return The data table
     * @throws java.io.FileNotFoundException if the file is not found
     */
    public static DataTable<String, String> dataTableFromFile(File file) throws FileNotFoundException {
        DataTable<String, String> data = null;
        try (Scanner in = new Scanner(file)) {
            data = new DataTableImpl<>(Arrays.asList(in.nextLine().trim().split(DataTools.DELIMITER_REGEX)));
            while (in.hasNextLine())
                data.addRow(Arrays.asList(in.nextLine().trim().split(DataTools.DELIMITER_REGEX)));
        }
        return data;
    }

    /**
     * Saves a data table to a file.
     *
     * @param data        The data table to save
     * @param dest        The file to save to
     * @param headers     Whether to include headers
     * @param <Attribute> a Attribute class
     * @param <Value>     a Value class
     * @throws java.io.IOException if something goes wrong
     */
    public static <Attribute, Value> void saveCSV(DataTable<Attribute, Value> data, File dest, boolean headers) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(dest))) {

            if (headers) {
                String delim = "";
                for (Attribute a : data.variables()) {
                    out.append(delim).append(a.toString());
                    delim = DataTools.DELIMITER;
                }
                out.append(DataTools.NEWLINE);
            }

            for (List<Value> row : data) {
                String delim = "";
                for (Value v : row) {
                    out.append(delim).append(v.toString());
                    delim = DataTools.DELIMITER;
                }
                out.append(DataTools.NEWLINE);
            }
        }
    }
}

