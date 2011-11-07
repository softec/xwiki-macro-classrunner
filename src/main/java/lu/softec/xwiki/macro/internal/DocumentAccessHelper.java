/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lu.softec.xwiki.macro.internal;

import java.net.URL;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.EntityReferenceValueProvider;
import org.xwiki.rendering.macro.MacroExecutionException;

/**
 * Helper class to access documents and objects
 */
public class DocumentAccessHelper
{

    private static final String CODESPACE = "ClassRunnerCode";

    private static final String JAVAPACKAGECLASS = "JavaPackageClass";

    private static final String PROFILEINCLUDECLASS = "JavaProfileIncludeClass";

    private static final String DATASPACE = "ClassRunnerData";

    private static final String DEFAULTPROFILE = "ClassRunnerData";

    private DocumentAccessBridge documentAccessBridge;

    private DocumentReferenceResolver<String> profileResolver;

    private EntityReferenceSerializer<String> E;

    private EntityReference defaultProfile;

    private DocumentReference javaPackageClass;

    private DocumentReference profileIncludeClass;

    private boolean hasAdmin;

    public boolean hasAdmin()
    {
        return hasAdmin;
    }

    public DocumentAccessHelper(DocumentAccessBridge bridge, EntityReferenceValueProvider provider,
        DocumentReferenceResolver<String> resolver, EntityReferenceSerializer<String> serializer)
    {
        documentAccessBridge = bridge;
        profileResolver = resolver;
        E = serializer;

        defaultProfile = new EntityReference(DEFAULTPROFILE, EntityType.DOCUMENT, new EntityReference(DATASPACE, EntityType.SPACE));
        javaPackageClass = new DocumentReference(provider.getDefaultValue(EntityType.WIKI), CODESPACE, JAVAPACKAGECLASS);
        profileIncludeClass = new DocumentReference(provider.getDefaultValue(EntityType.WIKI), CODESPACE, PROFILEINCLUDECLASS);
        hasAdmin = this.documentAccessBridge.isDocumentEditable(getProfileRef(""));
    }

    private DocumentReference getProfileRef(String pkgName)
    {
        return profileResolver.resolve(pkgName,defaultProfile);
    }

    public DocumentReference getProfile(HttpServletRequest httpRequest, HttpServletResponse httpResponse, String key,
        String defaultProfileName) throws MacroExecutionException
    {
        DocumentReference profile = null;

        if (hasAdmin) {
            String requestClpkg = httpRequest.getParameter(key);
            String cookieClpkg = getCookieValue(httpRequest, key);

            if (requestClpkg != null) {
                if (requestClpkg.length() != 0) {
                    profile = getProfileRef(requestClpkg);
                    if (!this.documentAccessBridge.exists(profile)) {
                        profile = null;
                    }
                }
            } else if (cookieClpkg != null && cookieClpkg.length() != 0) {
                profile = getProfileRef(cookieClpkg);
                if (!this.documentAccessBridge.exists(profile)) {
                    profile = null;
                }
            }

            if (profile != null) {
                if (cookieClpkg == null || !profile.equals(getProfileRef(cookieClpkg))) {
                    addCookie(httpResponse, key, requestClpkg, -1);
                }
            } else if (cookieClpkg != null) {
                removeCookie(httpResponse, key, httpRequest);
            }
        }

        if (profile == null) {
            profile = getProfileRef(this.documentAccessBridge.getCurrentUser().replaceAll("^(.+:)?XWiki.", ""));

            if (!this.documentAccessBridge.exists(profile)) {
                profile = getProfileRef(defaultProfileName);
            }

            if (!this.documentAccessBridge.exists(profile)) {
                profile = getProfileRef("");
            }

            if (!this.documentAccessBridge.exists(profile)) {
                throw new MacroExecutionException("ClassLoader profile is missing.");
            }
        }

        return profile;
    }

    private void removeCookie(HttpServletResponse httpResponse, String cookieName, HttpServletRequest httpRequest)
    {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setVersion(1);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        httpResponse.addCookie(cookie);
    }

    private void addCookie(HttpServletResponse httpResponse, String cookieName, String cookieValue, int age)
    {
        Cookie cookie = new Cookie(cookieName, cookieValue);
        cookie.setVersion(1);
        cookie.setMaxAge(age);
        cookie.setPath("/");
        httpResponse.addCookie(cookie);
    }

    private Cookie getCookie(HttpServletRequest httpRequest, String cookieName)
    {
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return (cookie);
                }
            }
        }

        return null;
    }

    private String getCookieValue(HttpServletRequest httpRequest, String cookieName)
    {
        Cookie cookie = getCookie(httpRequest, cookieName);
        if (cookie != null) {
            return cookie.getValue();
        }
        return null;
    }

    public void getPackageList(DocumentReference profile, String baseURL, Collection<URL> pkgUrls,
        Collection<URL> dpkgUrls, List<String> groupIds) throws MacroExecutionException
    {
        int nb = 0;
        String artifactId;
        try {
            while ((artifactId =
                (String) this.documentAccessBridge.getProperty(E.serialize(profile), E.serialize(javaPackageClass), nb,
                    "artifactId")) != null) {
                String version =
                    (String) this.documentAccessBridge.getProperty(E.serialize(profile), E.serialize(javaPackageClass),
                        nb, "version");
                String groupId =
                    (String) this.documentAccessBridge.getProperty(E.serialize(profile), E.serialize(javaPackageClass),
                        nb, "groupId");
                if (!groupIds.contains(groupId)) {
                    groupIds.add(groupId);
                }
                String packaging =
                    (String) this.documentAccessBridge.getProperty(E.serialize(profile), E.serialize(javaPackageClass),
                        nb, "packaging");
                URL url;
                if (packaging.equals("jar")) {
                    url = new URL(StringUtils.join(new String[] {baseURL, artifactId, "-", version, ".jar"}));
                } else {
                    url = new URL(StringUtils.join(new String[] {baseURL, artifactId, "-", version, "/"}));
                }

                if (version.endsWith("-SNAPSHOT")) {
                    dpkgUrls.add(url);
                } else {
                    pkgUrls.add(url);
                }
                nb++;
            }

            nb = 0;
            String iPkgName;
            while ((iPkgName =
                (String) this.documentAccessBridge.getProperty(E.serialize(profile), E.serialize(profileIncludeClass),
                    nb, "name")) != null) {
                String iBaseURL =
                    (String) this.documentAccessBridge.getProperty(E.serialize(profile), E
                        .serialize(profileIncludeClass), nb, "baseURL");
                if (iBaseURL == null || iBaseURL.length() == 0) {
                    iBaseURL = baseURL;
                }
                getPackageList(getProfileRef(iPkgName), iBaseURL, pkgUrls, dpkgUrls, groupIds);
                nb++;
            }
        } catch (Exception e) {
            throw new MacroExecutionException(e.getMessage(), e);
        }
    }
}
