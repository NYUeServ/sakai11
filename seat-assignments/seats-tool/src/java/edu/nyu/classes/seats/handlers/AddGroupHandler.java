package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.Locks;
import edu.nyu.classes.seats.storage.SeatsStorage;
import edu.nyu.classes.seats.storage.LMSConnection;
import edu.nyu.classes.seats.storage.db.DBConnection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AddGroupHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");
        String siteId = (String)context.get("siteId");

        LMSConnection lms = (LMSConnection)context.get("lms");

        RequestParams p = new RequestParams(request);
        String sectionId = p.getString("sectionId", null);

        Locks.lockSiteForUpdate(siteId);
        try {
            SeatSection seatSection = SeatsStorage.getSeatSection(db, sectionId, siteId).get();

            Integer maxGroups = lms.getGroupMaxForSite(siteId);

            if (seatSection.listGroups().size() < maxGroups) {
                for (int i = 0; ; i++) {
                    String groupName = String.format("%c", 65 + i);
                    if (seatSection.listGroups().stream().noneMatch(g -> groupName.equals(g.name))) {
                        SeatsStorage.createGroup(db, seatSection, groupName, Collections.emptyList());
                        SeatsStorage.markSectionAsSplit(db, seatSection);
                        break;
                    }
                }
            }
        } finally {
            Locks.unlockSiteForUpdate(siteId);
        }

        try {
            response.getWriter().write("{}");
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
}


