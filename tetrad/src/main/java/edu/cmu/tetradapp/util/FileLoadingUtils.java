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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.Version;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Sundry utilities for loading files.
 */
public class FileLoadingUtils {
    // Converts the contents of a file into a CharSequence
    // suitable for use by the regex package.
    public static String fromFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();

        // Create a read-only CharBuffer on the file
        ByteBuffer bbuf =
                fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
        CharBuffer cbuf = Charset.forName("8859_1").newDecoder().decode(bbuf);

        String s = cbuf.toString();
        fis.close();
        return s;
    }

    public static String fromResources(String path) {
        try {
            URL url = Version.class.getResource(path);

            if (url == null) {
                throw new RuntimeException(
                        "Could not load resource file: " + path);
            }

            InputStream inStream = url.openStream();
            Reader reader = new InputStreamReader(inStream);
            BufferedReader bufReader = new BufferedReader(reader);

            String line;
            String spec = "";
            while ((line = bufReader.readLine()) != null) {
                spec += line + "\n";
            }
            bufReader.close();

            return spec;
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        throw new RuntimeException("Could not load resource file: " + path);
    }
}




