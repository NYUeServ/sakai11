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
import org.sakaiproject.javax.PagingPosition;
import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.user.cover.UserDirectoryService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class BrightspaceMigratorHandler extends BasePortalHandler {

    private static final String URL_FRAGMENT = "brightspace-migrator";
    private static final String SESSION_KEY_ALLOWED_TO_MIGRATE = "NYU_ALLOWED_TO_MIGRATE";

    private static final String SESSION_KEY_NETID_LAST_CHECK_TIME = "SESSION_KEY_NETID_LAST_CHECK_TIME";
    private static final String SESSION_KEY_NETID_LAST_VALUE = "SESSION_KEY_NETID_LAST_VALUE";
    private static final String SESSION_KEY_RULE_LAST_CHECK_TIME = "SESSION_KEY_RULE_LAST_CHECK_TIME";
    private static final String SESSION_KEY_RULE_LAST_VALUE = "SESSION_KEY_RULE_LAST_VALUE";

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
                Set<String> terms = new HashSet<>();

                String termFilter = StringUtils.trimToNull(req.getParameter("term"));
                String queryFilter = StringUtils.trimToNull(req.getParameter("q"));

                for (SiteToArchive site : instructorSites()) {
                    terms.add(site.term);

                    if (termFilter != null && !termFilter.equals(site.term)) {
                        continue;
                    }

                    if (queryFilter != null) {
                        boolean matchesTitle = site.title.toLowerCase().contains(queryFilter.toLowerCase());

                        if (!matchesTitle) {
                            boolean matchesRoster = site.rosters.stream().anyMatch(r -> r.toLowerCase().equals(queryFilter.toLowerCase()));

                            if (!matchesRoster) {
                                boolean matchesInstructor = site.instructors.stream().anyMatch(instructor -> instructor.get("netid").toLowerCase().equals(queryFilter.toLowerCase()));

                                if (!matchesInstructor) {
                                    continue;
                                }
                            }
                        }
                    }

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
                    for (Map<String, String> instructor : site.instructors) {
                        JSONObject instructorJSON = new JSONObject();
                        instructorJSON.put("netid", instructor.get("netid"));
                        instructorJSON.put("display", instructor.get("displayName"));
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
                for (String term : terms) {
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


    private List<Site> loadDelegatedAccessSites(UserDirectoryService.getCurrentUser().getId()) {
        // Find the nodes that the current user has been granted permission for
        {nodeid => perm, nodeid2 => perm}

        // find all node ids under those nodes

        // pull back corresponding site ids for those nodes (another hash)

        // for each node with a site id, find the nearest parent with a permission (backwards walk)
    }

    private List<SiteToArchive> instructorSites() {
        Map<String, SiteToArchive> results = new LinkedHashMap<>();
        Set<String> allowedTermEids = allowedTermEids();

        Connection db = null;
        try {
            db = sqlService.borrowConnection();

            int start = 1;
            int pageSize = 100;
            int last = start + pageSize - 1;

            PagingPosition paging = new PagingPosition();
            while (true) {
                paging.setPosition(start, last);
                List<Site> userSites = SiteService.getSites(org.sakaiproject.site.api.SiteService.SelectionType.UPDATE, null, null, null, org.sakaiproject.site.api.SiteService.SortType.CREATED_ON_DESC, paging);
                userSites = userSites.stream().filter(s -> {
                                String termEid = s.getProperties().getProperty("term_eid");
                                return termEid == null || allowedTermEids.contains(termEid);
                            }).collect(Collectors.toList());

                for (Site userSite : userSites) {
                    if (userSite.getType() == null) {
                        continue;
                    }

                    SiteToArchive siteToArchive = new SiteToArchive();
                    siteToArchive.siteId = userSite.getId();
                    siteToArchive.title = userSite.getTitle();
                    siteToArchive.term = userSite.getProperties().getProperty("term_eid");
                    if (siteToArchive.term == null) {
                        siteToArchive.term = StringUtils.capitalize(userSite.getType());
                    }
                    siteToArchive.requests = new ArrayList<>();
                    siteToArchive.rosters = new ArrayList<>(authzGroupService.getProviderIds(String.format("/site/%s", userSite.getId())));
                    siteToArchive.instructors = new ArrayList<>();

                    siteToArchive.school = userSite.getProperties().getProperty("School");
                    siteToArchive.department = userSite.getProperties().getProperty("Department");

                    results.put(siteToArchive.siteId, siteToArchive);
                }

                List<String> chunkSiteIds = userSites.stream().map(s -> s.getId()).collect(Collectors.toList());

                String placeholders = chunkSiteIds.stream().map(_p -> "?").collect(Collectors.joining(","));

                try (PreparedStatement ps = db.prepareStatement("select * from NYU_T_SITE_ARCHIVES_QUEUE where site_id in (" + placeholders + ")")) {
                    for (int i = 0; i < chunkSiteIds.size(); i++) {
                        ps.setString(i + 1, chunkSiteIds.get(i));
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String siteId = rs.getString("site_id");

                            if (rs.getString("queued_by") != null) {
                                SiteArchiveRequest request = new SiteArchiveRequest();
                                request.queuedAt = rs.getLong("queued_at");
                                request.queuedBy = rs.getString("queued_by");
                                request.archivedAt = rs.getLong("archived_at");
                                request.uploadedAt = rs.getLong("uploaded_at");
                                request.completedAt = rs.getLong("completed_at");
                                request.brightspaceOrgUnitId = rs.getLong("brightspace_org_unit_id");
                                results.get(siteId).requests.add(request);
                            }
                        }
                    }
                }

                try (PreparedStatement ps = db.prepareStatement("select distinct ss.site_id, memb.user_id, usr.fname, usr.lname" +
                                                                " from cm_member_container_t cont" +
                                                                " inner join cm_membership_t memb on cont.member_container_id = memb.member_container_id AND memb.role = 'I'" +
                                                                " inner join sakai_realm_provider srp on srp.provider_id = cont.enterprise_id" +
                                                                " inner join sakai_realm sr on srp.realm_key = sr.realm_key" +
                                                                " inner join sakai_site ss on sr.realm_id = concat('/site/', ss.site_id)" +
                                                                " left outer join NYU_T_USERS usr on usr.netid = memb.user_id" +
                                                                " where ss.site_id in (" + placeholders + ")")) {
                    for (int i = 0; i < chunkSiteIds.size(); i++) {
                        ps.setString(i + 1, chunkSiteIds.get(i));
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String siteId = rs.getString("site_id");
                            String netid = rs.getString("user_id");

                            Map<String, String> instructor = new HashMap<>();
                            instructor.put("netid", netid);
                            String firstName = rs.getString("fname");
                            String lastName = rs.getString("lname");
                            String displayName = netid;
                            if (firstName != null && lastName != null) {
                                displayName = String.format("%s %s (%s)", firstName, lastName, netid);
                            }
                            instructor.put("displayName", displayName);

                            results.get(siteId).instructors.add(instructor);
                        }
                    }
                }

                if (userSites.size() < pageSize) {
                    break;
                }

                start = last + 1;
                last = start + pageSize - 1;
            }


            loadDelegatedAccessSites(UserDirectoryService.getCurrentUser().getId());

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
        public List<SiteArchiveRequest> requests;
        public List<String> rosters;
        public List<Map<String, String>> instructors;
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

