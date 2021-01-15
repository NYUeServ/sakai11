package edu.nyu.classes.seats.storage;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.*;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.db.*;
import edu.nyu.classes.seats.brightspace.*;
import edu.nyu.classes.seats.storage.migrations.BaseMigration;
import edu.nyu.classes.seats.storage.LTISession;
import edu.nyu.classes.seats.storage.Audit.AuditEvents;
import edu.nyu.classes.seats.BrightspaceEmails;
import edu.nyu.classes.seats.Utils;

import org.json.simple.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrightspaceSeatsStorage implements LMSConnection {

    private static final Logger LOG = LoggerFactory.getLogger(BrightspaceSeatsStorage.class);

    private BrightspaceClient brightspace;

    public BrightspaceSeatsStorage(BrightspaceClient brightspace) {
        this.brightspace = brightspace;
    }

    public static SeatsStorage.SyncResult syncGroupsToSection(DBConnection db, SeatSection section, BrightspaceSectionInfo sectionInfo) throws SQLException {
        List<Member> sectionMembers = getMembersForSectionFromInfo(db, section, sectionInfo);
        Map<String, Integer> groupCounts = new HashMap<>();

        Set<String> sectionNetids = sectionMembers.stream().map(m -> m.netid).collect(Collectors.toSet());

        Map<String, List<Member>> drops = new HashMap<>();
        for (SeatGroup group : section.listGroups()) {
            for (Member member : group.listMembers()) {
                if (!sectionNetids.contains(member.netid)) {
                    SeatsStorage.removeMemberFromGroup(db, section, group.id, member.netid);

                    drops.putIfAbsent(group.id, new ArrayList<>());
                    drops.get(group.id).add(member);
                }
            }
        }

        // handle adds
        Set<String> seatGroupMembers = new HashSet<>();

        for (SeatGroup group : section.listGroups()) {
            groupCounts.put(group.id, group.listMembers().size());
            seatGroupMembers.addAll(group.listMembers().stream().map(m -> m.netid).collect(Collectors.toList()));
        }

        Map<String, List<Member>> adds = new HashMap<>();
        for (Member member : sectionMembers) {
            if (seatGroupMembers.contains(member.netid)) {
                continue;
            }

            // Find the group with the smallest count and put them into it
            String groupId = groupCounts.keySet().stream().min((o1, o2) -> groupCounts.get(o1) - groupCounts.get(o2)).get();
            SeatsStorage.addMemberToGroup(db, member, groupId, section.id);
            groupCounts.put(groupId, groupCounts.get(groupId) + 1);

            adds.putIfAbsent(groupId, new ArrayList<>());
            adds.get(groupId).add(member);
        }

        return new SeatsStorage.SyncResult(adds, drops);
    }

    public static List<Member> getMembersForSectionFromInfo(DBConnection db, SeatSection section, BrightspaceSectionInfo sectionInfo) throws SQLException {
        List<String> rosterIds = db.run("select sakai_roster_id from seat_group_section_rosters where section_id = ?")
            .param(section.id)
            .executeQuery()
            .getStringColumn("sakai_roster_id");

        Set<Member> result = new HashSet<>();

        for (String rosterId : rosterIds) {
            result.addAll(sectionInfo.getSection(rosterId).getMembers());
        }

        return new ArrayList<>(result);
    }

    public static Map<String, SeatsStorage.UserDisplayName> getMemberNames(List<String> eids) {
        Map<String, SeatsStorage.UserDisplayName> result = new HashMap<>();

        if (eids.isEmpty()) {
            return result;
        }

        DB.transaction
            ("Read section member names",
             (DBConnection db) -> {
                try {
                    int lower = 0;
                    int maxClauses = 200;

                    for (;;) {
                        int upper = Math.min(eids.size(), lower + maxClauses);

                        List<String> subset = eids.subList(lower, upper);

                        String placeholders = db.placeholders(subset);

                        db.run(String.format("select fname, lname, netid, email from nyu_t_users where netid in (%s)",
                                             placeholders))
                            .stringParams(subset)
                            .executeQuery()
                            .each((row) -> {
                                    result.put(row.getString("netid"),
                                               new SeatsStorage.UserDisplayName(row.getString("netid"),
                                                                                String.format("%s %s",
                                                                                              Objects.toString(row.getString("fname"), ""),
                                                                                              Objects.toString(row.getString("lname"), "")).trim(),
                                                                                Objects.toString(row.getString("fname"), ""),
                                                                                Objects.toString(row.getString("lname"), ""),
                                                                                Objects.toString(row.getString("email"), "")));
                                });

                        if (upper == eids.size()) {
                            break;
                        }

                        lower += maxClauses;
                    }

                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

        return result;
    }

    // LMS Connection stuff
    @Override
    public Integer getGroupMaxForSite(String _siteId) {
        return 4;
    }

    @Override
    public boolean hasBlendedInstructionMode(DBConnection db, SeatSection seatSection, String _siteId) {
        try {
            return SeatsStorage.hasBlendedInstructionMode(db, seatSection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Map<String, SeatsStorage.UserDisplayName> getMemberNames(SeatSection seatSection) {
        Set<String> allEids = new HashSet<>();

        for (SeatGroup group : seatSection.listGroups()) {
            allEids.addAll(group.listMembers().stream().map(m -> m.netid).collect(Collectors.toList()));
        }

        return getMemberNames(new ArrayList<>(allEids));
    }

    @Override
    public List<Member> getMembersForSection(DBConnection db, SeatSection seatSection) {
        try {
            BrightspaceSectionInfo sectionInfo = brightspace.getSectionInfo(db, seatSection.siteId);

            return BrightspaceSeatsStorage.getMembersForSectionFromInfo(db, seatSection, sectionInfo);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Member> getMembersForSite(DBConnection db, String siteId) {
        BrightspaceSectionInfo sectionInfo = brightspace.getSectionInfo(db, siteId);

        return sectionInfo.listAllMembers();
    }

    @Override
    public String getCurrentUserNetId() {
        return LTISession.getString("netid", null);
    }

    @Override
    public String getCurrentUserDisplayName() {
        return LTISession.getString("user_name", "");
    }

    private List<BrightspaceEmails.EmailAddress> netIdsToEmailAddress(List<String> netIds) {
        if (netIds.isEmpty()) {
            return new ArrayList<>(0);
        }

        Map<String, SeatsStorage.UserDisplayName> members = getMemberNames(netIds);

        return netIds.stream().map(netid -> {
                SeatsStorage.UserDisplayName name = members.get(netid);

                if (name == null) {
                    LOG.warn("WARNING: User lookup failed for netid: " + netid);

                    return BrightspaceEmails.makeEmailAddress(netid, netid + "@nyu.edu");
                } else {
                    return BrightspaceEmails.makeEmailAddress(name.displayName, name.email);
                }
            }).collect(Collectors.toList());
    }

    @Override
    public void sendPlaintextEmail(List<String> toNetIds,
                                   List<String> ccNetIds,
                                   List<String> bccNetIds,
                                   String subject,
                                   String body) {
        BrightspaceEmails.sendPlaintextEmail(netIdsToEmailAddress(toNetIds),
                                             netIdsToEmailAddress(ccNetIds),
                                             netIdsToEmailAddress(bccNetIds),
                                             subject,
                                             body);
    }
}
