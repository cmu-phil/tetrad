package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads autistic/typical data files from a given directory and runs FASK on each one.
 *
 * @author jdramsey
 */
public class FaskGraphs {
    private List<String> filenames = new ArrayList<>();
    private List<DataSet> datasets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private List<Boolean> types = new ArrayList<>();

    /**
     *
     * @param path The path to the directory containing the files.
     * @param parameters Parameters for the FASK search.
     * @param contains Some string(s) the data filenames must include. May be  blank.
     */
    public FaskGraphs(String path, Parameters parameters, String... contains) {
        loadFiles(path, parameters, contains);
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

    public void setGraphs(List<Graph> graphs) {
        this.graphs = graphs;
    }

    public List<Boolean> getTypes() {
        return types;
    }

    public void reconcileNames(FaskGraphs... files) {
        Set<String> allNames = new HashSet<>();
        List<Node> nodes = new ArrayList<>();

        for (FaskGraphs file : files) {
            for (Graph graph : file.getGraphs()) {
                for (Node node : graph.getNodes()) {
                    if (!allNames.contains(node.getName())) {
                        nodes.add(node);
                        allNames.add(node.getName());
                    }
                }
            }
        }

        List<Graph> graphs2 = new ArrayList<>();

        for (Graph graph : graphs) {
            graphs2.add(GraphUtils.replaceNodes(graph, nodes));
        }

        this.graphs = graphs2;
    }

    public List<DataSet> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<DataSet> datasets) {
        this.datasets = datasets;
    }

    private void loadFiles(String path, Parameters parameters, String... contains) {
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(true);
        reader.setDelimiter(DelimiterType.TAB);

        File dir = new File(path);

        File[] files = dir.listFiles();

        if (files == null) {
            throw new NullPointerException();
        }

        FILE:
        for (File file : files) {
            String name = file.getName();

            for (String s : contains) {
                if (!name.contains(s)) continue FILE;
            }

            if (!name.contains("graph")) {
                try {
                    if (name.contains("autistic")) {
                        types.add(true);
                        DataSet dataSet = reader.parseTabular(new File(path, name));
                        filenames.add(name);
                        datasets.add(dataSet);
                        Fask fask = new Fask();
                        Graph search = fask.search(dataSet, parameters);
                        graphs.add(search);
                    } else if (name.contains("typical")) {
                        types.add(false);
                        DataSet dataSet = reader.parseTabular(new File(path, name));
                        filenames.add(name);
                        datasets.add(dataSet);
                        Fask fask = new Fask();
                        Graph search = fask.search(dataSet, parameters);
                        graphs.add(search);
                    }

                    System.out.println("Loaded " + name);
                } catch (IOException e) {
                    System.out.println("File " + name + " could not be parsed.");
                }
            }
        }

        reconcileNames();
    }

}
