package edu.nyu.classes.seats.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Comparator;
import java.text.DateFormat;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.time.api.Time;

import java.util.stream.Collectors;
import java.util.Collections;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.net.URL;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentTypeImageService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.content.api.GroupAwareEntity.AccessMode;


import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.authz.api.Member;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class SeatAssignmentHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            JSONObject result = new JSONObject();

            DBConnection db = (DBConnection)context.get("db");

            RequestParams p = new RequestParams(request);

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

            SeatSection seatSection = SeatsStorage.getSeatSection(db, sectionId);
            Meeting meeting = seatSection.fetchGroup(groupId).get().getOrCreateMeeting(meetingId);

            String seat = p.getString("seat", null);

            SeatAssignment seatAssignment = new SeatAssignment(null, netid, seat, meeting);

            if (seat == null) {
                SeatsStorage.clearSeat(db, seatAssignment);
            } else {
                SeatsStorage.setSeat(db, seatAssignment);
            }

            try {
                response.getWriter().write(result.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

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

