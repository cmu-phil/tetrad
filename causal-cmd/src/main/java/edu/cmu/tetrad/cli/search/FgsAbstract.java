package edu.cmu.tetrad.cli.search;

import edu.cmu.tetrad.cli.util.DateTime;
import edu.cmu.tetrad.cli.util.GraphmlSerializer;
import edu.cmu.tetrad.cli.util.JsonSerializer;
import edu.cmu.tetrad.cli.util.XmlPrint;
import edu.cmu.tetrad.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Author : Jeremy Espino MD
 * Created  7/2/16 3:01 PM
 */
public abstract class FgsAbstract {

    private static final Logger LOGGER = LoggerFactory.getLogger(FgsAbstract.class);

    protected static String outputPrefix;

    protected static void writeOutGraphML(Graph graph, Path outputFile) {
        if (graph == null) {
            return;
        }

        try (PrintStream graphWriter = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            String fileName = outputFile.getFileName().toString();

            String msg = String.format("Writing out GraphML file '%s'.", fileName);
            System.out.printf("%s: %s%n", DateTime.printNow(), msg);
            LOGGER.info(msg);
            XmlPrint.printPretty(GraphmlSerializer.serialize(graph, outputPrefix), graphWriter);
            msg = String.format("Finished writing out GraphML file '%s'.", fileName);
            System.out.printf("%s: %s%n", DateTime.printNow(), msg);
            LOGGER.info(msg);
        } catch (Throwable throwable) {
            String errMsg = String.format("Failed when writting out GraphML file '%s'.", outputFile.getFileName().toString());
            System.err.println(errMsg);
            LOGGER.error(errMsg, throwable);
        }
    }

    protected static void writeOutJson(Graph graph, Path outputFile) {
        if (graph == null) {
            return;
        }

        try (PrintStream graphWriter = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            String fileName = outputFile.getFileName().toString();

            String msg = String.format("Writing out Json file '%s'.", fileName);
            System.out.printf("%s: %s%n", DateTime.printNow(), msg);
            LOGGER.info(msg);

            JsonSerializer.writeToStream(JsonSerializer.serialize(graph, outputPrefix), graphWriter);

            msg = String.format("Finished writing out Json file '%s'.", fileName);
            System.out.printf("%s: %s%n", DateTime.printNow(), msg);
            LOGGER.info(msg);

        } catch (Throwable throwable) {
            String errMsg = String.format("Failed when writing out Json file '%s'.", outputFile.getFileName().toString());
            System.err.println(errMsg);
            LOGGER.error(errMsg, throwable);
        }
    }
}
