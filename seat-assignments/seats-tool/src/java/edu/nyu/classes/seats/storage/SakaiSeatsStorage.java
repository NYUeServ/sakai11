package edu.nyu.classes.seats.storage;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.*;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.coursemanagement.api.Membership;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.email.api.EmailAddress;

import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.authz.cover.SecurityService;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.db.*;
import edu.nyu.classes.seats.storage.migrations.BaseMigration;
import edu.nyu.classes.seats.storage.Audit.AuditEvents;
import edu.nyu.classes.seats.Utils;
import edu.nyu.classes.seats.SakaiEmails;

import org.json.simple.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SakaiSeatsStorage implements LMSConnection {

    public static SeatsStorage.SyncResult syncGroupsToSection(DBConnection db, SeatSection section, Site site) throws SQLException {
        List<Member> sectionMembers = new SakaiSeatsStorage().getMembersForSection(db, section);
        Set<String> seatGroupMembers = new HashSet<>();
        Map<String, Integer> groupCounts = new HashMap<>();

        Set<String> sectionNetids = new HashSet<>();
        for (Member member : sectionMembers) {
            sectionNetids.add(member.netid);
        }

        // handle upgrades to official
        for (SeatGroup group : section.listGroups()) {
            for (Member member : group.listMembers()) {
                if (sectionNetids.contains(member.netid) && !member.official && Member.Role.STUDENT.equals(member.role)) {
                    SeatsStorage.markMemberAsOfficial(db, group.id, member.netid);
                }
            }
        }

        Set<String> siteMembers = site.getMembers()
            .stream()
            .filter((m) -> m.isActive())
            .map((m) -> m.getUserEid())
            .collect(Collectors.toSet());

        // handle removes
        Map<String, List<Member>> drops = new HashMap<>();
        for (SeatGroup group : section.listGroups()) {
            for (Member member : group.listMembers()) {
                if (!member.official && siteMembers.contains(member.netid)) {
                    // Manual adds can stay in their groups, unless they've been removed from the site completely.
                    continue;
                }

                if (!sectionNetids.contains(member.netid)) {
                    SeatsStorage.removeMemberFromGroup(db, section, group.id, member.netid);

                    drops.putIfAbsent(group.id, new ArrayList<>());
                    drops.get(group.id).add(member);
                }
            }
        }

        // handle adds
        for (SeatGroup group : section.listGroups()) {
            groupCounts.put(group.id, group.listMembers().size());
            seatGroupMembers.addAll(group.listMembers().stream().map(m -> m.netid).collect(Collectors.toList()));
        }

        Map<String, List<Member>> adds = new HashMap<>();
        for (Member member : sectionMembers) {
            if (seatGroupMembers.contains(member.netid)) {
                continue;
            }

            String groupId = groupCounts.keySet().stream().min((o1, o2) -> groupCounts.get(o1) - groupCounts.get(o2)).get();
            SeatsStorage.addMemberToGroup(db, member, groupId, section.id);
            groupCounts.put(groupId, groupCounts.get(groupId) + 1);

            adds.putIfAbsent(groupId, new ArrayList<>());
            adds.get(groupId).add(member);
        }

        return new SeatsStorage.SyncResult(adds, drops);
    }

    public static boolean hasBlendedInstructionMode(DBConnection db, SeatSection seatSection, Site site) throws SQLException {
        ResourceProperties props = site.getProperties();
        String propInstructionModeOverrides = props.getProperty("OverrideToBlended");
        if (propInstructionModeOverrides != null) {
            if (Arrays.asList(Utils.stemNameToRosterId(propInstructionModeOverrides).split(" *, *")).contains(Utils.stemNameToRosterId(seatSection.primaryStemName))) {
                return true;
            }
        }

        return SeatsStorage.hasBlendedInstructionMode(db, seatSection);
    }


    public static Integer getGroupMaxForSite(Site site) {
        ResourceProperties props = site.getProperties();
        String propMaxGroupsString = props.getProperty("SeatingAssignmentsMaxGroups");
        return propMaxGroupsString == null ? 4 : Integer.valueOf(propMaxGroupsString);
    }


    public static Map<String, SeatsStorage.UserDisplayName> getMemberNames(Collection<String> eids) {
        Map<String, SeatsStorage.UserDisplayName> result = new HashMap<>();

        for (User user : UserDirectoryService.getUsersByEids(eids)) {
            result.put(user.getEid(), new SeatsStorage.UserDisplayName(user.getEid(), user.getDisplayName(), user.getFirstName(), user.getLastName(), user.getEmail()));
        }

        return result;
    }

    public static boolean stemIsEligible(DBConnection db, String stemName) throws SQLException {
        if (SeatsStorage.stemIsEligibleInstructionMode(db, stemName)) {
            return true;
        }

        // Site properties can override too.
        Optional<String> props = db.run("select to_char(ssp.value) as override_prop" +
                                        " from nyu_t_course_catalog cc" +
                                        " inner join sakai_realm_provider srp on srp.provider_id = replace(cc.stem_name, ':', '_')" +
                                        " inner join sakai_realm sr on sr.realm_key = srp.realm_key" +
                                        " inner join sakai_site ss on concat('/site/', ss.site_id) = sr.realm_id" +
                                        " inner join sakai_site_property ssp on ssp.site_id = ss.site_id AND ssp.name = 'OverrideToBlended'" +
                                        " where cc.stem_name = ?")
            .param(stemName)
            .executeQuery()
            .oneString();

        if (props.isPresent()) {
            return Arrays.asList(props.get().split(" *, *")).contains(stemName);
        } else {
            return false;
        }
    }


    // LMS Connection stuff
    @Override
    public Integer getGroupMaxForSite(String siteId) {
        try {
            Site site = SiteService.getSite(siteId);

            return SakaiSeatsStorage.getGroupMaxForSite(site);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasBlendedInstructionMode(DBConnection db, SeatSection seatSection, String siteId) {
        try {
            Site site = SiteService.getSite(siteId);

            return SakaiSeatsStorage.hasBlendedInstructionMode(db, seatSection, site);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Map<String, SeatsStorage.UserDisplayName> getMemberNames(SeatSection seatSection) {
        Set<String> allEids = new HashSet<>();

        for (SeatGroup group : seatSection.listGroups()) {
            allEids.addAll(group.listMembers().stream().map(m -> m.netid).collect(Collectors.toList()));
        }

        return SakaiSeatsStorage.getMemberNames(allEids);
    }

    @Override
    public List<Member> getMembersForSection(DBConnection db, SeatSection section) {
        try {
            List<String> rosterIds = db.run("select sakai_roster_id from seat_group_section_rosters where section_id = ?")
                .param(section.id)
                .executeQuery()
                .getStringColumn("sakai_roster_id");

            Set<Member> result = new HashSet<>();

            SeatsStorage.StudentLocations locations = SeatsStorage.getStudentLocationsForSection(db, section.id);

            CourseManagementService cms = (CourseManagementService) ComponentManager.get("org.sakaiproject.coursemanagement.api.CourseManagementService");
            for (String rosterId : rosterIds) {
                for (Membership membership : cms.getSectionMemberships(rosterId)) {
                    result.add(new Member(membership.getUserId(),
                                          true,
                                          Member.Role.forCMRole(membership.getRole()),
                                          locations.forNetid(membership.getUserId())));
                }
            }

            return new ArrayList<>(result);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Member> getMembersForSite(DBConnection db, String siteId) {
        try {
            Site site = SiteService.getSite(siteId);

            SeatsStorage.StudentLocations studentLocations = new SeatsStorage.StudentLocations();

            for (SeatSection section : SeatsStorage.siteSeatSections(db, siteId)) {
                studentLocations.putAll(SeatsStorage.getStudentLocationsForSection(db, section.id));
            }

            return site.getMembers()
                .stream()
                .filter(sakaiMember -> sakaiMember.isActive())
                .map(sakaiMember -> new Member(sakaiMember.getUserEid(),
                                               sakaiMember.isProvided(),
                                               Member.Role.fromSakaiRoleId(sakaiMember.getRole().getId()),
                                               studentLocations.forNetid(sakaiMember.getUserEid())))
                .collect(Collectors.toList());
        } catch (SQLException | IdUnusedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getCurrentUserNetId() {
        User currentUser = UserDirectoryService.getCurrentUser();
        return currentUser.getEid();
    }

    @Override
    public String getCurrentUserDisplayName() {
        User currentUser = UserDirectoryService.getCurrentUser();
        return currentUser.getDisplayName();
    }

    @Override
    public boolean isCurrentUserRoleSwapped(String siteId) {
        return ("Student".equals(SecurityService.getUserEffectiveRole(SiteService.siteReference(siteId))));
    }

    private List<EmailAddress> netIdsToEmailAddress(List<String> netIds) {
        if (netIds.isEmpty()) {
            return new ArrayList<>(0);
        }

        Map<String, Optional<User>> netIdUsers = new HashMap<>();

        List<User> users = UserDirectoryService.getUsersByEids(netIds);

        for (User u : users) {
            netIdUsers.put(u.getEid(), Optional.of(u));
        }

        return netIds
            .stream()
            .map(netid -> netIdUsers.getOrDefault(netid, Optional.empty()))
            .map(maybeUser -> maybeUser.map(u -> new EmailAddress(u.getEmail(), u.getDisplayName())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Override
    public void sendPlaintextEmail(List<String> toNetIds,
                                   List<String> ccNetIds,
                                   List<String> bccNetIds,
                                   String subject,
                                   String body) {
        SakaiEmails.sendPlaintextEmail(netIdsToEmailAddress(toNetIds),
                                       netIdsToEmailAddress(ccNetIds),
                                       netIdsToEmailAddress(bccNetIds),
                                       subject,
                                       body);
    }

}
