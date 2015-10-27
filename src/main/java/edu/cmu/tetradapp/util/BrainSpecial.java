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


import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Lays out a graph by placing springs between the nodes and letting the system
 * settle (one node at a time).
 *
 * @author Joseph Ramsey
 */
public final class BrainSpecial {

    /**
     * The graph being laid out.
     */
    private final Graph graph;

    /**
     * Has information about nodes on screen.
     */
    private LayoutEditable layoutEditable;

    //==============================CONSTRUCTORS===========================//

    public BrainSpecial(LayoutEditable layoutEditable) {
        this.graph = layoutEditable.getGraph();

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).isEmpty()) {
                graph.removeNode(node);
            }
        }
    }

    public BrainSpecial(Graph graph) {
        this.graph = graph;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        for (Node node : graph.getNodes()) {
            if (graph.getEdges(node).isEmpty()) {
                graph.removeNode(node);
            }
        }

        Map<String, Coord> map = loadMap();

        for (Node node : graph.getNodes()) {
            String name = node.getName();
            Coord coord = map.get(name);
            node.setCenterX(transform(coord.getX()));
            node.setCenterY(transform(coord.getY()));
        }

//        for (Node node : graph.getNodes()) {
//            String name = node.getName();
//            name = name.substring(1, name.length());
//            node.setName(name);
//        }
    }

    private int transform(int x) {
        return 6 * (x + 100);
    }

    private Map<String, Coord> loadMap() {
        Map<String, Coord> map = new HashMap<String, Coord>();

        try {
            File file = new File("/Users/josephramsey/Documents/proj/tetrad2/docs/notes/extended_power_labels_283.txt");

            BufferedReader in = new BufferedReader(new FileReader(file));

            String line;

            while ((line = in.readLine()) != null) {
//                System.out.println(line);

                String[] tokens = line.split("\t");

                String var = "X" + tokens[0];
                int index = Integer.parseInt(tokens[0]);
                int x = Integer.parseInt(tokens[1]);
                int y = Integer.parseInt(tokens[2]);
                int z = Integer.parseInt(tokens[3]);
                String area = tokens[4];

                map.put(var, new Coord(index, x, y, z, area));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return map;
    }

    private class Coord {
        private int index;
        private int x;
        private int y;
        private int z;
        private String area;

        public Coord(int index, int x, int y, int z, String area) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
            this.area = area;
        }

        public int getIndex() {
            return index;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        private String getArea() {
            return area;
        }
    }
}





