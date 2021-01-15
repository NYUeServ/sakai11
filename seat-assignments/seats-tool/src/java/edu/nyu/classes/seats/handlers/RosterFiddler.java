// LTI note: This isn't used in the LTI version, so remains Sakai-specific.

package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.models.Meeting;
import edu.nyu.classes.seats.models.SeatAssignment;
import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.db.DBConnection;
import edu.nyu.classes.seats.storage.Locks;
import edu.nyu.classes.seats.storage.SeatsStorage;
import org.json.simple.JSONObject;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.nyu.classes.seats.api.SeatsService;

public class RosterFiddler implements Handler {

    protected String redirectTo = null;

    private static List<String> VALID_NETIDS = Arrays.asList(new String[] {
            "pg1025", "mt1970", "kjb355", "jwp292",
            "tst126", "tst130", "tst131", "tst132", "tst133", "tst134", "tst135", "tst136", "tst137", "tst138", "tst139", "tst141", "tst142", "tst143",
            "tst144", "tst145", "tst146", "tst148", "tst149", "tst150", "tst151", "tst152", "tst157", "tst158", "tst161", "tst163", "tst2", "tst223", "tst224",
            "tst227", "tst234", "tst236", "tst237", "tst238", "tst239", "tst240", "tst241", "tst243", "tst244", "tst249", "tst25", "tst250", "tst252", "tst258",
            "tst259", "tst261", "tst262", "tst263", "tst264", "tst267", "tst268", "tst269", "tst270", "tst271", "tst281", "tst282", "tst283", "tst284", "tst285",
            "tst286", "tst287", "tst288", "tst289", "tst294", "tst3", "tst31", "tst310", "tst318", "tst328", "tst329", "tst330", "tst331", "tst332", "tst333",
            "tst334", "tst335", "tst336", "tst337", "tst338", "tst341", "tst351", "tst352", "tst353", "tst354"
        });

    private static List<String> VALID_SECTIONS = Arrays.asList(new String[] {
            "section_a65da58722e798acd65b742445ccef39",
            "FA12_DESL1-UC_9615_S_001",
            "FA12_DESL1-UC_9615_S_002",
            "FA12_DESL1-UC_9784_S_A",
            "FA12_DESL1-UC_9784_S_AA",
            "FA12_DESL1-UC_9785_S_AA",
            "FA12_DESL1-UC_9894_S_A",
            "FA12_DESL1-UC_9894_S_AA",
            "FA12_DEVE1-GC_2300_1_001",
            "FA12_DEVE1-GC_2315_1_001",
            "FA12_DEVE1-GC_3030_1_001",
            "FA12_DEVE1-GC_3050_1_001",
            "FA12_DEVE1-GC_3055_1_001",
            "FA12_DFLM1-CE_9424_S_001",
            "FA12_DGCM1-UC_2241_1_001",
            "FA12_DGSCI-CD_7006_S_500",
            "FA12_DGSCI-CD_7008_S_500",
            "FA12_DGSCI-CD_7009_S_500",
            "FA12_DGSCI-CD_7010_S_500",
            "FA12_DGSCI-CD_7012_S_500",
            "FA12_DGSCI-CD_7013_S_500",
            "FA12_DGSCI-CD_7025_S_500",
            "FA12_DGSCI-CD_7027_S_500",
            "FA12_DGSCI-CD_7029_S_500",
            "FA12_DGSCI-CD_7037_S_500",
            "FA12_DGSCI-CD_7038_S_500",
            "FA12_DGSCI-CD_7040_S_500",
            "FA12_DGSCI-CD_7041_S_500",
            "FA12_DGSCI-CD_7044_S_500",
            "FA12_DGSCI-CD_7045_S_500",
            "FA12_DGSCI-CD_8038_S_500",
            "FA12_DGSCI-CD_8061_S_500",
        });

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        String siteId = (String)context.get("siteId");
        DBConnection db = (DBConnection)context.get("db");

        RequestParams p = new RequestParams(request);

        String rosterId = p.getString("rosterId", null);
        String netid = p.getString("netid", null);

        if (rosterId == null || netid == null) {
            context.put("validFiddleNetIDs", VALID_NETIDS);
            context.put("validFiddleSections", VALID_SECTIONS);

            context.put("subpage", "roster_fiddler");
            return;
        }

        if (!VALID_NETIDS.contains(netid)) {
            throw new RuntimeException("No dice");
        }

        if (!VALID_SECTIONS.contains(rosterId)) {
            throw new RuntimeException("No dice");
        }

        String remove = p.getString("remove", "");

        CourseManagementAdministration cmAdmin = (CourseManagementAdministration)ComponentManager.get("org.sakaiproject.coursemanagement.api.CourseManagementAdministration");

        if ("remove".equals(remove)) {
            cmAdmin.removeSectionMembership(netid, rosterId);
        } else {
            cmAdmin.addOrUpdateSectionMembership(netid, "S", rosterId, "active");
        }

        SeatsService seats = (SeatsService) ComponentManager.get("edu.nyu.classes.seats.SeatsService");
        seats.markSitesForSync(siteId);

        redirectTo = ((java.net.URL)context.get("baseURL")).toString();
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
        return redirectTo != null;
    }

    public String getRedirect() {
        return redirectTo;
    }
}


