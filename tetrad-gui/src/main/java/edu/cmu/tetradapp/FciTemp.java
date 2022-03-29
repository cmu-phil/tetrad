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
package edu.cmu.tetradapp;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class FciTemp {

    public static void main(final String... args) {
        if (args.length != 3) {
            throw new RuntimeException("java -jar edu.cmu.edu.tetrad.FciTemp [data] [knowledge] [alpha]");
        }

        final Path dataFile = new File(args[0]).toPath();
        final Delimiter delimiter = Delimiter.TAB;

        final ContinuousTabularDatasetReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
        dataReader.setHasHeader(true);
        final DataSet dataSet;

        try {
            final Data data = dataReader.readInData();
            dataSet = (DataSet) DataConvertUtils.toDataModel(data);
        } catch (final IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Expecting a properly formatted data file: " + args[0]);
        }

        final IKnowledge knowledge;

        try {
            knowledge = DataUtils.loadKnowledge(new File(args[1]), DelimiterType.WHITESPACE, "//");
        } catch (final IOException e) {
            throw new RuntimeException("Expecting properly formatted knowledge file: " + args[1]);
        }

        double alpha = 0;
        try {
            alpha = Double.parseDouble(args[2]);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expecting a number for alpha");
        }

        final Fci fci = new Fci(new IndTestFisherZ(dataSet, alpha));
        fci.setKnowledge(knowledge);
        fci.setHeuristic(1); // lexicographic order.

        final Graph graph = fci.search();

        System.out.println(GraphUtils.graphToText(graph));
    }
}
