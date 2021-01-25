package edu.nyu.classes.seats.brightspace;

import edu.nyu.classes.seats.models.Member;
import edu.nyu.classes.seats.storage.db.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.sql.SQLException;

public class BrightspaceSectionInfo {
    private static final Logger LOG = LoggerFactory.getLogger(BrightspaceSectionInfo.class);


    Map<String, BrightspaceSection> sections;

    public List<Member> listAllMembers() {
        return getSections()
            .stream()
            .flatMap(section -> section.getMembers().stream())
            .distinct()
            .collect(Collectors.toList());
    }

    private static class NetIDLookup {
        public static Map<String, String> netIDsForNNumbers(DBConnection db, List<String> userNNumbers) {
            try {
                Map<String, String> result = new HashMap<>();

                if (userNNumbers.isEmpty()) {
                    return result;
                }

                int lower = 0;
                int maxClauses = 200;
                for (;;) {
                    int upper = Math.min(userNNumbers.size(), lower + maxClauses);

                    List<String> subset = userNNumbers.subList(lower, upper);

                    String placeholders = db.placeholders(subset);

                    db.run(String.format("select nyuidn, netid from nyu_t_users where nyuidn in (%s)",
                                         placeholders))
                        .stringParams(subset)
                        .executeQuery()
                        .each((row) -> {
                                result.put(row.getString("nyuidn"), row.getString("netid"));
                            });

                    if (upper == userNNumbers.size()) {
                        break;
                    }

                    lower += maxClauses;
                }

                return result;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public BrightspaceSectionInfo(DBConnection db, BrightspaceClient brightspace, JSON sectionData, List<JSON> userData) {
        Map<String, JSON> userJSONById = new HashMap<>();

        for (JSON page : userData) {
            for (JSON user : page.path("Items").asJSONList()) {
                String id = user.path("User > Identifier").asStringOrDie();
                userJSONById.put(id, user);
            }
        }

        sections = new HashMap<>();

        for (JSON section : sectionData.asJSONList()) {
            Long brightspaceId = section.path("SectionId").asLongOrDie();
            String rosterId = section.path("Code").asStringOrDie();
            BrightspaceSection bSection = new BrightspaceSection(db, brightspaceId, rosterId);

            List<String> userNNumbers = section.path("Enrollments").asLongList().stream().map((userId) -> {
                    JSON userJSON = userJSONById.get(String.valueOf(userId));
                    if (userJSON != null) {
                        return userJSON.path("User > OrgDefinedId").asString("IDENTIFIER_NOT_FOUND");
                    } else {
                        return "IDENTIFIER_NOT_FOUND";
                    }
                }).collect(Collectors.toList());

            Map<String, String> netidByNNumber = NetIDLookup.netIDsForNNumbers(db, userNNumbers);

            List<JSON> missedUsers = new ArrayList<>();

            for (Long userId : section.path("Enrollments").asLongList()) {
                JSON userJSON = userJSONById.get(String.valueOf(userId));

                if (userJSON == null) {
                    LOG.error(String.format("User not found for ID: %s", userId));
                    continue;
                }

                String netid = netidByNNumber.get(userJSON.path("User > OrgDefinedId").asString("NO_ORG_DEFINED_ID"));

                if (netid == null) {
                    LOG.warn(String.format("WARNING: Hitting slow path for user lookup: %s", userJSON));

                    missedUsers.add(userJSON);
                    continue;
                }

                bSection.addMember(netid,
                                   userJSON.path("User > EmailAddress").asString(null),
                                   userJSON.path("User > DisplayName").asString(netid),
                                   userJSON.path("Role > Name").asStringOrDie());
            }

            // Pick up after our missed users
            Map<String, String> userIdToNetId =
                brightspace.netIdsForUserIds(missedUsers
                                             .stream()
                                             .map(userJSON -> userJSON.path("User > Identifier").asString("NO_IDENTIFIER"))
                                             .collect(Collectors.toList()));

            for (JSON userJSON : missedUsers) {
                String userId = userJSON.path("User > Identifier").asString("NO_IDENTIFIER");
                String netid = userIdToNetId.get(userId);

                if (netid != null) {
                    LOG.info("Slow path revealed NetID was: " + netid);
                }

                // Yeesh, we tried...
                if (netid == null) {
                    LOG.error(String.format("Couldn't find a netid for: %s", userJSON));
                }

                bSection.addMember(netid,
                                   userJSON.path("User > EmailAddress").asString(null),
                                   userJSON.path("User > DisplayName").asString(netid),
                                   userJSON.path("Role > Name").asStringOrDie());

            }

            sections.put(rosterId, bSection);
        }
    }

    public List<BrightspaceSection> getSections() {
        return new ArrayList<>(sections.values());
    }

    public BrightspaceSection getSection(String rosterId) {
        String stemName = rosterId.replace("_", ":");

        return sections.get(stemName);
    }

}
