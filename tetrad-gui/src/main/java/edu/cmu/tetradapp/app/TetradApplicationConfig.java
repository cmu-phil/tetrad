///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.session.SessionNode;
import edu.cmu.tetrad.util.DefaultTetradLoggerConfig;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradLoggerConfig;
import edu.cmu.tetradapp.editor.ParameterEditor;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Represents the configuration details for the Tetrad application.
 *
 * @author Tyler Gibson
 */
public class TetradApplicationConfig {


    /**
     * The singleton instance
     */
    private final static TetradApplicationConfig instance = new TetradApplicationConfig();

    /**
     * A map from ids to node configs.
     */
    private Map<String, SessionNodeConfig> configs;


    /**
     * A map from model classes to the configurations that handle them.
     */
    private Map<Class, SessionNodeConfig> classMap = new HashMap<Class, SessionNodeConfig>();

    /**
     * Constructs the configuration.
     */
    public TetradApplicationConfig() {
        String path;

        if (Preferences.userRoot().getBoolean("experimental", false)) {
            path = "/resources/configplay.xml";
        }
        else {
            path = "/resources/configpost.xml";
        }

//        String PATH = "/resources/configplay.xml";
        InputStream stream = this.getClass().getResourceAsStream(path);
        Builder builder = new Builder(true);
        try {
            Document doc = builder.build(stream);
            this.configs = buildConfiguration(doc.getRootElement());
            for (SessionNodeConfig config : this.configs.values()) {
                Class[] models = config.getModels();
                for (Class model : models) {
                    if (classMap.containsKey(model)) {
                        throw new IllegalStateException("Model " + model + " has two configurations");
                    }
                    this.classMap.put(model, config);
                }
            }
        }
        catch (Exception ex) {
            throw new IllegalStateException("Chould not load configuration", ex);
        }
    }

    //============================== Public Methods =====================================//


    /**
     * @return an instance of the session configuration.
     */
    public static TetradApplicationConfig getInstance() {
        return instance;
    }


    /**
     * @return the <code>SessionNodeConfig</code> to be used for the given id,
     * or null if there isn't one defined for the given id.
     *
     * @param id - The id of the session config (e.g., "Graph" etc)
     */
    public SessionNodeConfig getSessionNodeConfig(String id) {
        return this.configs.get(id);
    }


    /**
     * @return the <code>SessionNodeConfig</code> that the given model is part
     * of.
     */
    public SessionNodeConfig getSessionNodeConfig(Class model) {
        return this.classMap.get(model);
    }

    //============================== Private Methods ====================================//


    /**
     * Loads the configuration from the root element of the configuratin.xml
     * file. It is assumed that the document has been validated against its dtd
     * already.
     */
    private static Map<String, SessionNodeConfig> buildConfiguration(Element root) {
        Elements elements = root.getChildElements();
        ClassLoader loader = getClassLoader();
        Map<String, SessionNodeConfig> configs = new HashMap<String, SessionNodeConfig>();
        for (int i = 0; i < elements.size(); i++) {
            Element node = elements.get(i);
            String id = node.getAttributeValue("id");
            DefaultNodeConfig nodeConfig = new DefaultNodeConfig(id);
            Elements nodeElements = node.getChildElements();
            for (int k = 0; k < nodeElements.size(); k++) {
                Element child = nodeElements.get(k);
                if ("models".equals(child.getQualifiedName())) {
                    nodeConfig.setSessionNodeModelConfig(buildModelConfigs(child));
                } else if ("display-component".equals(child.getQualifiedName())) {
                    String image = child.getAttributeValue("image");
                    String value = getValue(child);
                    Class compClass = value == null ? null : loadClass(loader, value);
                    nodeConfig.setDisplayComp(image, compClass);
                } else if ("model-chooser".equals(child.getQualifiedName())) {
                    String title = child.getAttributeValue("title");
                    String value = getValue(child);
                    Class chooserClass = value == null ? null : loadClass(loader, value);
                    nodeConfig.setChooser(title, chooserClass);
                } else if ("tooltip".equals(child.getQualifiedName())) {
                    nodeConfig.setTooltipText(child.getValue());
                } else {
                    throw new IllegalStateException("Unknown element " + child.getQualifiedName());
                }
                configs.put(id, nodeConfig);
            }
        }
        return configs;
    }

    /**
     * @return the value of the elemnt, will return null if its an empty
     * string.
     */
    private static String getValue(Element value) {
        String v = value.getValue();
        if (v != null && v.length() == 0) {
            return null;
        }
        return v;
    }


    /**
     * Builds the model configs from the models element.
     */
    private static List<SessionNodeModelConfig> buildModelConfigs(Element models) {
        Elements modelElements = models.getChildElements();
        List<SessionNodeModelConfig> configs = new LinkedList<SessionNodeModelConfig>();
        ClassLoader loader = getClassLoader();
        for (int i = 0; i < modelElements.size(); i++) {
            Element model = modelElements.get(i);
            String name = model.getAttributeValue("name");
            String acronym = model.getAttributeValue("acronym");
            String help = model.getAttributeValue("help");
            String category = model.getAttributeValue("category");
            Class modelClass = null;
            Class editorClass = null;
            Class paramsClass = null;
            Class paramsEditorClass = null;
            TetradLoggerConfig loggerConfig = null;
            Elements elements = model.getChildElements();
            for (int k = 0; k < elements.size(); k++) {
                Element element = elements.get(k);
                if ("model-class".equals(element.getQualifiedName())) {
                    modelClass = loadClass(loader, element.getValue());
                } else if ("editor-class".equals(element.getQualifiedName())) {
                    editorClass = loadClass(loader, element.getValue());
                } else if ("params-class".equals(element.getQualifiedName())) {
                    paramsClass = loadClass(loader, element.getValue());
                } else if ("params-editor-class".equals(element.getQualifiedName())) {
                    paramsEditorClass = loadClass(loader, element.getValue());
                } else if ("logger".equals(element.getQualifiedName())) {
                    loggerConfig = configureLogger(element);
                } else {
                    throw new IllegalStateException("Unknown element: " + element.getQualifiedName());
                }
            }
            // if there is a logger config, add it with its model to the tetrad logger.
            if(loggerConfig != null){
                TetradLogger.getInstance().addTetradLoggerConfig(modelClass, loggerConfig);
            }
            
            SessionNodeModelConfig config = new DefaultModelConfig(modelClass, paramsClass,
                    paramsEditorClass, editorClass, name, acronym, help, category);
            configs.add(config);
        }
        return configs;
    }


    /**
     * Configures the logger that the given element represents and returns its id.
     */
    private static TetradLoggerConfig configureLogger(Element logger) {
        Elements elements = logger.getChildElements();
        List<TetradLoggerConfig.Event> events = new LinkedList<TetradLoggerConfig.Event>();
        List<String> defaultLog = new LinkedList<String>();
        for (int i = 0; i < elements.size(); i++) {
            Element event = elements.get(i);
            String eventId = event.getAttributeValue("id");
            String description = event.getAttributeValue("description");
            String defaultOption = event.getAttributeValue("default");
            if (defaultOption != null && defaultOption.equals("on")) {
                defaultLog.add(eventId);
            }
            events.add(new DefaultTetradLoggerConfig.DefaultEvent(eventId, description));
        }
        TetradLoggerConfig config = new DefaultTetradLoggerConfig(events);
        // set any defaults
        for (String event : defaultLog) {
            config.setEventActive(event, true);
        }
        return config;
    }


    /**
     * Creates the display comp from an image/comp class.  If the not null then
     * it is given as an argument to the constructor of the given class. IF the
     * givne comp is null then the default is used.
     */
    private static SessionDisplayComp createDisplayComp(String image, Class compClass) {
        if (compClass == null) {
            return new StdDisplayComp(image);
        }
        try {
            if (image == null) {
                return (SessionDisplayComp) compClass.newInstance();
            }
            Constructor constructor = compClass.getConstructor(String.class);
            return (SessionDisplayComp) constructor.newInstance(image);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Could not create display component", ex);
        }
    }


    private static Class loadClass(ClassLoader loader, String className) {
        try {
            return loader.loadClass(className.trim());
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("The class name " + className + " could not be found", e);
        }
    }


    /**
     * @return a class loader to use.
     */
    private static ClassLoader getClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = TetradApplicationConfig.class.getClassLoader();
        }
        // if its still null nothing we can do.
        if (loader == null) {
            throw new NullPointerException("Class loader was null could not load handler");
        }
        return loader;
    }


    /**
     * removes newline and extra white space (Seems to be sensitive to this,
     * when its html)
     */
    private static String pruneTooltipText(String text) {
        int size = text.length();
        int i = 0;
        StringBuilder builder = new StringBuilder(size);
        while (i < size) {
            char c = text.charAt(i);
            if (c == ' ') {
                builder.append(c);
                // skip until non whitespace is found
                while (i < size - 1 && c == ' ') {
                    i++;
                    c = text.charAt(i);
                }
            }
            if (c != '\n') {
                builder.append(c);
            }
            i++;
        }
        return builder.toString().trim();
    }


    private static boolean matches(Class[] params, Object[] arguments) {
        if (params.length != arguments.length) {
            return false;
        }

        for (int i = 0; i < params.length; i++) {
            Class param = params[i];
            if (!param.isInstance(arguments[i])) {
                return false;
            }
        }

        return true;
    }

    //============================== Inner classes =======================================//

    /**
     * Default implementation of the session config. Most functionality is
     * implemented by static methods from the outer-class.
     */
    private static class DefaultNodeConfig implements SessionNodeConfig {

        /**
         * ALl the config info of the configuration.
         */
        private Map<Class, SessionNodeModelConfig> modelMap = new HashMap<Class, SessionNodeModelConfig>();
        private List<SessionNodeModelConfig> models;
        private String image;
        private Class compClass;
        private String tooltip;
        private String id;
        private String chooserTitle;
        private Class chooserClass;


        public DefaultNodeConfig(String id) {
            if (id == null) {
                throw new NullPointerException("The given id must not be null");
            }
            this.id = id;
        }


        public SessionNodeModelConfig getModelConfig(Class model) {
            return this.modelMap.get(model);
        }

        public Class[] getModels() {
            Class[] modelClasses = new Class[this.models.size()];
            for (int i = 0; i < this.models.size(); i++) {
                modelClasses[i] = this.models.get(i).getModel();
            }
            return modelClasses;
        }

        public String getTooltipText() {
            return this.tooltip;
        }

        public ModelChooser getModelChooserInstance(SessionNode sessionNode) {
            ModelChooser chooser;
            if (this.chooserClass == null) {
                chooser = new DefaultModelChooser();
            } else {
                try {
                    chooser = (ModelChooser) this.chooserClass.newInstance();
                    chooser.setSessionNode(sessionNode);
                }
                catch (InstantiationException e) {
                    throw new IllegalStateException("Model chooser must have empty constructor", e);
                }
                catch (IllegalAccessException e) {
                    throw new IllegalStateException("Model chooser must have empty constructor", e);
                }
            }

            Class[] consistentClasses = sessionNode.getConsistentModelClasses();

            List<SessionNodeModelConfig> filteredModels = new ArrayList<SessionNodeModelConfig>();

            for (SessionNodeModelConfig config : this.models) {
                Class clazz = config.getModel();

                boolean exists = false;

                for (Class clazz2 : consistentClasses) {
                    if (clazz.equals(clazz2)) {
                        exists = true;
                        break;
                    }
                }

                if (exists) {
                    filteredModels.add(config);
                }
            }

            chooser.setSessionNode(sessionNode);
            chooser.setNodeId(this.id);
            chooser.setTitle(this.chooserTitle);
//            chooser.setNodeName(sessionNode.getDisplayName());
//            chooser.setModelConfigs(new ArrayList<SessionNodeModelConfig>(this.models));
            chooser.setModelConfigs(new ArrayList<SessionNodeModelConfig>(filteredModels));
            chooser.setup();
            return chooser;
        }

        public SessionDisplayComp getSessionDisplayCompInstance() {
            return createDisplayComp(this.image, this.compClass);
        }

        //========================= Private Methods ===============================//

        private void setChooser(String title, Class chooserClass) {
            if (title == null) {
                throw new NullPointerException("The chooser title must not be null");
            }
            this.chooserTitle = title;
            this.chooserClass = chooserClass;
        }

        private void setTooltipText(String text) {
            if (text == null) {
                throw new NullPointerException("The give toolip text must not be null");
            }
            this.tooltip = pruneTooltipText(text);
        }

        private void setDisplayComp(String image, Class comp) {
            if (image == null && comp == null) {
                throw new NullPointerException("Must have an image or a display component class defined");
            }
            this.image = image;
            this.compClass = comp;
        }

        private void setSessionNodeModelConfig(List<SessionNodeModelConfig> configs) {
            this.models = configs;
            for (SessionNodeModelConfig config : configs) {
                this.modelMap.put(config.getModel(), config);
            }
        }
    }


    /**
     * THe default implementation of the model config.
     */
    private static class DefaultModelConfig implements SessionNodeModelConfig {

        private Class model;
        private Class params;
        private Class paramsEditor;
        private Class editor;
        private String name;
        private String acronym;
        private String help;
        private String category;

        public DefaultModelConfig(Class model, Class params, Class paramsEditor, Class editor,
                                  String name, String acronym, String help, String category
        ) {
            if (model == null || editor == null || name == null || acronym == null) {
                throw new NullPointerException("Values must not be null");
            }
            this.model = model;
            this.params = params;
            this.paramsEditor = paramsEditor;
            this.editor = editor;
            this.name = name;
            this.help = help;
            this.acronym = acronym;
            this.category = category;
        }

        public String getHelpIdentifier() {
            return this.help;
        }

        public String getCategory() {
            return this.category;
        }


        public Class getModel() {
            return this.model;
        }

        public String getName() {
            return this.name;
        }

        public String getAcronym() {
            return this.acronym;
        }

        public JPanel getEditorInstance(Object[] arguments) {
            Class[] parameters = new Class[arguments.length];

            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = arguments[i].getClass();
            }

            Constructor constructor = null;

            try {
                constructor = editor.getConstructor(parameters);
            }
            catch (Exception ex) {
                // do nothing, try to find a constructor below.
            }

            if (constructor == null) {
                // try to find object-compatable constructor.
                Constructor[] constructors = editor.getConstructors();
                for (Constructor _constructor : constructors) {
                    Class[] params = _constructor.getParameterTypes();
                    if (matches(params, arguments)) {
                        constructor = _constructor;
                        break;
                    }
                }
            }

            if (constructor == null) {
                throw new NullPointerException("Could not find constructor in " + editor + " for model: " + this.model);
            }

            try {
                return (JPanel) constructor.newInstance(arguments);
            }
            catch (Exception ex) {
                throw new IllegalStateException("Could not construct editor", ex);
            }
        }

        public Params getParametersInstance() {
            if (this.params != null) {
                try {
                    return (Params) this.params.newInstance();
                }
                catch (ClassCastException e) {
                    throw new IllegalStateException("Model params doesn't implement Params", e);
                }
                catch (Exception e) {
                    throw new IllegalStateException("Error instantiating params, must be empty constructor", e);
                }
            }
            return null;
        }

        public ParameterEditor getParameterEditorInstance() {
            if (this.paramsEditor != null) {
                try {
                    return (ParameterEditor) this.paramsEditor.newInstance();
                }
                catch (ClassCastException e) {
                    throw new IllegalStateException("Params editor must implement ParameterEditor", e);
                }
                catch (Exception e) {
                    throw new IllegalStateException("Error intatiating params editor, must have empty constructor", e);
                }
            }
            return null;
        }

    }


}




