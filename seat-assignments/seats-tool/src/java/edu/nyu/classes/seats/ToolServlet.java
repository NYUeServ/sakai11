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

import java.util.concurrent.atomic.AtomicBoolean;

import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;
import edu.nyu.classes.seats.handlers.*;

public class ToolServlet extends BaseServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ToolServlet.class);
    private SakaiSeatingHandlerBackgroundThread backgroundThread = null;

    private AtomicBoolean developmentMode = new AtomicBoolean(false);

    private Long logQueryThresholdMs = null;

    public void init(ServletConfig config) throws ServletException {
        if ("true".equals(HotReloadConfigurationService.getString("seats.development-mode", "false"))) {
            developmentMode.set(true);
        }

        if ("true".equals(HotReloadConfigurationService.getString("auto.ddl.seats", "false")) ||
            developmentMode.get()) {
            new SeatsStorage().runDBMigrations();
        }

        String thresholdFromConfig = HotReloadConfigurationService.getString("seats.log-query-threshold-ms", null);
        if (thresholdFromConfig != null) {
            try {
                logQueryThresholdMs = Long.valueOf(thresholdFromConfig);
            } catch (NumberFormatException e) {
                logQueryThresholdMs = null;
            }
        }

        String runBackgroundTask = HotReloadConfigurationService.getString("seats.run-background-task", null);

        if ("true".equals(runBackgroundTask) || (developmentMode.get() && runBackgroundTask == null)) {
            this.backgroundThread = new SakaiSeatingHandlerBackgroundThread().startThread();
            this.backgroundThread.setDBTimingThresholdMs(dbTimingThresholdMs());
        }

        super.init(config);
    }

    private long dbTimingThresholdMs() {
        if (logQueryThresholdMs != null) {
            return logQueryThresholdMs;
        } else {
            return developmentMode.get() ? 10 : 1000;
        }
    }

    public void destroy() {
        super.destroy();
        this.backgroundThread.shutdown();
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        I18n i18n = new I18n(this.getClass().getClassLoader(), "edu.nyu.classes.seats.i18n.seats");

        URL toolBaseURL = determineBaseURL();
        Handlebars handlebars = loadHandlebars(toolBaseURL, i18n);

        try {
            Map<String, Object> context = new HashMap<String, Object>();

            context.put("iframe_mode", false);
            context.put("site_title", "");

            context.put("lms", new SakaiSeatsStorage());

            context.put("baseURL", toolBaseURL);
            context.put("layout", true);
            context.put("skinRepo", HotReloadConfigurationService.getString("skin.repo", ""));
            context.put("randomSakaiHeadStuff", request.getAttribute("sakai.html.head"));

            if (ToolManager.getCurrentPlacement() != null) {
                context.put("siteId", ToolManager.getCurrentPlacement().getContext());
            }

            context.put("developmentMode", developmentMode.get());

            context.put("hasSiteUpd", hasSiteUpd((String)context.get("siteId")));

            context.put("portalCdnQuery",
                        developmentMode.get() ?
                        java.util.UUID.randomUUID().toString() :
                        HotReloadConfigurationService.getString("portal.cdn.version", java.util.UUID.randomUUID().toString()));

            if ("true".equals(HotReloadConfigurationService.getString("seats.enable-fiddler", "false"))) {
                context.put("rosterFiddlerEnabled", "true");
            }

            Handler handler = handlerForRequest(request);

            if (handler.isSiteUpdRequired() && !(boolean)context.get("hasSiteUpd")) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            } else if (!hasSiteVisit((String)context.get("siteId"))) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setHeader("Content-Type", handler.getContentType());
            response.setHeader("Cache-Control", "Cache-Control: no-cache, max-age=0");

            DB.transaction("Handle seats API request",
                           (DBConnection db) -> {
                               db.setTimingEnabled(dbTimingThresholdMs());

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
            LOG.error("Error caught by ToolServlet: " + e);
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    protected URL determineBaseURL() {
        try {
            return new URL(ServerConfigurationService.getPortalUrl() + getBaseURI() + "/");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't determine tool URL", e);
        }
    }

    private String getBaseURI() {
        String result = "";

        String siteId = null;
        String toolId = null;

        if (ToolManager.getCurrentPlacement() != null) {
            siteId = ToolManager.getCurrentPlacement().getContext();
            toolId = ToolManager.getCurrentPlacement().getId();
        }

        if (siteId != null) {
            result += "/site/" + siteId;
            if (toolId != null) {
                result += "/tool/" + toolId;
            }
        }

        return result;
    }

    private boolean hasSiteUpd(String siteId) {
        return SecurityService.unlock("site.upd", "/site/" + siteId);
    }

    private boolean hasSiteVisit(String siteId) {
        return SecurityService.unlock("site.visit", "/site/" + siteId);
    }
}
