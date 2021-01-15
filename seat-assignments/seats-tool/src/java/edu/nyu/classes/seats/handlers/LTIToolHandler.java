package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.storage.db.DBConnection;
import edu.nyu.classes.seats.storage.LTISession;
import edu.nyu.classes.seats.BrightspaceSeatGroupUpdatesTask;
import edu.nyu.classes.seats.brightspace.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;

import java.io.*;
import java.util.*;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Future;


public class LTIToolHandler implements Handler {

    private static final Logger LOG = LoggerFactory.getLogger(LTIToolHandler.class);

    protected String redirectTo = null;

    private static AtomicReference<JwksStore> jwksStore = new AtomicReference<>();

    private static JwksStore jwks() {
        if (jwksStore.get() == null) {
            try {
                jwksStore.set(new JwksStore(new URL(HotReloadConfigurationService.getString("seats.brightspace_jwks_url", "https://brightspace.nyu.edu/d2l/.well-known/jwks"))));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return jwksStore.get();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");

        String idToken = request.getParameter("id_token");
        String[] bits = idToken.split("\\.");

        if (bits.length != 3) {
            throw new RuntimeException("id_token invalid");
        }

        byte[] headerBytes = Base64.getUrlDecoder().decode(bits[0]);
        byte[] bodyBytes = Base64.getUrlDecoder().decode(bits[1]);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(bits[2]);

        JSON jwtHeader = JSON.parse(headerBytes);

        if (!"RS256".equals(jwtHeader.path("alg").asString(""))) {
            throw new RuntimeException("JWT algorithm not supported");
        }

        String kid = jwtHeader.path("kid").asStringOrDie();

        if (!jwks().isSignatureOK(kid, (bits[0] + "." + bits[1]).getBytes("UTF-8"), signatureBytes)) {
            throw new RuntimeException("Signature could be verified");
        }

        JSON body = JSON.parse(bodyBytes);

        if (System.currentTimeMillis() / 1000 < body.path("nbf").asLong(0L)) {
            throw new RuntimeException("Token is not yet valid");
        }

        if (body.path("exp").asLongOrDie() < System.currentTimeMillis() / 1000) {
            throw new RuntimeException("Token has expired");
        }

        boolean isInstructor = false;

        for(String role : body.path("https://purl.imsglobal.org/spec/lti/claim/roles").asStringList()) {
            if (role.endsWith("#Instructor") || role.endsWith("#Administrator")) {
                isInstructor = true;
                break;
            }
        }

        LTISession.put("iframe_mode", ("seats_running_iframe".equals(request.getParameter("state"))));

        LTISession.put("hasSiteUpd", isInstructor);
        LTISession.put("user_familyName", body.path("family_name").asString(null));
        LTISession.put("user_givenName", body.path("given_name").asString(null));
        LTISession.put("user_name", body.path("name").asString(null));
        LTISession.put("user_email", body.path("email").asString(null));

        JSON userContext = body.path("https://purl.imsglobal.org/spec/lti/claim/context");

        String siteId = "brightspace:" + userContext.path("id").asStringOrDie();
        LTISession.put("siteId", siteId);
        LTISession.put("site_title", userContext.path("title").asString(null));

        JSON roster = body.path("https://purl.imsglobal.org/spec/lti/claim/lis");
        LTISession.put("user_roster_id", extractRosterId(roster.path("course_section_sourcedid").asString(null)));

        JSON user = body.path("http://www.brightspace.com");
        LTISession.put("user_n_number", user.path("org_defined_id").asString(null));
        LTISession.put("user_id", String.valueOf(user.path("user_id").asLong(null)));
        LTISession.put("netid", user.path("username").asStringOrDie());

        LTISession.put("session_active", "true");

        // Redirect to Home
        redirectTo = "";

        Future<Boolean> syncResult = BrightspaceSeatGroupUpdatesTask.syncImmediate((BrightspaceClient)context.get("brightspace"), siteId);
        try {
            syncResult.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // If we're not quite ready, show a loading page
            context.put("subpage", "loading_spinner");
            context.put("siteId", siteId);
            redirectTo = null;
        } catch (Exception e) {
            LOG.error("Exception waiting for immediate sync", e);
        }
    }

    private String extractRosterId(String sourcedid) {
        return (sourcedid != null) ? sourcedid.split(":")[1] : null;
    }

    @Override
    public String getContentType() {
        return "text/html";
    }

    @Override
    public boolean hasTemplate() {
        return redirectTo == null;
    }

    @Override
    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    public Errors getErrors() {
        return null;
    }

    public boolean hasRedirect() {
        return redirectTo != null;
    }

    public String getRedirect() {
        return redirectTo;
    }

    public boolean isLTISetupHandler() {
        return true;
    }

    public boolean isSiteUpdRequired() {
        return false;
    }

}


