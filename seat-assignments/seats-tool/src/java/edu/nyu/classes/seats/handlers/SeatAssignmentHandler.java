package edu.nyu.classes.seats.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;

import org.json.simple.JSONObject;

public class SeatAssignmentHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        JSONObject result = new JSONObject();

        String siteId = (String)context.get("siteId");
        DBConnection db = (DBConnection)context.get("db");

        LMSConnection lms = (LMSConnection)context.get("lms");

        RequestParams p = new RequestParams(request);

        boolean isInstructor = (boolean)context.get("hasSiteUpd");

        String sectionId = p.getString("sectionId", null);
        if (sectionId == null) {
            throw new RuntimeException("Need argument: sectionId");
        }

        String groupId = p.getString("groupId", null);
        if (groupId == null) {
            throw new RuntimeException("Need argument: groupId");
        }

        String meetingId = p.getString("meetingId", null);
        if (meetingId == null) {
            throw new RuntimeException("Need argument: meetingId");
        }

        String netid = p.getString("netid", null);
        if (netid == null) {
            throw new RuntimeException("Need argument: netid");
        }

        SeatSection seatSection = SeatsStorage.getSeatSection(db, sectionId, siteId).get();
        Meeting meeting = seatSection.fetchGroup(groupId).get().getOrCreateMeeting(meetingId);

        String seat = p.getString("seat", null);
        String currentSeat = p.getString("currentSeat", null);

        SeatAssignment seatAssignment = new SeatAssignment(null, netid, seat, 0, meeting);

        // Force student to their own netid
        if (!isInstructor) {
            netid = lms.getCurrentUserNetId();
        }

        if (isInstructor && seatAssignment.seat == null) {
            SeatsStorage.clearSeat(db, seatAssignment);
        } else if (seatAssignment.seat != null) {
            SeatsStorage.SetSeatResult seatResult = SeatsStorage.setSeat(db, seatAssignment, currentSeat, isInstructor);
            if (!SeatsStorage.SetSeatResult.OK.equals(seatResult)) {
                result.put("error", true);
                if (SeatsStorage.SetSeatResult.SEAT_TAKEN.equals(seatResult)) {
                    result.put("error_code", isInstructor ? "SEAT_TAKEN_INSTRUCTOR" : "SEAT_TAKEN");
                } else {
                    result.put("error_code", seatResult.toString());
                }
            }
        } else {
            result.put("error", true);
            result.put("error_code", "SEAT_REQUIRED");
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

    @Override
    public boolean isSiteUpdRequired() {
        return false;
    }
}


