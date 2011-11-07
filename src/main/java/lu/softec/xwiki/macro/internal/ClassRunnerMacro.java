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
 *
 */
package lu.softec.xwiki.macro.internal;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletApplicationContext;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.container.servlet.ServletResponse;
import org.xwiki.context.Execution;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.EntityReferenceValueProvider;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.util.ParserUtils;

import lu.softec.xwiki.classloader.ClassLoaderCache;
import lu.softec.xwiki.macro.ClassRunnerMacroParameters;

/**
 * Example Macro.
 */
@Component
@Named("classrunner")
@Singleton
public class ClassRunnerMacro extends AbstractMacro<ClassRunnerMacroParameters> {

    /**
     * The description of the macro.
     */
    private static final String DESCRIPTION = "Execute a new POJO by executing its run(Writer,XWikiContext) or run(Writer, Map, XWikiContext)";
    /**
     * Used to find the parser from syntax identifier.
     */
    @Inject
    private ComponentManager componentManager;
    /**
     * Used to get the current context that we clone if the users asks to execute the included page in its own context.
     */
    @Inject
    private Execution execution;
    /**
     * Used to get the request and response access
     */
    @Inject
    private Container container;
    /**
     * Used as a factory for classloader
     */
    @Inject
    private ClassLoaderCache loaderf;
    /**
     * Used to access document content and check view access right.
     */
    @Inject
    private DocumentAccessBridge documentAccessBridge;
    /**
     * Used to create appropriate constant document references
     */
    @Inject
    @Named("current")
    private EntityReferenceValueProvider currentProvider;
    /**
     * Used to resolve document references
     */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> currentDocumentReferenceResolver;
    /**
     * Used to convert reference to string for older API
     */
    @Inject
    private EntityReferenceSerializer<String> E;
    /**
     * Used to clean result of the parser syntax.
     */
    private ParserUtils parserUtils = new ParserUtils();

    /**
     * Create and initialize the descriptor of the macro.
     */
    public ClassRunnerMacro() {
        super("ClassRunner", DESCRIPTION, ClassRunnerMacroParameters.class);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.macro.Macro#supportsInlineMode()
     */
    public boolean supportsInlineMode() {
        return true;
    }

    /**
     * Allows overriding the Document Access Bridge used (useful for unit tests).
     * 
     * @param documentAccessBridge the new Document Access Bridge to use
     */
    public void setDocumentAccessBridge(DocumentAccessBridge documentAccessBridge) {
        this.documentAccessBridge = documentAccessBridge;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.macro.Macro#execute(Object, String, MacroTransformationContext)
     */
    @SuppressWarnings("unchecked")
    public List<Block> execute(ClassRunnerMacroParameters parameters, String content, MacroTransformationContext context)
            throws MacroExecutionException {
        DocumentAccessHelper docHelper = new DocumentAccessHelper(documentAccessBridge, currentProvider, currentDocumentReferenceResolver, E);

        boolean showDetailedException = true;
        String executionResult;

        try {
            // Hide Exception to all except admins
            showDetailedException = docHelper.hasAdmin();

            String baseURL = parameters.getBaseURL();
            String key = parameters.getKey();

            // TODO: Ensure security when we know precisely the document source
            // if (!this.documentAccessBridge.hasProgrammingRights()) {
            // throw new MacroExecutionException("You must have programming rigths to use this macro.");
            // }

            if (!(this.container.getApplicationContext() instanceof ServletApplicationContext)) {
                throw new MacroExecutionException("This macro is currently implemented only for a Servlet Context.");
            }

            HttpServletRequest httpRequest = ((ServletRequest) container.getRequest()).getHttpServletRequest();
            HttpServletResponse httpResponse = ((ServletResponse) container.getResponse()).getHttpServletResponse();

            DocumentReference profile = docHelper.getProfile(httpRequest, httpResponse, key, parameters.getProfile());
            
            Collection<URL> pkgUrls = new ArrayList<URL>();
            Collection<URL> dpkgUrls = new ArrayList<URL>();
            List<String> groupIds = new ArrayList<String>();

            docHelper.getPackageList(profile, baseURL, pkgUrls, dpkgUrls, groupIds);

            if( pkgUrls.isEmpty() && dpkgUrls.isEmpty() ) {
                throw new MacroExecutionException("No package to load in " + profile + ", no chance to find a class to run!");
            }

            // Only display detailed stackTrace to user working on a debug version
            showDetailedException = docHelper.hasAdmin() || !dpkgUrls.isEmpty();

            Map<Object, Object> xcontext = (Map<Object, Object>) execution.getContext().getProperty("xwikicontext");

            // Place the name of the choosed Profile in the XWikiContext for access from velocity and the executed class
            if (xcontext != null) {
                xcontext.put(key, profile.getName());
            }

            DocumentReference currentDoc = this.documentAccessBridge.getCurrentDocumentReference();
            String currentDatabase = currentProvider.getDefaultValue(EntityType.WIKI);

            String className = parameters.getClassName();
            if (StringUtils.isEmpty(className)) {
                className = StringUtils.join(new String[]{currentDoc.getLastSpaceReference().getName(), ".", currentDoc.getName()});
            }

            boolean prefixed = false;
            for (String groupId : groupIds) {
                if (className.startsWith(groupId)) {
                    prefixed = true;
                    break;
                }
            }

            if (!prefixed) {
                className = StringUtils.join(new String[]{groupIds.get(0), ".", currentDatabase, ".", className});
            }

            ClassLoader loader = getClassLoader(pkgUrls, dpkgUrls);
            executionResult = execute(loader, className, parameters, xcontext);
        } catch (Exception e) {
            if (showDetailedException) {
                throw new MacroExecutionException("Server Internal Error", e);
            } else {
                executionResult = "\n{{html}}\n<span class=\"xwikirenderingerror\">Server Internal Error</span>\n{{/html}}\n";
            }
        }

        try {
            List<Block> result = Collections.emptyList();

            if (parameters.isOutput()) {
                XDOM parsedDom = parseSourceSyntax(executionResult, parameters.getParser(), context);

                result = parsedDom.getChildren();
                if (context.isInline()) {
                    this.parserUtils.removeTopLevelParagraph(result);
                }
            }

            return result;
        } catch (Exception e) {
            if (showDetailedException) {
                throw new MacroExecutionException("Server Internal Error", e);
            } else {
                throw new MacroExecutionException("Server Internal Error");
            }
        }
    }

    private ClassLoader getClassLoader(Collection<URL> pkgUrls, Collection<URL> dpkgUrls) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (!dpkgUrls.isEmpty()) {
            pkgUrls.addAll(dpkgUrls);
        }
        if (!pkgUrls.isEmpty()) {
            loader = loaderf.getURLClassLoader(pkgUrls.toArray(new URL[0]), loader, !dpkgUrls.isEmpty());
        }
        return loader;
    }

    protected String execute(ClassLoader loader, String className, ClassRunnerMacroParameters parameters, Object xcontext) throws MacroExecutionException {
        StringWriter stringWriter = new StringWriter();
        boolean hasArgs = (parameters.getRawProperties() != null);
        Map<String,Object> args = (hasArgs) ? parameters.getRawProperties() : new LinkedHashMap<String,Object>();

        Class<?> klass;
        try {
            klass = loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new MacroExecutionException(e.getMessage(), e);
        }

        Method[] methods = klass.getMethods();
        Method run1 = null;
        Method run2 = null;
        Method run3 = null;
        Method setContext1 = null;
        Method setContext2 = null;
        Method getParser = null;
        for (Method m : methods) {
            if (m.getName().equals("run")) {
                Class<?>[] types = m.getParameterTypes();
                if (types.length == 1 && types[0].isAssignableFrom(stringWriter.getClass())) {
                    run1 = m;
                } else if (types.length == 2 && types[0].isAssignableFrom(stringWriter.getClass())
                        && types[1].isAssignableFrom(xcontext.getClass())) {
                    run2 = m;
                } else if( types.length == 3 && types[0].isAssignableFrom(stringWriter.getClass())
                        && types[1].isAssignableFrom(args.getClass())
                        && types[2].isAssignableFrom(xcontext.getClass())) {
                    run3 = m;
                }
            } else if (m.getName().equals("setContext")) {
                Class<?>[] types = m.getParameterTypes();
                if (types.length == 1 && types[0].isAssignableFrom(xcontext.getClass())) {
                    setContext1 = m;
                } else if (types.length == 2 && types[0].isAssignableFrom(args.getClass())
                        && types[1].isAssignableFrom(xcontext.getClass())) {
                    setContext2 = m;
                }
            } else if (m.getName().equals("getParser")) {
                Class<?>[] types = m.getParameterTypes();
                if (types.length == 0) {
                    getParser = m;
                }
            }
            if( run2 != null && run3 != null && setContext1 != null && setContext2 != null && getParser != null)
                break;
        }

        if (run2 == null && run3 == null) {
            throw new MacroExecutionException(
                    "Unable to find the appropriate run(Writer,XWikiContext) or run(Writer,Map,XWikiContext) method in the class.");
        }

        try {
            Object obj = klass.newInstance();
            if(setContext2 != null && (hasArgs || setContext1 == null)) {
                setContext2.invoke(obj, args, xcontext);
            } else if(setContext1 != null) {
                setContext1.invoke(obj, xcontext);
            }
            if(setContext2 != null || setContext1 != null) {
                if(getParser != null) {
                    String parser = null;
                    parser = (String) getParser.invoke(obj);
                    if(parser != null) {
                        parameters.setParser(parser);
                    }
                }
                run1.invoke(obj, stringWriter);
            } else {
                if(run3 != null && (hasArgs || run2 == null)) {
                    run3.invoke(obj, stringWriter, args, xcontext);
                } else {
                    run2.invoke(obj, stringWriter, xcontext);
                }
            }
        } catch (Exception e) {
            throw new MacroExecutionException(e.getMessage(), e);
        }

        return (stringWriter.toString());
    }

    /**
     * Get the parser of the current wiki syntax.
     *
     * @param parserId the parser Id to retrieve or null for the current syntax parser
     * @param context the context of the macro transformation.
     * @return the parser of the current wiki syntax.
     * @throws MacroExecutionException Failed to find source parser.
     */
    protected Parser getSyntaxParser(String parserId, MacroTransformationContext context) throws MacroExecutionException {
        try {
            return (Parser) this.componentManager.lookup(Parser.class, (StringUtils.isNotEmpty(parserId)) ? parserId : context.getSyntax().toIdString());
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException("Failed to find source parser", e);
        }
    }

    /**
     * Parse provided content with the parser of the current wiki syntax.
     * 
     * @param content the content to parse.
     * @param parserId the parser Id of the parser to use
     * @param context the context of the macro transformation.
     * @return an XDOM containing the parser content.
     * @throws MacroExecutionException failed to parse content
     */
    protected XDOM parseSourceSyntax(String content, String parserId, MacroTransformationContext context) throws MacroExecutionException {
        Parser parser = getSyntaxParser(parserId, context);

        try {
            return parser.parse(new StringReader(content));
        } catch (ParseException e) {
            throw new MacroExecutionException("Failed to parse content [" + content + "] with Syntax parser ["
                    + parser.getSyntax() + "]", e);
        }
    }
}
