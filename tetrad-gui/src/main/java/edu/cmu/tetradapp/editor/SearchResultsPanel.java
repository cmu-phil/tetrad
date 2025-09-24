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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class SearchResultsPanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane(SwingConstants.LEFT);

    public SearchResultsPanel() {
        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }

    public void showGraphs(List<Graph> graphs, List<String> names) {
        tabs.removeAll();
        for (int i = 0; i < graphs.size(); i++) {
            Graph g = graphs.get(i);
            String name = (names != null && i < names.size()) ? names.get(i) : ("Result " + (i + 1));
            GraphWorkbench display = new GraphWorkbench(g); // your existing graph viewer
            tabs.addTab(name, display);
        }
        revalidate();
        repaint();
    }

    public void showSingleGraph(Graph g, String title) {
        showGraphs(java.util.Collections.singletonList(g),
                java.util.Collections.singletonList(title));
    }
}
