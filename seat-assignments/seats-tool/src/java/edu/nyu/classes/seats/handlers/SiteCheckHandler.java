package edu.nyu.classes.seats.handlers;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;

import edu.nyu.classes.seats.BrightspaceSeatGroupUpdatesTask;

public class SiteCheckHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        String siteId = request.getParameter("site_id");
        response.getWriter().write(String.format("{\"ready\": %s}", BrightspaceSeatGroupUpdatesTask.hasSyncedLately(siteId)));
    }

    @Override
    public String getContentType() {
        return "text/json";
    }

    @Override
    public boolean hasTemplate() {
        return false;
    }

    @Override
    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    public Errors getErrors() {
        return null;
    }

    public boolean hasRedirect() {
        return false;
    }

    public String getRedirect() {
        return "";
    }

    public boolean isLTISetupHandler() {
        return true;
    }

    @Override
    public boolean isSiteUpdRequired() {
        return false;
    }
}


