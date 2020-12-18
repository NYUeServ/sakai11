package org.sakaiproject.portal.charon.handlers;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.cover.UserDirectoryService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.*;
import java.util.*;

public class BrightspaceMigratorHandler extends BasePortalHandler {

    private static final String URL_FRAGMENT = "brightspace-migrator";
    private static final String SESSION_KEY_ALLOWED_TO_MIGRATE = "NYU_ALLOWED_TO_MIGRATE";

    private static final String SESSION_KEY_NETID_LAST_CHECK_TIME = "SESSION_KEY_NETID_LAST_CHECK_TIME";
    private static final String SESSION_KEY_NETID_LAST_VALUE = "SESSION_KEY_NETID_LAST_VALUE";
    private static final String SESSION_KEY_RULE_LAST_CHECK_TIME = "SESSION_KEY_RULE_LAST_CHECK_TIME";
    private static final String SESSION_KEY_RULE_LAST_VALUE = "SESSION_KEY_RULE_LAST_VALUE";

    private static final int QUERY_BATCH_SIZE = 1000;

    private static final int PAGE_SIZE = 50;

    private SqlService sqlService;
    private static Log M_log = LogFactory.getLog(BrightspaceMigratorHandler.class);

    private AuthzGroupService authzGroupService = (AuthzGroupService)ComponentManager.get("org.sakaiproject.authz.api.AuthzGroupService");

    private static long NETID_RECHECK_INTERVAL_MS = 60000;
    private static long RULE_RECHECK_INTERVAL_MS = 3600000;

    public BrightspaceMigratorHandler()
    {
        setUrlFragment(BrightspaceMigratorHandler.URL_FRAGMENT);
        if(sqlService == null) {
            sqlService = (SqlService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService");
        }
    }

    @Override
    public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res,
                     Session session) throws PortalHandlerException
    {
        if ((parts.length >= 2) && (parts[1].equals(BrightspaceMigratorHandler.URL_FRAGMENT)))
        {
            try
            {
                if (!isAllowedToMigrateSitesToBrightspace()) {
                    return NEXT;
                }

                JSONObject obj = new JSONObject();

                JSONArray sitesJSON = new JSONArray();

                String termFilter = StringUtils.trimToNull(req.getParameter("term"));
                String queryFilter = StringUtils.trimToNull(req.getParameter("q"));
                String pageStr = StringUtils.trimToNull(req.getParameter("page"));

                int page = 0;
                if (pageStr != null) {
                    page = Integer.valueOf(pageStr);
                }

                List<SiteToArchive> allSites = instructorSites(termFilter, queryFilter);
                int minCurrentPageIndex = Math.max(page * PAGE_SIZE, 0);
                int maxCurrentPageIndex = Math.min(allSites.size(), (page + 1) *  PAGE_SIZE);

            
                List<SiteToArchive> currentPage = Collections.emptyList();
                if (minCurrentPageIndex <= maxCurrentPageIndex) {
                    currentPage = allSites.subList(minCurrentPageIndex, maxCurrentPageIndex);
                } else {
                    page = 0;
                }

                boolean hasPrevious = page > 0;
                boolean hasNext = allSites.size() > maxCurrentPageIndex;

                obj.put("page", page);
                obj.put("has_previous_page", hasPrevious);
                obj.put("has_next_page", hasNext);

                for (SiteToArchive site : currentPage) {
                    JSONObject siteJSON = new JSONObject();
                    siteJSON.put("site_id", site.siteId);
                    siteJSON.put("title", site.title);
                    siteJSON.put("term", site.term);

                    JSONArray rostersJSON = new JSONArray();
                    for (String roster : site.rosters) {
                        rostersJSON.add(roster);
                    }
                    siteJSON.put("rosters", rostersJSON);

                    JSONArray instructorsJSON = new JSONArray();
                    for (Map.Entry<String, String> instructor : site.instructors.entrySet()) {
                        JSONObject instructorJSON = new JSONObject();
                        instructorJSON.put("netid", instructor.getKey());
                        instructorJSON.put("display", instructor.getValue());
                        instructorsJSON.add(instructorJSON);
                    }
                    siteJSON.put("instructors", instructorsJSON);

                    boolean queued = false;
                    for (SiteArchiveRequest request : site.requests) {
                        siteJSON.put("queued_by", request.queuedBy);
                        siteJSON.put("queued_at", request.queuedAt);
                        siteJSON.put("archived_at", request.archivedAt);
                        siteJSON.put("uploaded_at", request.uploadedAt);
                        siteJSON.put("completed_at", request.completedAt);
                        siteJSON.put("status", request.getStatus());
                        siteJSON.put("brightspace_org_unit_id", request.brightspaceOrgUnitId);
                        queued = true;
                    }
                    siteJSON.put("queued", queued);
                    sitesJSON.add(siteJSON);
                }

                obj.put("sites", sitesJSON);

                JSONArray termsJSON = new JSONArray();
                for (String term : instructorTerms()) {
                    termsJSON.add(term);
                }
                obj.put("terms", termsJSON);

                res.getWriter().write(obj.toString());

                return END;
            }
            catch (Exception ex)
            {
                throw new PortalHandlerException(ex);
            }
        }
        else
        {
            return NEXT;
        }
    }

    @Override
    public int doPost(String[] parts, HttpServletRequest req,
                      HttpServletResponse res, Session session)
            throws PortalHandlerException {
        if ((parts.length >= 2) && (parts[1].equals(BrightspaceMigratorHandler.URL_FRAGMENT)))
        {
            try {
                if (!isAllowedToMigrateSitesToBrightspace()) {
                    return NEXT;
                }

                String siteId = StringUtils.trimToNull(req.getParameter("site_id"));
                if (siteId != null && SecurityService.unlock("site.upd", "/site/" + siteId)) {
                    queueSiteForArchive(siteId);
                }
                res.getWriter().write("{'success':true}");

                return END;
            } catch (Exception e) {
                throw new PortalHandlerException(e);
            }
        }

        return NEXT;
    }

    private List<String> instructorTerms() {
        List<String> result = new ArrayList<>();

        String netid = UserDirectoryService.getCurrentUser().getEid();

        Connection db = null;
        try {
            db = sqlService.borrowConnection();

            try (PreparedStatement ps = db.prepareStatement("select distinct nvl(sss.term, initcap(sss.site_type)) as term" +
                    " from NYU_T_SELFSERV_MIG_ACCESS ma" +
                    " inner join NYU_T_SELFSERV_SITES sss on sss.site_id = ma.site_id" +
                    " where ma.netid = ?")) {
                ps.setString(1, netid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("term"));
                    }
                }
            }
        } catch (SQLException e) {
            M_log.warn(this + ".instructorTerms: " + e);
            e.printStackTrace();
        } finally {
            if (db != null) {
                sqlService.returnConnection(db);
            }
        }

        return result;
    }

    public boolean isAllowedToMigrateSitesToBrightspace() {
        if (!"true".equals(HotReloadConfigurationService.getString("brightspace.selfservice.enabled", "true"))) {
            // Self-service is disabled!
            return false;
        }

        String netid = UserDirectoryService.getCurrentUser().getEid();
        Session session = SessionManager.getCurrentSession();

        long timerStartTime = System.currentTimeMillis();

        try {
            if (netIdExplicitlyAllowed(netid, session)) {
                return true;
            } else if (allowedByRule(netid, session)) {
                return true;
            } else {
                return false;
            }
        } finally {
            long timerEndTime = System.currentTimeMillis();
            long duration = timerEndTime - timerStartTime;

            if (duration > 2000) {
                M_log.warn(String.format("isAllowedToMigrateSitesToBrightspace took %d ms to complete", duration));
            }
        }
    }

    public boolean netIdExplicitlyAllowed(String netid, Session session) {
        Long lastCheckTime = (Long)session.getAttribute(SESSION_KEY_NETID_LAST_CHECK_TIME);
        if (lastCheckTime == null) {
            lastCheckTime = 0L;
        }

        if ((System.currentTimeMillis() - lastCheckTime) > NETID_RECHECK_INTERVAL_MS) {
            // Refresh our value
            boolean result = false;

            Connection db = null;
            try {
                db = sqlService.borrowConnection();

                // First, let's check if the netid has been approved
                try (PreparedStatement ps = db.prepareStatement("select count(1) " +
                                                                " from NYU_T_SELF_SERVICE_ACCESS ssa" +
                                                                " where ssa.netid = ?" +
                                                                " and ssa.rule_type = 'netid'")) {
                    ps.setString(1, netid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            result = rs.getInt(1) > 0;
                        }
                    }
                }
            } catch (SQLException e) {
                M_log.warn(this + ".netIdExplicitlyAllowed: " + e);
            } finally {
                if (db != null) {
                    sqlService.returnConnection(db);
                }
            }

            session.setAttribute(SESSION_KEY_NETID_LAST_CHECK_TIME, System.currentTimeMillis());
            session.setAttribute(SESSION_KEY_NETID_LAST_VALUE, result);
        }

        return (boolean)session.getAttribute(SESSION_KEY_NETID_LAST_VALUE);
    }

    public boolean allowedByRule(String netid, Session session) {
        Long lastCheckTime = (Long)session.getAttribute(SESSION_KEY_RULE_LAST_CHECK_TIME);
        if (lastCheckTime == null) {
            lastCheckTime = 0L;
        }

        if ((System.currentTimeMillis() - lastCheckTime) > RULE_RECHECK_INTERVAL_MS) {
            // Refresh our value
            boolean result = false;

            Connection db = null;
            try {
                db = sqlService.borrowConnection();

                RuleSet ruleSet = new RuleSet();

                try (PreparedStatement ps = db.prepareStatement("select * " +
                                                                " from NYU_T_SELF_SERVICE_ACCESS ssa" +
                                                                " where ssa.rule_type != 'netid'");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ruleSet.addRule(rs.getString("school"), rs.getString("department"));
                    }
                }

                result = instructorSites().stream().anyMatch(s -> ruleSet.matches(s));
            } catch (SQLException e) {
                M_log.warn(this + ".netIdExplicitlyAllowed: " + e);
            } finally {
                if (db != null) {
                    sqlService.returnConnection(db);
                }
            }

            session.setAttribute(SESSION_KEY_RULE_LAST_CHECK_TIME, System.currentTimeMillis());
            session.setAttribute(SESSION_KEY_RULE_LAST_VALUE, result);
        }

        return (boolean)session.getAttribute(SESSION_KEY_RULE_LAST_VALUE);
    }

    private List<SiteToArchive> instructorSites() {
        return instructorSites(null, null);
    }

    private List<SiteToArchive> instructorSites(String termFilter, String queryFilter) {
        String netid = UserDirectoryService.getCurrentUser().getEid();
        Map<String, SiteToArchive> results = new HashMap<>();
        Set<String> allowedTermEids = allowedTermEids();

        List<String> replacements = new ArrayList<>();
        replacements.add(netid);

        String termFilterSQL = "";
        if (termFilter != null) {
            termFilterSQL = " and (sss.term = ? or (sss.term is null and sss.site_type = lower(?))) ";
            replacements.add(termFilter);
            replacements.add(termFilter);
        }
        
        String querySubSelect = "select site_id from NYU_T_SELFSERV_SITES";
        if (queryFilter != null && queryFilter.length() >= 3) {
            querySubSelect = "select sss.site_id" +
                             " from NYU_T_SELFSERV_MIG_ACCESS ma" +
                             " inner join NYU_T_SELFSERV_SITES sss on sss.site_id = ma.site_id" +
                             " left join NYU_T_SELFSERV_INSTRS ssi on ssi.site_id = sss.site_id" +
                             " left join NYU_T_SELFSERV_ROSTERS ssr on ssr.site_id = sss.site_id" +
                             " where ma.netid = ?" +
                             " and (ssi.netid = ? or ssr.roster_id = ? or instr(lower(sss.title), lower(?)) >= 1)";

            replacements.add(netid);
            replacements.add(queryFilter);
            replacements.add(queryFilter);
            replacements.add(queryFilter);
        }

        Connection db = null;
        try {
            db = sqlService.borrowConnection();

            try (PreparedStatement ps = db.prepareStatement("select sss.*, ssi.netid, ssi.fname, ssi.lname, ssr.roster_id, aq.queued_by, aq.queued_at, aq.archived_at, aq.uploaded_at, aq.completed_at, aq.brightspace_org_unit_id" +
                    " from NYU_T_SELFSERV_MIG_ACCESS ma" +
                    " inner join NYU_T_SELFSERV_SITES sss on sss.site_id = ma.site_id" +
                    " left join NYU_T_SELFSERV_INSTRS ssi on ssi.site_id = sss.site_id" +
                    " left join NYU_T_SELFSERV_ROSTERS ssr on ssr.site_id = sss.site_id" +
                    " left join NYU_T_SITE_ARCHIVES_QUEUE aq on aq.site_id = sss.site_id" +
                    " where ma.netid = ?" +
                    termFilterSQL + 
                    " and sss.site_id in (" + querySubSelect + ")"
                    )) {
                
                for (int i=0; i<replacements.size(); i++) {
                    ps.setString(i+1, replacements.get(i));
                }

                ResultSet rs = ps.executeQuery();
                try {
                    while (rs.next()) {
                        String siteId = rs.getString("site_id");
                        String termEid = rs.getString("term");

                        if (termEid != null && !allowedTermEids.contains(termEid)) {
                            continue;
                        }

                        if (!results.containsKey(siteId)) {
                            SiteToArchive siteToArchive = new SiteToArchive();
                            siteToArchive.siteId = siteId;
                            siteToArchive.title = rs.getString("title");
                            siteToArchive.term = rs.getString("term");
                            siteToArchive.department = rs.getString("department");
                            siteToArchive.school = rs.getString("school");
                            siteToArchive.location = rs.getString("location");
                            if (siteToArchive.term == null) {
                                siteToArchive.term = StringUtils.capitalize(rs.getString("site_type"));
                            }

                            if (rs.getString("queued_by") != null) {
                                SiteArchiveRequest request = new SiteArchiveRequest();
                                request.queuedAt = rs.getLong("queued_at");
                                request.queuedBy = rs.getString("queued_by");
                                request.archivedAt = rs.getLong("archived_at");
                                request.uploadedAt = rs.getLong("uploaded_at");
                                request.completedAt = rs.getLong("completed_at");
                                request.brightspaceOrgUnitId = rs.getLong("brightspace_org_unit_id");
                                siteToArchive.requests.add(request);
                            }

                            results.put(siteId, siteToArchive);
                        }

                        SiteToArchive siteToArchive = results.get(siteId);

                        String instructorNetid = rs.getString("netid");
                        String instructorFirstName = rs.getString("fname");
                        String instructorLastName = rs.getString("lname");
                        if (instructorNetid != null) {
                            if (instructorFirstName != null && instructorLastName != null) {
                                siteToArchive.instructors.put(instructorNetid, String.format("%s %s (%s)", instructorFirstName, instructorLastName, instructorNetid));
                            } else {
                                siteToArchive.instructors.put(instructorNetid, instructorNetid);
                            }
                        }

                        String rosterId = rs.getString("roster_id");
                        if (rosterId != null) {
                            siteToArchive.rosters.add(rosterId);
                        }
                    }
                } finally {
                    rs.close();
                }
            }

            List<SiteToArchive> sites = new ArrayList<>(results.values());

            List<String> termOrdering = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement("select cle_eid from nyu_t_acad_session order by strm desc")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        termOrdering.add(rs.getString("cle_eid"));
                    }
                }
            }

            termOrdering.add("Sandboxes");
            termOrdering.add("Project");

            Collections.sort(sites, (s1, s2) -> {
                return termOrdering.indexOf(s1.term) - termOrdering.indexOf(s2.term);
            });

            return sites;
        } catch (SQLException e) {
            M_log.error(this + ".instructorSites: " + e);
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (db != null) {
                sqlService.returnConnection(db);
            }
        }
    }

    private void queueSiteForArchive(String siteId) {
        String netid = UserDirectoryService.getCurrentUser().getEid();

        Connection db = null;
        try {
            db = sqlService.borrowConnection();

            try (PreparedStatement ps = db.prepareStatement("insert into NYU_T_SITE_ARCHIVES_QUEUE (site_id, queued_at, queued_by)" +
                    " values (?, ?, ?)")) {
                ps.setString(1, siteId);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, netid);
                ps.executeUpdate();
                db.commit();
            }
        } catch (SQLException e) {
            M_log.error(this + ".queueSiteForArchive: " + e);
        } finally {
            if (db != null) {
                sqlService.returnConnection(db);
            }
        }
    }

    private Set<String> allowedTermEids() {
        String termEidsStr = HotReloadConfigurationService.getString("brightspace.selfservice.termeids", "").trim();
        return new HashSet<>(Arrays.asList(termEidsStr.split(" *, *")));
    }

    private class SiteToArchive {
        public String siteId;
        public String title;
        public String term;
        public String school;
        public String department;
        public String location;
        public List<SiteArchiveRequest> requests = new ArrayList<>();
        public Set<String> rosters = new HashSet<>();
        public Map<String, String> instructors = new HashMap<>();
    }

    private class SiteArchiveRequest {
        public String queuedBy;
        public long queuedAt;
        public long archivedAt;
        public long uploadedAt;
        public long completedAt;
        public long brightspaceOrgUnitId;

        public String getStatus() {
            if (this.completedAt > 0) {
                return "COMPLETED";
            } else if (this.uploadedAt > 0) {
                return "UPLOADED";
            } else if (this.archivedAt > 0) {
                return "ARCHIVED";
            } else if (this.queuedAt > 0) {
                return "QUEUED";
            } else {
                return "READY_FOR_ARCHIVE";
            }
        }
    }

    private class AccessRule {
        public String school;
        public String department;
    }

    private class RuleSet {
        public Map<String, List<AccessRule>> rules = new HashMap<>();

        public void addRule(String school, String department) {
            if (!rules.containsKey(school)) {
                rules.put(school, new ArrayList<>());
            }
            AccessRule rule = new AccessRule();
            rule.school = school;
            rule.department = department;

            rules.get(school).add(rule);
        }

        public boolean matches(SiteToArchive site) {
            if (rules.containsKey(site.school)) {
                List<AccessRule> matched = rules.get(site.school);

                return matched.stream().anyMatch(r -> {
                    return r.department == null || r.department.equals(site.department);
                });
            }

            return false;
        }
    }
}


//    CREATE TABLE "NYU_T_SITE_ARCHIVES_QUEUE"
//        ("SITE_ID" VARCHAR2(100) NOT NULL,
//        "QUEUED_BY" VARCHAR2(100),
//        "QUEUED_AT" NUMBER(38,0),
//        "ARCHIVED_AT" NUMBER(38,0),
//        "UPLOADED_AT" NUMBER(38,0),
//        "COMPLETED_AT" NUMBER(38,0),
//        "BRIGHTSPACE_ORG_UNIT_ID" NUMBER(38,0),
//        CONSTRAINT "NYU_T_SITE_ARCHIVES_QUEUE_PK" PRIMARY KEY ("SITE_ID"))

