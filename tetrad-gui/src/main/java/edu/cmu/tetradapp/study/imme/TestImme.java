package edu.cmu.tetradapp.study.imme;

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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Ignore;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;


/**
 * @author Joseph Ramsey
 */
@Ignore
public class TestImme {

    @Test
    public void test1() {
        try {
            File dir = new File("/Users/user/Box Sync/data/imme/");
            DataSet data = readInContinuousData(dir, "ADV_AND_DIFF_CIRCULAR_30_65_TIME_SERIES_DATA.txt");
//            DataSet data = readInContinuousData(dir, "ADV_AND_DIFF_CIRCULAR_30_65_TIME_SERIES_DATA.nonparanormal.txt");

            int priorN = data.getNumRows();
            int newN = 8000;
            int offset2 = 4000;

            int[] rows = new int[newN];
            for (int i = 0; i < newN; i++) rows[i] = offset2 + i - 1;

//            data = data.subsetRows(rows);

            System.out.println("rows = " + data.getNumRows() + " columns = " + data.getNumColumns());


            DataSet grid = readInContinuousData(dir, "ADV_AND_DIFF_CIRCULAR_30_65_Grid_1.txt");

            Score score = new SemBicScore(new CovarianceMatrix(data));

            edu.cmu.tetrad.search.Fask fask = new edu.cmu.tetrad.search.Fask(data, score);
            fask.setPenaltyDiscount(20);
            fask.setDepth(3);
            fask.setAlpha(.001);
            fask.setDelta(-1);
            fask.setExtraEdgeThreshold(9.0);
            Graph graph = fask.search();

//            final EdgeListGraph graph = new EdgeListGraph();

            int scale = 15;
            int offset = 50;

            java.util.List<Node> nodes = graph.getNodes();

            for (int i = 0; i < 400; i++) {
                final Node node = nodes.get(i);
                final int x = offset + scale * grid.getInt(i, 0);
                final int y = offset + scale * grid.getInt(i, 1);
                node.setCenter(x, y);
            }

            writePng(graph, new File(dir, "outgraph.png"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DataSet readInContinuousData(File dir, String s) throws IOException {
        Dataset dataset1 = new ContinuousTabularDataFileReader(new File(dir, s), Delimiter.WHITESPACE).readInData();
        return (DataSet) DataConvertUtils.toDataModel(dataset1);
    }

    public static void main(String... args) {
        new TestImme().test1();
        ;
    }

    public void writePng(Graph graph, File file) {
//        circleLayout(graph, 200, 200, 175);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Remove self-loops.
        graph = new EdgeListGraph(graph);

        for (Node node : graph.getNodes()) {
            for (Edge edge : new ArrayList<>(graph.getEdges(node, node))) {
                graph.removeEdge(edge);
            }
        }

        final GraphWorkbench workbench = new GraphWorkbench(graph);

        int maxx = 0;
        int maxy = 0;

        for (Node node : graph.getNodes()) {
            if (node.getCenterX() > maxx) {
                maxx = node.getCenterX();
            }

            if (node.getCenterY() > maxy) {
                maxy = node.getCenterY();
            }
        }

        workbench.setSize(new Dimension(maxx + 50, maxy + 50));
        panel.add(workbench, BorderLayout.CENTER);

        JDialog dialog = new JDialog();
        dialog.add(workbench);
        dialog.pack();

        Dimension size = workbench.getSize();
        BufferedImage image = new BufferedImage(size.width, size.height,
                BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D graphics = image.createGraphics();
        workbench.paint(graphics);
        image.flush();

        // Write the image to resultFile.
        try {
            ImageIO.write(image, "PNG", file);
        } catch (IOException e1) {
            throw new RuntimeException("Could not write to " + file, e1);
        }
    }
}



