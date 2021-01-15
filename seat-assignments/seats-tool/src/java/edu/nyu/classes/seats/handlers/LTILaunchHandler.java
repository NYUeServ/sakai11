package edu.nyu.classes.seats.handlers;

import org.apache.http.client.utils.URIBuilder;
import org.sakaiproject.component.cover.HotReloadConfigurationService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LTILaunchHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        // FIXME: prod default
        String oauthClientId = HotReloadConfigurationService.getString("seats.lti.client_id", null);

        if (oauthClientId == null) {
            throw new RuntimeException("seats.lti.client_id property was not set");
        }

        URIBuilder ub = new URIBuilder(HotReloadConfigurationService.getString("seats.lti.auth_endpoint", "https://nyutest.brightspace.com/d2l/lti/authenticate"));
        ub.addParameter("client_id", oauthClientId);
        ub.addParameter("response_type", "id_token");
        ub.addParameter("redirect_uri", request.getParameter("target_link_uri"));
        ub.addParameter("response_mode", "form_post");
        ub.addParameter("scope", "openid");
        ub.addParameter("state", "seats_running_standalone");
        ub.addParameter("login_hint", request.getParameter("login_hint"));
        ub.addParameter("nonce", UUID.randomUUID().toString());
        ub.addParameter("prompt", "none");
        ub.addParameter("lti_message_hint", request.getParameter("lti_message_hint"));

        context.put("lti_redirect_url", ub.toString());
        context.put("subpage", "cookie_check");
    }

    @Override
    public String getContentType() {
        return "text/html";
    }

    @Override
    public boolean hasTemplate() {
        return true;
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
        return redirectTo;
    }

    public boolean isLTISetupHandler() {
        return true;
    }

    public boolean isSiteUpdRequired() {
        return false;
    }
}


