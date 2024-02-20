package edu.cmu.tetradapp.session;

import java.util.Map;

/**
 * Created by jdramsey on 3/14/16.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SimulationParamsSource {
    /**
     * <p>getParamSettings.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<String, String> getParamSettings();

    /**
     * <p>getAllParamSettings.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<String, String> getAllParamSettings();

    /**
     * <p>setAllParamSettings.</p>
     *
     * @param paramSettings a {@link java.util.Map} object
     */
    void setAllParamSettings(Map<String, String> paramSettings);
}
