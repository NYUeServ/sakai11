// LTI note: This isn't used in the LTI version, so remains Sakai-specific.

package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.models.Meeting;
import edu.nyu.classes.seats.models.SeatAssignment;
import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.db.DBConnection;
import edu.nyu.classes.seats.storage.Locks;
import edu.nyu.classes.seats.storage.SeatsStorage;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.site.cover.SiteService;

import edu.nyu.classes.seats.models.Member;
import edu.nyu.classes.seats.models.SeatGroup;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.sakaiproject.site.api.Site;

public class GroupRemoveMembersHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");
        String siteId = (String)context.get("siteId");

        RequestParams p = new RequestParams(request);
        String groupId = p.getString("groupId", null);
        String sectionId = p.getString("sectionId", null);
        String netid = p.getString("netid", null);

        if (groupId == null || sectionId == null || netid == null) {
            return;
        }

        Site site = SiteService.getSite(siteId);
        Optional<SeatSection> seatSection = SeatsStorage.getSeatSection(db, sectionId, siteId);

        if (!seatSection.isPresent()) {
            return;
        }

        Optional<org.sakaiproject.authz.api.Member> member = site
            .getMembers()
            .stream()
            .filter((m) -> m.getUserEid().equals(netid))
            .findFirst();

        // No removing student users if they're in a roster
        boolean isStudent = Member.Role.STUDENT.toString().equals(member.get().getRole().toString().toUpperCase(Locale.ROOT));
        if (!member.isPresent() || (member.get().isProvided() && isStudent)) {
            return;
        }

        SeatsStorage.removeMemberFromGroup(db, seatSection.get(), groupId, netid);

        response.getWriter().write("{}");
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


