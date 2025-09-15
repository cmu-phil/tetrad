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