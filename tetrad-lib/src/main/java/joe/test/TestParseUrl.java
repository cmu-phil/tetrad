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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TestParseUrl {
    String[] coins = {
                    "bitcoin",
//                    "ethereum",
//                    "ripple",
//                    "bitcoin-cash",
//                    "cardano",
//                    "litecoin",
//                    "monero",
//                    "bitconnect",
//                    "sirin-labs-token",
//            "electroneum"
    };


    @Test
    public void test1() {
        InputStream is = null;
        BufferedReader br;
        String line;

        try {

            for (String coin : coins) {
                URL url = new URL("https://coinmarketcap.com/currencies/" + coin + "/historical-data/?start=20170121&end=20180121");
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
        } catch (MalformedURLException mue) {
            mue.printStackTrace();
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

        DataReader reader = new ContinuousTabularDataFileReader(new File("/Users/user/Downloads/" + coins[0]), Delimiter.TAB);

        try {
            Dataset dataset = reader.readInData();
            DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

            System.out.println(dataSet);
            DataSet lagged = TimeSeriesUtils.createLagData(dataSet, 10);

            List<Node> regressors = new ArrayList<>(lagged.getVariables());

            Node target = regressors.get(4);

            for (Node node : new ArrayList<>(regressors)) {
                final int lag = TimeSeriesUtils.getLag(node.getName());
                final String name = TimeSeriesUtils.getNameNoLag(node.getName());
                if (lag == 0 ) regressors.remove(node);
                if (!name.equalsIgnoreCase("MarketCap")) regressors.remove(node);
            }

            System.out.println("Coin = " + coins[0]);
            System.out.println("Target = " + target);

            RegressionDataset regressionDataset = new RegressionDataset(lagged);

            RegressionResult result = regressionDataset.regress(target, regressors);

            System.out.println(result);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
