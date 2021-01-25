package edu.nyu.classes.seats.storage;

import java.util.*;

import edu.nyu.classes.seats.storage.db.DBConnection;
import edu.nyu.classes.seats.models.*;

public interface LMSConnection {

    public Integer getGroupMaxForSite(String siteId);

    public boolean hasBlendedInstructionMode(DBConnection db, SeatSection seatSection, String siteId);

    public Map<String, SeatsStorage.UserDisplayName> getMemberNames(SeatSection section);

    public List<Member> getMembersForSection(DBConnection db, SeatSection seatSection);

    public List<Member> getMembersForSite(DBConnection db, String siteId);

    public String getCurrentUserNetId();

    public String getCurrentUserDisplayName();

    default public boolean isCurrentUserRoleSwapped(String siteId) {
        return false;
    }

    public void sendPlaintextEmail(List<String> toNetIds,
                                          List<String> ccNetIds,
                                          List<String> bccNetIds,
                                          String subject,
                                          String body);
}
