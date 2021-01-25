package edu.nyu.classes.seats.models;

import java.util.Locale;

public class Member {
    public enum StudentLocation {
        REMOTE,
        IN_PERSON,
    }

    public String netid;
    public StudentLocation studentLocation;
    public boolean official;
    public Role role;

    public enum Role {
        INSTRUCTOR,
        COURSE_SITE_ADMIN,
        STUDENT,
        TEACHING_ASSISTANT;

        public static Role forCMRole(String cmRole) {
            cmRole = cmRole.toUpperCase(Locale.ROOT);

            if ("I".equals(cmRole)) {
                return INSTRUCTOR;
            } else if ("S".equals(cmRole)) {
                return STUDENT;
            } else if ("A".equals(cmRole)) {
                return COURSE_SITE_ADMIN;
            } else {
                // Shouldn't happen, but student is a safe default.
                return STUDENT;
            }
        }

        public String toSakaiRoleId() {
            switch (this) {
            case INSTRUCTOR: return "Instructor";
            case COURSE_SITE_ADMIN: return "Course Site Admin";
            case STUDENT: return "Student";
            case TEACHING_ASSISTANT: return "Teaching Assistant";
            }

            throw new RuntimeException("BUG: Missed a case in toSakaiRoleId");
        }

        public static Role fromSakaiRoleId(String roleId) {
            switch (roleId) {
            case "Instructor": return INSTRUCTOR;
            case "Course Site Admin": return COURSE_SITE_ADMIN;
            case "Student": return STUDENT;
            case "Teaching Assistant": return TEACHING_ASSISTANT;
            }

            throw new RuntimeException("BUG: Missed a case in fromSakaiRoleId");
        }

    }

    public Member(String netid, boolean official, Role role, StudentLocation location) {
        this.netid = netid;
        this.studentLocation = location;
        this.official = official;
        this.role = role;
    }

    public int hashCode() {
        return netid.hashCode();
    }

    public boolean equals(Object other) {
        if (!(other instanceof Member)) {
            return false;
        }

        return ((Member) other).netid.equals(netid);
    }

    public boolean isInstructor() {
        return Role.INSTRUCTOR.equals(role) || Role.COURSE_SITE_ADMIN.equals(role);
    }

    public boolean isManager() {
        return Role.INSTRUCTOR.equals(role) || Role.COURSE_SITE_ADMIN.equals(role) || Role.TEACHING_ASSISTANT.equals(role);
    }

    public String sakaiRoleId() {
        return role.toSakaiRoleId();
    }
}
