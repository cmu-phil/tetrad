package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class DraggableElementExample extends Application {

    private double offsetX1, offsetY1;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Graph graph = RandomGraph.randomDag(20, 0, 20,
                100, 100, 100, false);

//        LayoutUtil.circleLayout(graph, 500, 500, 300);
        LayoutUtil.fruchtermanReingoldLayout(graph);

        AnchorPane contentArea = new AnchorPane();
        ScrollPane scrollPane = new ScrollPane(contentArea);

        Pane root = new Pane(scrollPane);
        Scene scene = new Scene(root, 400, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Graph View Example");
        primaryStage.show();

        scrollPane.prefWidthProperty().bind(root.widthProperty());
        scrollPane.prefHeightProperty().bind(root.heightProperty());

        Map<Node, DisplayNode> displayNodes = new HashMap<>();
        Map<Edge, DisplayEdge> displayEdges = new HashMap<>();

        // Order: edges first, then nodes. This is so that the nodes are on top of the edges.
        // First, add the nodes to the display nodes map.
        for (Node node : graph.getNodes()) {
            displayNodes.put(node, makeDisplayNode(node, graph, displayNodes, displayEdges));
        }

        // Add the edges to the display edges map and add them to the root.
        for (Edge edge : graph.getEdges()) {
            DisplayEdge _edge = makeDisplayEdge();
            displayEdges.put(edge, _edge);
            contentArea.getChildren().addAll(_edge.getLine(), _edge.getArrowHead());
            updateLineAndArrow(edge, _edge.getLine(), _edge.getArrowHead(), displayNodes.get(edge.getNode1()).getEllipse(), displayNodes.get(edge.getNode2()).getEllipse());
        }

        // Finally, add the nodes to the root.
        for (Node node : graph.getNodes()) {
            contentArea.getChildren().addAll(displayNodes.get(node).getEllipse(), displayNodes.get(node).getText());
        }
    }

    private DisplayEdge makeDisplayEdge() {
        Line line = new Line();
        line.setStroke(Color.BLACK);

        Polygon arrowHead = new Polygon();
        arrowHead.setStroke(Color.BLACK);
        arrowHead.setFill(Color.BLACK);

        return new DisplayEdge();
    }

    private void updateLineAndArrow(Edge edge, Line line, Polygon arrowhead, Ellipse startEllipse, Ellipse endEllipse) {
        double startX = startEllipse.getCenterX();
        double startY = startEllipse.getCenterY();
        double endX = endEllipse.getCenterX();
        double endY = endEllipse.getCenterY();

        double[] startIntersection = findEllipseIntersection(startEllipse, startX, startY, endX, endY);
        double[] endIntersection = findEllipseIntersection(endEllipse, endX, endY, startX, startY);

        line.setStartX(startIntersection[0]);
        line.setStartY(startIntersection[1]);
        line.setEndX(endIntersection[0]);
        line.setEndY(endIntersection[1]);

        double arrowSize = 10;
        double angle = Math.atan2(line.getStartY() - line.getEndY(), line.getStartX() - line.getEndX());

        arrowhead.getPoints().clear();
        arrowhead.getPoints().addAll(
                line.getEndX() + arrowSize * Math.cos(angle - Math.PI / 6),
                line.getEndY() + arrowSize * Math.sin(angle - Math.PI / 6),
                line.getEndX(),
                line.getEndY(),
                line.getEndX() + arrowSize * Math.cos(angle + Math.PI / 6),
                line.getEndY() + arrowSize * Math.sin(angle + Math.PI / 6)
        );
    }

    private double[] findEllipseIntersection(Ellipse ellipse, double startX, double startY, double endX, double endY) {
        double[] intersection = new double[2];

        // Use binary search to find a point on the line segment that lies inside the ellipse
        int iterations = 20; // The number of iterations for binary search (can be adjusted for higher precision)
        for (int i = 0; i < iterations; i++) {
            double midX = (startX + endX) / 2;
            double midY = (startY + endY) / 2;

            if (ellipse.contains(midX, midY)) {
                startX = midX;
                startY = midY;
            } else {
                intersection[0] = midX;
                intersection[1] = midY;
                endX = midX;
                endY = midY;
            }
        }

        return intersection;
    }

    private DisplayNode makeDisplayNode(Node node, Graph graph, Map<Node, DisplayNode> displayNodes,
                                        Map<Edge, DisplayEdge> displayEdges) {
        final Ellipse ellipse = new Ellipse(node.getCenterX(), node.getCenterY(), 30, 20); // x, y, radiusX, radiusY
        ellipse.setFill(Color.WHITE);
        ellipse.setStroke(Color.BLACK);
        ellipse.setStrokeWidth(2);

        Text text = new Text(node.getName());
        text.setFont(Font.font(20));
        text.setX(ellipse.getCenterX() - text.getLayoutBounds().getWidth() / 2);
        text.setY(ellipse.getCenterY() + text.getLayoutBounds().getHeight() / 4);

        ellipse.setOnMousePressed(event -> {
            offsetX1 = event.getSceneX() - ellipse.getCenterX();
            offsetY1 = event.getSceneY() - ellipse.getCenterY();
        });

        ellipse.setOnMouseDragged(event -> {
            double newX = event.getSceneX() - offsetX1;
            double newY = event.getSceneY() - offsetY1;
            ellipse.setCenterX(newX);
            ellipse.setCenterY(newY);
            text.setX(newX - text.getLayoutBounds().getWidth() / 2);
            text.setY(newY + text.getLayoutBounds().getHeight() / 4);

            for (Edge edge : graph.getEdges(node)) {
                Node n1 = Edges.getDirectedEdgeTail(edge);
                Node n2 = Edges.getDirectedEdgeHead(edge);

                updateLineAndArrow(edge, displayEdges.get(edge).getLine(), displayEdges.get(edge).getArrowHead(),
                        displayNodes.get(n1).getEllipse(), displayNodes.get(n2).getEllipse());
            }
        });

        text.setOnMousePressed(event -> {
            offsetX1 = event.getSceneX() - ellipse.getCenterX();
            offsetY1 = event.getSceneY() - ellipse.getCenterY();
        });

        text.setOnMouseDragged(event -> {
            double newX = event.getSceneX() - offsetX1;
            double newY = event.getSceneY() - offsetY1;
            ellipse.setCenterX(newX);
            ellipse.setCenterY(newY);
            text.setX(newX - text.getLayoutBounds().getWidth() / 2);
            text.setY(newY + text.getLayoutBounds().getHeight() / 4);

            for (Edge edge : graph.getEdges(node)) {
                Node n1 = Edges.getDirectedEdgeTail(edge);
                Node n2 = Edges.getDirectedEdgeHead(edge);

                updateLineAndArrow(edge, displayEdges.get(edge).getLine(), displayEdges.get(edge).getArrowHead(),
                        displayNodes.get(n1).getEllipse(), displayNodes.get(n2).getEllipse());
            }
        });

        return new DisplayNode(ellipse, text);
    }

    private static class DisplayNode {
        private final Ellipse ellipse;
        private final Text text;

        public DisplayNode(Ellipse ellipse, Text text) {
            this.ellipse = ellipse;
            this.text = text;
        }

        public Ellipse getEllipse() {
            return ellipse;
        }

        public Text getText() {
            return text;
        }
    }

    private static class DisplayEdge {
        private final Line line;
        private final Polygon arrowHead;

        public DisplayEdge() {
            Line line = new Line();
            line.setStroke(Color.BLACK);

            Polygon arrowHead = new Polygon();
            arrowHead.setStroke(Color.BLACK);
            arrowHead.setFill(Color.BLACK);

            this.line = line;
            this.arrowHead = arrowHead;
        }

        public Line getLine() {
            return line;
        }

        public Polygon getArrowHead() {
            return arrowHead;
        }
    }
}

