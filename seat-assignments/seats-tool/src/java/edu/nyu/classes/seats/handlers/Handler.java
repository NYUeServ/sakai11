package edu.nyu.classes.seats.handlers;

import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The interface implemented by all handlers.
 */
public interface Handler {
    /**
     * Handle a request and either redirect the user or set the request context
     * to display an appropriate view.
     *
     * If a string entry called "subpage" is added to the context, this will be
     * resolved to a handlebars template and rendered.
     */
    void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception;

    /**
     * True if the handler has returned a redirect.
     */
    boolean hasRedirect();

    String getRedirect();

    /**
     * Return any validation errors produced by this request.
     */
    Errors getErrors();

    /**
     * Return any flash messages (from the previous request) that should be displayed.
     */
    Map<String, List<String>> getFlashMessages();

    interface Errors {}

    default String getContentType() {
        return "text/html";
    }

    default boolean hasTemplate() {
        return true;
    }

    default boolean isSiteUpdRequired() {
        return true;
    }

    default boolean isLTISetupHandler() {
        return false;
    }
}
