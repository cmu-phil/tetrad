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

package edu.pitt.isp.sverchkov.data;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 *
 * @author YUS24
 */
public class DataTools {
    
    public final static String NEWLINE = System.getProperty("line.separator");
    public final static String DELIMITER_REGEX = " *, *";
    public final static String DELIMITER = ", ";
            
    public static DataTable<String,String> dataTableFromFile( File file ) throws FileNotFoundException{
        DataTable<String,String> data = null;
        try ( Scanner in = new Scanner( file ) ){
            data = new DataTableImpl<>( Arrays.asList( in.nextLine().trim().split(DELIMITER_REGEX) ) );
            while( in.hasNextLine() )
                data.addRow( Arrays.asList( in.nextLine().trim().split(DELIMITER_REGEX) ) );
        }
        return data;
    }
    
    public static <Attribute,Value> void saveCSV( DataTable<Attribute,Value> data, File dest, boolean headers ) throws IOException{
        try( BufferedWriter out = new BufferedWriter( new FileWriter( dest ) ) ){
            
            if( headers ){
                String delim = "";
                for( Attribute a : data.variables() ){
                    out.append(delim).append( a.toString() );
                    delim = DELIMITER;
                }
                out.append(NEWLINE);
            }
            
            for( List<Value> row : data ){
                String delim = "";
                for( Value v : row ){
                    out.append(delim).append( v.toString() );
                    delim = DELIMITER;
                }
                out.append(NEWLINE);
            }
        }
    }
}

