///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.test;

import edu.cmu.tetradapp.util.FileLoadingUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This adjusts copyright notices for all Java classes.
 */
public class AdjustCopyrights {

    public static void main(String[] args) {
        new AdjustCopyrights().adjustCopyrights();
    }

    private void adjustCopyrights() {
        String copyrightNotice = null;

        try {
            copyrightNotice = loadCopyrightNotice();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File directory = new File(".");
        List<File> javaFiles = getJavaFiles(directory);

        for (File file : javaFiles) {
            try {
                String inContents = FileLoadingUtils.fromFile(file);

                if (inContents.startsWith(copyrightNotice)) {
                    continue;
                }

                Pattern pattern = Pattern.compile("package");
                Matcher matcher = pattern.matcher(inContents);
                if (!matcher.find()) {
                    System.out.println("No package statement: " + file);
                }

                System.out.println("Modifying: " + file);

                FileOutputStream out = new FileOutputStream(file);
                PrintStream outStream = new PrintStream(out);
                outStream.println(copyrightNotice);
                outStream.println();

                int from = matcher.start();
                outStream.println(
                        inContents.substring(from));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String loadCopyrightNotice() throws IOException {
        File file = new File("COPYRIGHT");

        System.out.println("Loading copyright notice from: " + file.getAbsolutePath());

        return FileLoadingUtils.fromFile(file);
    }

    /**
     * @return all of the files in the given directory whose names end with ".java".
     */
    private List<File> getJavaFiles(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        List<File> javaFiles = new LinkedList<>();
        File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
                javaFiles.addAll(getJavaFiles(file));
            } else {
                if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }

        return javaFiles;
    }
}






