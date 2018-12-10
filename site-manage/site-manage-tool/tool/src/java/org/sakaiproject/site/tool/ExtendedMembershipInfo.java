package org.sakaiproject.site.tool;

import org.sakaiproject.site.api.Site;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ExtendedMembershipInfo {
    private Site site;

    public ExtendedMembershipInfo(Site s) {
        site = s;
    }

    public String getId() { return site.getId(); }
    public String getTitle() { return site.getTitle(); }
    public String getUrl() { return site.getUrl(); }

    public String getDescription() { return site.getDescription(); }

    public String getFormattedPublicationStatus() {
        return site.isPublished() ? "Published" : "Unpublished";
    }

    public String getFormattedInstructorList() {
        Set<String> userIds = site.getUsersHasRole("Instructor");
        List<User> users = UserDirectoryService.getUsers(userIds);

        return users.stream()
            .map(u -> u.getDisplayName())
            .collect(Collectors.joining(", "));
    }

    public String getFormattedDateCreated() {
        Date created = site.getCreatedDate();

        if (created == null) {
            return "";
        }

        return new SimpleDateFormat("dd MMM, YYYY").format(created);
    }

    public String getFormattedTerm() {
        String result = (String)site.getProperties().get("term");

        if (result == null) {
            return "";
        }

        return result;
    }
}

