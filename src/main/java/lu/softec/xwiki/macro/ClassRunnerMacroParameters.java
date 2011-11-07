/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package lu.softec.xwiki.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.properties.RawProperties;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyMandatory;

/**
 * Parameters for the {@link lu.softec.xwiki.macro.internal.ClassRunnerMacro} Macro.
 */
public class ClassRunnerMacroParameters implements RawProperties
{
    /**
     * @see {@link #getClassName()}
     */
    private String className;

    /**
     * @see {@link #getBaseURL()}
     */
    private String baseURL = "http://localhost:8080/resources/java/";

    /**
     * @see {@link #getProfile()}
     */
    private String profile = "Default";

    /**
     * Indicate the output result has to be inserted back in the document.
     */
    private boolean output = true;

    /**
     * Indicate the parser ID of the parser to use for output (Default to the document syntax).
     */
    private String parser = null;

    /**
     * @see {@link #getKey()}
     */
    private String key = "clpkg";

     /**
     * @see {@link #set(String,Object)}
     */
    private Map<String,Object> raw = new LinkedHashMap<String, Object>();

    /**
     * @return the classname of the Class to be run
     */
    public String getClassName()
    {
        return this.className;
    }
    
    /**
     * @param className the fully qualified classname of the Class to be run
     */
    @PropertyMandatory
    @PropertyDescription("Fully qualified classname of the Class that will be run")
    public void setClassName(String className)
    {
        this.className = className;
    }
    
    /**
     * @return the base URL for accessing the required packages
     */
    public String getBaseURL()
    {
        return this.baseURL;
    }
    
    /**
     * @param baseURL Base URL to access the package that will be loaded for running the Class
     */
    @PropertyDescription("Base URL to access the package that will be loaded for running the Class")
    public void setBaseURL(String baseURL)
    {
        this.baseURL = baseURL;
    }
    
    /**
     * @param output indicate the output result has to be inserted back in the document.
     */
    @PropertyDescription("indicate the output result has to be inserted back in the document")
    public void setOutput(boolean output)
    {
        this.output = output;
    }

    /**
     * @return indicate the output result has to be inserted back in the document.
     */
    public boolean isOutput()
    {
        return this.output;
    }

    /**
     * @param output indicate the output result has to be inserted back in the document.
     */
    @PropertyDescription("indicate the parser ID of the parser to use to parse the output result")
    public void setParser(String parser)
    {
        this.parser = parser;
    }

    /**
     * @return the parser to use for the output.
     */
    public String getParser()
    {
        return this.parser;
    }

    /**
     * @param profile defines the default profile used.
     */
    @PropertyDescription("defines the default profile used")
    public void setProfile(String profile)
    {
        this.profile = profile;
    }

    /**
     * @return defines the default profile used.
     */
    public String getProfile()
    {
        return this.profile;
    }
    
    /**
     * @param key defines the key used for request argument, cookie name and context attribute.
     */
    @PropertyDescription("defines the key used for request argument, cookie name and context attribute. Default is clpkg")
    public void setKey(String key)
    {
        this.key = key;
    }

    /**
     * @return retrieve the key used for request argument, cookie name and context attribute.
     */
    public String getKey()
    {
        return this.key;
    }

    /**
     * Implements Raw Properties
     * @param string    Property name
     * @param o         Property value
     */
    public void set(String name, Object o) {
        if( raw.containsKey(name) ) {
            Object lo = raw.get(name);
            if( lo instanceof ArrayList ) {
                List<Object> a = (List<Object>) lo;
                a.add(o);
            } else {
                List<Object> a = new ArrayList<Object>();
                a.add(lo);
                a.add(o);
                raw.put(name, a);
            }
        } else {
            raw.put(name, o);
        }
    }

    /**
     * @return the remaining raw arguments
     */
    public Map<String,Object> getRawProperties()
    {
        if( this.raw.isEmpty() )
            return null;
        return this.raw;
    }
}
