package edu.nyu.classes.nyuhome.servlet;

import lombok.*;

import java.io.IOException;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.site.api.SiteService;

import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.api.Session;

import org.sakaiproject.user.api.UserNotDefinedException;

import edu.nyu.classes.nyuhome.api.DataFeed;
import edu.nyu.classes.nyuhome.api.DataFeedEntry;
import edu.nyu.classes.nyuhome.api.QueryUser;
import edu.nyu.classes.nyuhome.api.Resolver;

import edu.nyu.classes.nyuhome.servlet.SakaiQueryUser;
import edu.nyu.classes.nyuhome.servlet.SakaiResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.codehaus.jackson.map.ObjectMapper;


public class NYUHomeDataFeedServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(NYUHomeDataFeedServlet.class);

    private UserDirectoryService userDirectoryService;
    private SiteService siteService;


    @Data private class MissingParamException extends Exception {
        private final String param;
    }


    private class AccessDeniedException extends Exception {
    }


    private String dataFeedClassesConfig = "edu.nyu.classes.nyuhome.feeds.AnnouncementFeed,edu.nyu.classes.nyuhome.feeds.AssignmentFeed";
    private String allowedIPPatternConfig = "127\\.0\\.0\\.1";


    private List<Class> dataFeedClasses;

    public NYUHomeDataFeedServlet() {
        dataFeedClassesConfig = ServerConfigurationService.getString("nyuhome.data.feed.classes", dataFeedClassesConfig);

        userDirectoryService = (UserDirectoryService) ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService");
        siteService = (SiteService) ComponentManager.get("org.sakaiproject.site.api.SiteService");

        LOG.info("Initialized with classes: {}", dataFeedClassesConfig);

        List<Class> dfc = new ArrayList<Class>();

        for (String classString : dataFeedClassesConfig.split(", *")) {
            try {
                dfc.add(Class.forName(classString));
            } catch (ClassNotFoundException e) {
                LOG.error("Couldn't find data feed provider for: " + classString, e);
            }
        }

        dataFeedClasses = dfc;
    }


    private List<DataFeedEntry> getDataForFeed(Class dataFeedClass, QueryUser user, Resolver resolver, int maxAgeDays, int maxResults) {
        try {
            DataFeed instance = (DataFeed)dataFeedClass.newInstance();
            return instance.getUserData(user, resolver, maxAgeDays, maxResults);
        } catch (IllegalAccessException ex) {
            LOG.debug("Failed to get data for feed", ex);
            return new ArrayList<DataFeedEntry>();
        } catch (InstantiationException ex) {
            LOG.debug("Failed to get data for feed", ex);
            return new ArrayList<DataFeedEntry>();
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Session session = null;
        String netId = null;

        try {
            checkAccessPermission(request);
            netId = getParamOrFail("netid", request);

            int maxAgeDays = getIntParamOrFail("maxAgeDays", request);
            int maxResults = getIntParamOrFail("maxResults", request);

            QueryUser user = new SakaiQueryUser(netId);

            session = SessionManager.startSession();

            session.setActive();
            session.setUserId(user.getId());
            session.setUserEid(netId);
            SessionManager.setCurrentSession(session);

            Map<String, Object> result = new HashMap<String, Object>();
            Resolver resolver = new SakaiResolver(userDirectoryService, siteService);

            for (Class dataFeed : dataFeedClasses) {
                result.put(dataFeed.getName(), getDataForFeed(dataFeed, user, resolver, maxAgeDays, maxResults));
            }

            result.put("_dictionary", resolver.toMap());

            ObjectMapper mapper = new ObjectMapper();

            response.setContentType("text/json");
            response.setStatus(200);
            mapper.writeValue(response.getOutputStream(), result);

        } catch (UserNotDefinedException ex) {
            LOG.info("Request for unknown user: " + netId, ex);

        } catch (MissingParamException ex) {
            LOG.error("Missing parameter", ex);
            response.setContentType("text/plain");
            response.setStatus(400);
            String msg = "Missing value for parameter: " + ex.param;
            response.getOutputStream().write(msg.getBytes("UTF-8"));

        } catch (AccessDeniedException ex) {
            // LOG.error("Access denied", ex);
            response.setContentType("text/plain");
            response.setStatus(403);
            String msg = "Access denied";
            response.getOutputStream().write(msg.getBytes("UTF-8"));

        } finally {
            if (session != null) {
                SessionManager.setCurrentSession(null);
                session.invalidate();
            }
        }
    }


    private String getParamOrFail(String param, HttpServletRequest request)
        throws MissingParamException {
        String result = request.getParameter(param);

        if (result == null) {
            throw new MissingParamException(param);
        }

        return result;
    }


    private int getIntParamOrFail(String param, HttpServletRequest request)
        throws MissingParamException {
        String paramValue = getParamOrFail(param, request);

        try {
            return Integer.valueOf(paramValue);
        } catch (NumberFormatException ex) {
            throw new MissingParamException(param);
        }
    }


    private void checkAccessPermission(HttpServletRequest req)
        throws AccessDeniedException {
        String remoteAddr = req.getRemoteAddr();

        String pattern = HotReloadConfigurationService.getString("nyuhome.allowed.ips", allowedIPPatternConfig);

        if (!remoteAddr.matches(pattern)) {
            throw new AccessDeniedException();
        }
    }
}
