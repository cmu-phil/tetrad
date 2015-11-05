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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Writes PNG files.
 */
public class PngWriter {
    public static void writePng(Graph graph, File file) {
//        circleLayout(graph, 200, 200, 175);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Remove self-loops.
        graph = new EdgeListGraph(graph);

        for (Node node : graph.getNodes()) {
            for (Edge edge : new ArrayList<Edge>(graph.getEdges(node, node))) {
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
        }
        catch (IOException e1) {
            throw new RuntimeException("Could not write to " + file, e1);
        }
    }
}



