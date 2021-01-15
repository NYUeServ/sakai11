package edu.nyu.classes.seats;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.cover.ToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;

import java.util.concurrent.atomic.AtomicBoolean;

import edu.nyu.classes.seats.api.SeatsService;
import org.sakaiproject.component.cover.ComponentManager;

import edu.nyu.classes.seats.brightspace.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;
import edu.nyu.classes.seats.handlers.*;

public class LTIServlet extends BaseServlet {

    private static final Logger LOG = LoggerFactory.getLogger(LTIServlet.class);
    private BrightspaceSeatingHandlerBackgroundThread backgroundThread = null;


    private static final String SESSION_COOKIE = "seatsSession";
    private static final String SESSION_COOKIE_FALLBACK = "seatsSession2";

    public void init(ServletConfig config) throws ServletException {
        String runBackgroundTask = HotReloadConfigurationService.getString("seats.run-background-task", null);

        if ("true".equals(runBackgroundTask)) {
            this.backgroundThread = new BrightspaceSeatingHandlerBackgroundThread().startThread(() -> brightspace());
        }

        super.init(config);
    }

    public void destroy() {
        super.destroy();
        this.backgroundThread.shutdown();
    }


    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            handleIt(request, response);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            e.printStackTrace();

            throw e;
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            handleIt(request, response);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            e.printStackTrace();

            throw e;
        }
    }

    private String getSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie c : request.getCookies()) {
            if (SESSION_COOKIE.equals(c.getName()) || SESSION_COOKIE_FALLBACK.equals(c.getName())) {
                return c.getValue();
            }
        }

        return null;
    }

    private BrightspaceClient brightspaceInstance;

    private synchronized BrightspaceClient brightspace() {
        if (brightspaceInstance == null) {
            brightspaceInstance = new BrightspaceClient();
        }

        return brightspaceInstance;
    }

    public void handleIt(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String bootstrapUuid = HotReloadConfigurationService.getString("seats.brightspace.bootstrap_uuid", null);
        if (bootstrapUuid != null) {
            String redirectUri = determineBaseURL() + bootstrapUuid + "/return";

            OAuth oauth = new OAuth();

            String pathInfo = String.valueOf(request.getPathInfo());

            if (pathInfo.endsWith("/" + bootstrapUuid)) {
                String redirect = oauth.buildAuthRedirectUrl(redirectUri);

                if (redirect == null) {
                    response.setContentType("text/plain");
                    response.getWriter().write("All refresh tokens ready\n");
                    return;
                }

                response.sendRedirect(redirect);
                return;
            } else if (pathInfo.endsWith("/" + bootstrapUuid + "/return")) {
                String clientId = request.getParameter("state");
                String authCode = request.getParameter("code");
                oauth.redeemAuthCode(clientId, authCode, redirectUri);

                // Go around to fill out others...
                response.sendRedirect(determineBaseURL() + bootstrapUuid);

                return;
            }
        }

        I18n i18n = new I18n(this.getClass().getClassLoader(), "edu.nyu.classes.seats.i18n.seats");

        URL toolBaseURL = determineBaseURL();
        Handlebars handlebars = loadHandlebars(toolBaseURL, i18n);

        String sessionId = getSessionId(request);

        long expireSessionMilliseconds = Long.valueOf(HotReloadConfigurationService.getString("seats.lti_session_expire_seconds", "2592000")) * 1000;

        if (Math.random() < 0.1) {
            LTISession.expireOldSessions(System.currentTimeMillis() - expireSessionMilliseconds);
        }


        sessionId = LTISession.setCurrentSession(sessionId);

        String domain = HotReloadConfigurationService.getString("seats.host_override", ServerConfigurationService.getServerName());

        // SameSite=None support is patchy right now in older Safari/IOS versions.  Take
        // a bet each way with multiple session cookies.
        response.addHeader("Set-Cookie",
                           String.format("%s=%s; Domain=%s; Path=%s; SameSite=None; Secure; HttpOnly",
                                         SESSION_COOKIE,
                                         sessionId,
                                         domain,
                                         "/"));

        response.addHeader("Set-Cookie",
                           String.format("%s=%s; Domain=%s; Path=%s; Secure; HttpOnly",
                                         SESSION_COOKIE_FALLBACK,
                                         sessionId,
                                         domain,
                                         "/"));

        try {
            Map<String, Object> context = new HashMap<String, Object>();

            context.put("iframe_mode", false);
            context.put("site_title", "");

            LTISession.populateContext(context);

            context.put("baseURL", toolBaseURL);
            context.put("layout", true);

            context.put("brightspace", brightspace());
            context.put("lms", new BrightspaceSeatsStorage(brightspace()));
            context.put("standaloneMode", true);
            context.put("portalCdnQuery", HotReloadConfigurationService.getString("portal.cdn.version", java.util.UUID.randomUUID().toString()));
            context.put("randomSakaiHeadStuff", "");

            if ("true".equals(HotReloadConfigurationService.getString("seats.brightspace_dev_mode", "false")) &&
                String.valueOf(request.getPathInfo()).endsWith("/brightspace-task")) {

                if (context.get("siteId") != null) {
                    SeatsService seats = (SeatsService) ComponentManager.get("edu.nyu.classes.seats.SeatsService");
                    seats.markSitesForSync(new String[] { (String)context.get("siteId") });
                }

                BrightspaceSeatGroupUpdatesTask.handleSeatGroupUpdates(0, brightspace());

                response.setContentType("text/plain");
                response.getWriter().write("You betcha\n");
                return;
            }

            Handler handler = handlerForRequest(request);

            if (handler.isSiteUpdRequired() && !(boolean)context.get("hasSiteUpd")) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (!handler.isLTISetupHandler() && !LTISession.isActive()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/plain");
                response.getWriter().write("There was a problem launching the Seating Tool.  Please close this window and retry.\n");
                return;
            }

            response.setHeader("Content-Type", handler.getContentType());
            response.setHeader("Cache-Control", "Cache-Control: no-cache, max-age=0");

            DB.transaction("Handle seats API request",
                           (DBConnection db) -> {
                               context.put("db", db);
                               try {
                                   handler.handle(request, response, context);
                                   db.commit();
                                   return null;
                               } catch (Exception e) {
                                   db.rollback();
                                   throw new RuntimeException(e);
                               }
                           });

            if (handler.hasRedirect()) {
                if (handler.getRedirect().startsWith("http")) {
                    response.sendRedirect(handler.getRedirect());
                } else {
                    response.sendRedirect(toolBaseURL + handler.getRedirect());
                }
            } else if (handler.hasTemplate()) {
                if (Boolean.TRUE.equals(context.get("layout"))) {
                    Template template = handlebars.compile("edu/nyu/classes/seats/views/layout");
                    response.getWriter().write(template.apply(context));
                } else {
                    Template template = handlebars.compile("edu/nyu/classes/seats/views/" + context.get("subpage"));
                    response.getWriter().write(template.apply(context));
                }
            }
        } catch (IOException e) {
            LOG.warn("Write failed", e);
        } catch (Exception e) {
            LOG.error("Error caught by LTIServlet: " + e);
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            boolean sessionIsVenerable = LTISession.activeSessionAge() > (expireSessionMilliseconds / 2);

            if (LTISession.isActiveSessionChanged() || sessionIsVenerable) {
                LTISession.writeSession(sessionId);
            } else {
                LTISession.clearActiveSession();
            }
        }
    }

    protected URL determineBaseURL() {
        try {
            String override = HotReloadConfigurationService.getString("seats.host_override", null);
            if (override != null) {
                return new URL(String.format("https://%s:%s/seats-tool/lti/", override, HotReloadConfigurationService.getString("seats.port_override", "443")));
            }

            return new URL(ServerConfigurationService.getServerUrl() + "/seats-tool/lti/");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't determine tool URL", e);
        }
    }
}
