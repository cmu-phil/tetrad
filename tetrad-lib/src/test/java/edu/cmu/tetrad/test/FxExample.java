package edu.cmu.tetrad.test;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class FxExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create a large content area using an AnchorPane
        AnchorPane contentArea = new AnchorPane();

        // Add some content to the content area (a large rectangle in this case)
        Rectangle largeRectangle = new Rectangle(0, 0, 1000, 1000);
        largeRectangle.setFill(Color.LIGHTGRAY);
        contentArea.getChildren().add(largeRectangle);

        // Create a ScrollPane and set the content to the large content area
        ScrollPane scrollPane = new ScrollPane(contentArea);

        // Create a resizable pane to hold the ScrollPane
        Pane root = new Pane(scrollPane);

        // Bind the ScrollPane's size to the size of the Scene
        scrollPane.prefWidthProperty().bind(root.widthProperty());
        scrollPane.prefHeightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("ScrollPane Example");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}



