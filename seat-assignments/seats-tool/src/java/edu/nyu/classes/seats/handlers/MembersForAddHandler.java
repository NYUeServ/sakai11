// LTI note: This isn't used in the LTI version, so remains Sakai-specific.

package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.models.Meeting;
import edu.nyu.classes.seats.models.SeatAssignment;
import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.db.DBConnection;
import edu.nyu.classes.seats.storage.Locks;
import edu.nyu.classes.seats.storage.SakaiSeatsStorage;
import edu.nyu.classes.seats.storage.SeatsStorage;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.site.cover.SiteService;

import edu.nyu.classes.seats.models.SeatGroup;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.sakaiproject.site.api.Site;

public class MembersForAddHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");
        String siteId = (String)context.get("siteId");

        RequestParams p = new RequestParams(request);
        String groupId = p.getString("groupId", null);
        String sectionId = p.getString("sectionId", null);

        Optional<SeatSection> seatSection = SeatsStorage.getSeatSection(db, sectionId, siteId);
        Optional<SeatGroup> seatGroup = seatSection.flatMap((sec) -> sec.fetchGroup(groupId));

        if (!seatSection.isPresent() || !seatGroup.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Set<String> allAddedUsers = seatSection.get().listGroups().stream()
            .flatMap((g) -> g.listMembers().stream().map(m -> m.netid))
            .collect(Collectors.toSet());

        Set<String> existingGroupMembers = seatGroup.get().listMembers().stream().map(m -> m.netid).collect(Collectors.toSet());

        Site site = SiteService.getSite(siteId);

        List<org.sakaiproject.authz.api.Member> candidateMembers = site
            .getMembers()
            .stream()
            .filter((m) -> {
                    if (existingGroupMembers.contains(m.getUserEid())) {
                        return false;
                    } else if (allAddedUsers.contains(m.getUserEid()) && "Student".equals(m.getRole().getId())) {
                        return false;
                    } else if (!m.isActive()) {
                        return false;
                    } else if (m.isProvided() && "Student".equals(m.getRole().getId())) {
                        return false;
                    }

                    return true;
                })
            .collect(Collectors.toList());

        Map<String, SeatsStorage.UserDisplayName> memberNames = SakaiSeatsStorage.getMemberNames(candidateMembers.stream().map(m -> m.getUserEid()).collect(Collectors.toList()));

        JSONArray result = new JSONArray();

        for (org.sakaiproject.authz.api.Member m : candidateMembers) {
            String netid = m.getUserEid();

            JSONObject user = new JSONObject();
            user.put("netid", netid);
            SeatsStorage.UserDisplayName memberName = memberNames.get(netid);
            if (memberName == null) {
                user.put("displayName", netid);
            } else {
                user.put("displayName", memberName.displayName);
            }
            user.put("role", m.getRole().getId());

            result.add(user);
        }

        try {
            response.getWriter().write(result.toString());
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


