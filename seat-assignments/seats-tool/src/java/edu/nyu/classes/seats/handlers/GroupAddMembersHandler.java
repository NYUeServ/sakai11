// LTI note: This handler isn't available in LTI mode, so remains Sakai-specific.

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

public class GroupAddMembersHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");
        String siteId = (String)context.get("siteId");

        RequestParams p = new RequestParams(request);
        String groupId = p.getString("groupId", null);
        String sectionId = p.getString("sectionId", null);

        List<String> netIdsForAdd = p.getStrings("selectedMembers[]");

        Site site = SiteService.getSite(siteId);

        Map<String, org.sakaiproject.authz.api.Member> siteMembers = site
            .getMembers()
            .stream()
            .collect(Collectors.toMap(org.sakaiproject.authz.api.Member::getUserEid, (m) -> m));

        List<Member> addme = netIdsForAdd.stream().map((netid) -> {
                org.sakaiproject.authz.api.Member siteMembership = siteMembers.get(netid);

                Member.Role memberRole = Member.Role.STUDENT;

                if (siteMembership != null) {
                    try {
                        String siteRole = siteMembership.getRole().getId().toUpperCase(Locale.ROOT);
                        memberRole = Member.Role.valueOf(siteRole.replace(" ", "_"));
                    } catch (IllegalArgumentException e) {
                        // Fall through to student
                    }
                }

                return new Member(netid, false, memberRole, Member.StudentLocation.IN_PERSON);
            }).collect(Collectors.toList());

        for (Member m : addme) {
            SeatsStorage.addMemberToGroup(db, m, groupId, sectionId);
        }

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


