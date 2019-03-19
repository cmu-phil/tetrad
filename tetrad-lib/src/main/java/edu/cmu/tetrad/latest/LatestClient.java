package edu.cmu.tetrad.latest;

import edu.cmu.tetrad.util.ParamDescriptionsCopy;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

/**
 * Author : Jeremy Espino MD Created 9/20/16 11:06 AM
 */
public class LatestClient {

    private static final Logger LOGGER = Logger.getLogger(LatestClient.class);

    private static LatestClient theInstance = null;
    private String latestResult;

    private LatestClient() {
    }

    public static LatestClient getInstance() {
        if (theInstance == null) {
            theInstance = new LatestClient();
        }
        return theInstance;
    }

    public String getLatestResult() {
        return latestResult;

    }

    public String getLatestResult(int lineWidth) {
        StringBuilder truncatedLatestResult = new StringBuilder();

        if (latestResult != null) {
            truncatedLatestResult.append(latestResult);
            int i = 0;
            while ((i = truncatedLatestResult.indexOf(" ", i + lineWidth)) != -1) {
                truncatedLatestResult.replace(i, i + 1, "\n");
            }
        }

        return truncatedLatestResult.toString();
    }

    public boolean checkLatest(String softwareName, String version) {

        // For generating parameters HTML manual - Zhou
        ParamDescriptionsCopy instance = ParamDescriptionsCopy.getInstance();

        // generate html of parameters
        String html = "";
        Set<String> params = instance.getNames();
        for (String param : params) {
            html += "<h3 id=\"" + param + "\" class=\"parameter_description\">" + param + "</h3>\n"
                    + "<ul class=\"parameter_description_list\">\n"
                    + "<li>Short Description: <span id=\"" + param + "_short_desc\">" + instance.get(param).getShortDescription() + "</span></li>\n"
                    + "<li>Long Description: <span id=\"" + param + "_long_desc\">" + instance.get(param).getLongDescription() + "</span></li>\n"
                    + "<li>Default Value: <span id=\"" + param + "_default_value\">" + instance.get(param).getDefaultValue() + "</span></li>\n";

            if (instance.get(param).getDefaultValue() instanceof Integer) {
                html += "<li>Lower Bound: <span id=\"" + param + "_lower_bound\">" + instance.get(param).getLowerBoundInt() + "</span></li>\n"
                        + "<li>Upper Bound: <span id=\"" + param + "_upper_bound\">" + instance.get(param).getUpperBoundInt() + "</span></li>\n"
                        + "<li>Value Type: <span id=\"" + param + "_value_type\">Integer</span></td>\n";
            } else if (instance.get(param).getDefaultValue() instanceof Double) {
                html += "<li>Lower Bound: <span id=\"" + param + "_lower_bound\">" + instance.get(param).getLowerBoundDouble() + "</span></li>\n"
                        + "<li>Upper Bound: <span id=\"" + param + "_upper_bound\">" + instance.get(param).getUpperBoundDouble() + "</span></li>\n"
                        + "<li>Value Type: <span id=\"" + param + "_value_type\">Double</span></li>\n";
            } else if (instance.get(param).getDefaultValue() instanceof Boolean) {
                html += "<li>Lower Bound: <span id=\"" + param + "_lower_bound\"></span></li>\n"
                        + "<li>Upper Bound: <span id=\"" + param + "_upper_bound\"></span></li>\n"
                        + "<li>Value Type: <span id=\"" + param + "_value_type\">Boolean</span></li>\n";
            } else {
                html += "<li>Lower Bound: <span id=\"" + param + "_lower_bound\"></span></li>\n"
                        + "<li>Upper Bound: <span id=\"" + param + "_upper_bound\"></span></li>\n"
                        + "<li>Value Type: <span id=\"" + param + "_value_type\"></span></li>\n";
            }

            html += "</ul>\n\n";
        }

        // Save the generated HTML in target folder so it won't get pushed to github
        try (PrintWriter out = new PrintWriter("tetrad-gui/target/parameters.html")) {
            out.println(html);
        } catch (FileNotFoundException ex) {
            java.util.logging.Logger.getLogger(LatestClient.class.getName()).log(Level.SEVERE, null, ex);
        }

        // End of generating parameters HTML manual - Zhou
        LOGGER.debug("running version: " + version);

        final Properties applicationProperties = new Properties();
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("tetrad-lib.properties")) {
            if (inputStream != null) {
                applicationProperties.load(inputStream);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not read tetrad-lib.properties file", exception);
        }

        String baseUrl = applicationProperties.getProperty("latest.version.url");
        if (baseUrl == null) {
            latestResult = String.format("Running version %s but unable to contact version server. To disable checking use the skip-latest option.", version);
            return false;
        }

        // create a timeout after 5 seconds
        int timeout = 5000;
        RequestConfig reqConfig = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout).build();

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(reqConfig).build()) {
            HttpGet request = new HttpGet(baseUrl + "/latest/version/latest?softwareName=" + softwareName + "&softwareVersion=" + version);
            request.addHeader("content-type", "application/json");
            HttpResponse result = httpClient.execute(request);

            if (result != null) {
                String json = EntityUtils.toString(result.getEntity(), "UTF-8");
                LOGGER.debug("response from latest version server: " + json);

                if (json != null) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    SoftwareVersion softwareVersion = gson.fromJson(json, SoftwareVersion.class);

                    if (softwareVersion.getSoftwareVersion().equalsIgnoreCase(version)) {
                        latestResult = String.format("Running version %s which is the latest version.  To disable checking use the skip-latest option.", version);
                        return true;
                    } else {
                        latestResult = String.format("Running version %s but the latest version is %s.  To disable checking use the skip-latest option.", version, softwareVersion.getSoftwareVersion());
                        return false;
                    }
                }
            }

            latestResult = String.format("Unable to communicate with version server. Running version %s.  To disable checking use the skip-latest option.", version);
            return false;

        } catch (Exception ex) {
            LOGGER.error("Could not contact server for latest version", ex);
            latestResult = String.format("Running version %s but unable to contact latest version server.  To disable checking use the skip-latest option.", version);
            return false;

        }

    }
}
