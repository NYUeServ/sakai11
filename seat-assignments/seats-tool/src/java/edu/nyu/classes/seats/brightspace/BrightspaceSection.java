package edu.nyu.classes.seats.brightspace;

import edu.nyu.classes.seats.storage.db.DBConnection;

import edu.nyu.classes.seats.storage.SeatsStorage;
import edu.nyu.classes.seats.models.Member;

import org.sakaiproject.component.cover.HotReloadConfigurationService;

import java.io.*;
import java.util.*;

public class BrightspaceSection {

    private Long brightspaceId;
    private String rosterId;

    private SeatsStorage.StudentLocations locations;

    private List<Member> members;

    public BrightspaceSection(DBConnection db, Long brightspaceId, String rosterId) {
        this.brightspaceId = brightspaceId;
        this.rosterId = rosterId;

        try {
            this.locations = SeatsStorage.getStudentLocationsForSection(db, rosterId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.members = new ArrayList<>();
    }

    private Member.Role mapRole(String brightspaceRoleString) {
        if (brightspaceRoles("instructor").contains(brightspaceRoleString)) {
            return Member.Role.INSTRUCTOR;
        } else if (brightspaceRoles("student").contains(brightspaceRoleString)) {
            return Member.Role.STUDENT;
        } else if (brightspaceRoles("teaching_assistant").contains(brightspaceRoleString)) {
            return Member.Role.TEACHING_ASSISTANT;
        } else if (brightspaceRoles("course_site_admin").contains(brightspaceRoleString)) {
            return Member.Role.COURSE_SITE_ADMIN;
        } else {
            return null;
        }
    }

    private List<String> brightspaceRoles(String role) {
        String roleList = HotReloadConfigurationService.getString(String.format("seats.brightspace.%s_roles", role), "");

        if (roleList.isEmpty()) {
            return new ArrayList<>();
        } else {
            return Arrays.asList(roleList.split(" *, *"));
        }
    }

    public void addMember(String netid, String email, String displayName, String brightspaceRole) {
        Member.Role role = mapRole(brightspaceRole);

        if (role != null) {
            this.members.add(new Member(netid, true, role, locations.forNetid(netid)));
        }
    }

    public List<Member> getMembers() {
        return this.members;
    }

    public String getRosterId() {
        return this.rosterId;
    }
}
