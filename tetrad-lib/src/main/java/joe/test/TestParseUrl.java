package joe.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.DataReader;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestParseUrl {

    String[] coins = {
            "bitcoin",
            "ethereum",
            "ripple",
            "bitcoin-cash",
            "cardano",
            "stellar",
            "litecoin",
            "nem",
            "neo",
            "eos",
            "iota",
            "dash",
            "monero",
            "bitconnect",
            "electroneum"
    };

    @Test
    public void test1() {
        InputStream is = null;
        BufferedReader br;
        String line;

        try {

            for (String coin : coins) {
                URL url = new URL("https://coinmarketcap.com/currencies/" + coin + "/historical-data/?start=20150121&end=20180121");
                is = url.openStream();  // throws an IOException
                br = new BufferedReader(new InputStreamReader(is));

                List<double[]> recordList = new ArrayList<>();

                while ((line = br.readLine()) != null) {

                    if (line.contains("<tr class=\"text-right\">")) {
                        for (int i = 0; i < 1; i++) {
                            br.readLine();
                        }

                        double[] record = new double[6];

                        boolean fullRecord = true;

                        for (int i = 0; i < 6; i++) {
                            line = br.readLine();

//                            line = line.split("<")[1];
//                            line = line.split(">")[1];
//                            line = line.replaceAll(",", "");

                            line = line.split("\"")[1];

                            if (line.equalsIgnoreCase("-")) {
                                fullRecord = false;
                                continue;
                            }

                            double d = Double.parseDouble(line);
                            record[i] = d;
                        }

                        if (fullRecord) {
                            recordList.add(record);
                        }
                    }
                }

                List<Node> variables = new ArrayList<>();

                variables.add(new ContinuousVariable("Open"));
                variables.add(new ContinuousVariable("High"));
                variables.add(new ContinuousVariable("Low"));
                variables.add(new ContinuousVariable("Close"));
                variables.add(new ContinuousVariable("Volume"));
                variables.add(new ContinuousVariable("MarketCap"));

                DoubleDataBox box = new DoubleDataBox(recordList.size(), 6);
                DataSet dataSet = new BoxDataSet(box, variables);

                for (int i = 0; i < recordList.size(); i++) {
                    for (int j = 0; j < 6; j++) {
                        dataSet.setDouble(recordList.size() - i - 1, j, recordList.get(i)[j]);
                    }
                }

                DataWriter.writeRectangularData(dataSet, new PrintWriter(new File(
                        "/Users/user/Downloads/" + coin)), '\t');
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException ioe) {
                // nothing to see here
            }
        }
    }

    @Test
    public void test2() {


        int targetIndex = 0;
        final int numLags = 2;
        final int sampleSize = 75;


        try {

//            for (int r = dataSet.getNumRows() - 30; r < dataSet.getNumRows() - 2; r++) {
//                adviceForInitialRecords(r, coins[0], targetIndex, numLags, dataSet);
//            }

            for (int c = 0; c < coins.length; c++) {
                DataReader reader = new ContinuousTabularDataFileReader(new File("/Users/user/Downloads/" + coins[c]), Delimiter.TAB);
                DataSet dataSet = readData(reader);
                final int r = dataSet.getNumRows() - 1;
                adviceForInitialRecords(r, sampleSize, coins[c], targetIndex, numLags, dataSet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DataSet readData(DataReader reader) throws IOException {
        Dataset dataset = reader.readInData();
        DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            dataSet.setDouble(i, 4, dataSet.getDouble(i, 4) / 1e6);
        }

        return dataSet;
    }

    private void adviceForInitialRecords(int r, int sampleSize, String coin, int targetIndex, int numLags, DataSet dataSet) {

        List<Integer> _rows = new ArrayList<>();

        for (int i = Math.max(0, r - 1 - sampleSize); i < r - 1; i++) {
            _rows.add(i);
        }

        int[] rows = new int[_rows.size()];
        for (int i = 0; i < _rows.size(); i++) rows[i] = _rows.get(i);

        DataSet subset = dataSet.subsetRows(rows);
        DataSet lagged = TimeSeriesUtils.createLagData(subset, numLags);
        List<Node> regressors = new ArrayList<>(lagged.getVariables());
        Node target = regressors.get(targetIndex);

        for (Node node : new ArrayList<>(regressors)) {
            final int lag = TimeSeriesUtils.getLag(node.getName());
            if (lag == 0) regressors.remove(node);
        }

        RegressionDataset regressionDataset = new RegressionDataset(lagged);

        RegressionResult result = regressionDataset.regress(target, regressors);

        double[] x = new double[lagged.getNumColumns() - 6];

        int i = 0;

        for (int w = 0; w < numLags; w++) {
            for (int s = 0; s < 6; s++) {
                x[i++] = subset.getDouble(subset.getNumRows() - numLags, s);
            }
        }

//        System.out.println(Arrays.toString(x));

        double predictedValue = result.getPredictedValue(x);

        final double previousValue = dataSet.getDouble(r - 2, targetIndex);
        final double actualValue = dataSet.getDouble(r - 1, targetIndex);

        System.out.println();
        System.out.println("r = " + r);
        System.out.println("Coin = " + coin);
        System.out.println("Target = " + target);
        System.out.println("Previous value = " + previousValue);
        System.out.println("Predicted value = " + predictedValue);
        System.out.println("(Actual value = " + actualValue + ")");

        if (predictedValue > previousValue) {
            System.out.println("BUY BUY BUY!");
        } else {
            System.out.println("SELL SELL SELL!");
        }
    }
}
