package edu.nyu.classes.seats.handlers;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;

public class CookieCheckHandler implements Handler {

    protected String redirectTo = null;

    private static final String TEST_COOKIE = "ct56a8de61-d3e3-485d-9086-1f3a0c4fbda0";
    private static final String TEST_COOKIE_FALLBACK = "ctfb56a8de61-d3e3-485d-9086-1f3a0c4fbda0";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        String action = "set".equals(request.getParameter("mode")) ? "set" : "check";

        if ("set".equals(action)) {
            // SameSite=None support is patchy right now in older Safari/IOS versions.  Take
            // a bet each way with multiple session cookies.
            response.addHeader("Set-Cookie",
                               String.format("%s=%s; Path=%s; SameSite=None; Secure; HttpOnly",
                                             TEST_COOKIE,
                                             TEST_COOKIE,
                                             "/"));

            response.addHeader("Set-Cookie",
                               String.format("%s=%s;Path=%s; Secure; HttpOnly",
                                             TEST_COOKIE_FALLBACK,
                                             TEST_COOKIE_FALLBACK,
                                             "/"));


            response.getWriter().write(String.format("{\"cookie_set\": \"ok\"}"));
        } else if ("check".equals(action)) {
            boolean found = false;

            if (request.getCookies() != null) {
                for (Cookie c : request.getCookies()) {
                    if (TEST_COOKIE.equals(c.getName()) || TEST_COOKIE_FALLBACK.equals(c.getName())) {
                        found = true;
                        break;
                    }
                }
            }

            response.getWriter().write(String.format("{\"cookie_found\": %s}", found ? true : false));
        } else {
            response.getWriter().write("{\"error\": \"action unrecognized\"}");
        }
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


