package edu.cmu.tetrad.session;

import java.util.Map;

/**
 * Created by jdramsey on 3/14/16.
 */
public interface SimulationParamsSource {
    Map<String, String> getParamSettings();

    Map<String, String> getAllParamSettings();

    void setAllParamSettings(Map<String, String> paramSettings);
}
