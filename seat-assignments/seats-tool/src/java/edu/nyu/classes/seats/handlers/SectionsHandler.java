package edu.nyu.classes.seats.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Comparator;
import java.text.DateFormat;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.stream.Collectors;
import java.util.Collections;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.net.URL;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import edu.nyu.classes.seats.api.SeatsService;

// LTI note: sneaky Sakai dependency here.  If we split this from Sakai we'll
// have to incorporate SeatsService and just call it directly.  That'll be nice!
import org.sakaiproject.component.cover.ComponentManager;

public class SectionsHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        String siteId = (String)context.get("siteId");
        DBConnection db = (DBConnection)context.get("db");

        JSONArray sections = new JSONArray();

        for (SeatSection section : SeatsStorage.siteSeatSections(db, siteId)) {
            JSONObject obj = new JSONObject();
            obj.put("id", section.id);
            obj.put("name", section.name);
            obj.put("shortName", section.shortName);
            obj.put("groupCount", section.listGroups().size());

            sections.add(obj);
        }

        if (sections.isEmpty()) {
            // Last ditch effort to populate this site
            SeatsService seats = (SeatsService) ComponentManager.get("edu.nyu.classes.seats.SeatsService");
            seats.markSitesForSync(siteId);
        }

        try {
            response.getWriter().write(sections.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    @Override
    public boolean isSiteUpdRequired() {
        return false;
    }
}


