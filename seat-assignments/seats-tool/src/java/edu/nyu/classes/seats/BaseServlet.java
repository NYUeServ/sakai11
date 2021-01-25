package edu.nyu.classes.seats;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.net.URL;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
// import org.sakaiproject.authz.cover.SecurityService;
// import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
// import org.sakaiproject.time.api.Time;
// import org.sakaiproject.time.cover.TimeService;
// import org.sakaiproject.tool.cover.ToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import edu.nyu.classes.seats.handlers.*;

public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(BaseServlet.class);

    private AtomicBoolean developmentMode = new AtomicBoolean(false);

    private Long logQueryThresholdMs = null;

    protected Handler handlerForRequest(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null) {
            path = "";
        }

        if (path.startsWith("/sections")) {
            return new SectionsHandler();
        }

        if (path.startsWith("/section")) {
            return new SectionHandler();
        }

        if (path.startsWith("/seat-assignment")) {
            return new SeatAssignmentHandler();
        }

        if (path.startsWith("/split-section")) {
            return new SplitSectionHandler();
        }

        if (path.startsWith("/available-site-members")) {
            return new MembersForAddHandler();
        }

        if (path.startsWith("/add-group-users")) {
            return new GroupAddMembersHandler();
        }

        if (path.startsWith("/student-meetings")) {
            return new StudentMeetingsHandler();
        }

        if (path.startsWith("/save-group-description")) {
            return new GroupDescriptionHandler();
        }

        if (path.startsWith("/add-group")) {
            return new AddGroupHandler();
        }

        if (path.startsWith("/delete-group")) {
            return new DeleteGroupHandler();
        }

        if (path.startsWith("/transfer-group")) {
            return new TransferGroupsHandler();
        }

        if (path.startsWith("/email-group")) {
            return new EmailGroupHandler();
        }

        if (path.startsWith("/remove-group-user")) {
            return new GroupRemoveMembersHandler();
        }

        if (path.startsWith("/launch")) {
            return new LTILaunchHandler();
        }

        if (path.startsWith("/tool")) {
            return new LTIToolHandler();
        }

        if (path.startsWith("/cookie-check")) {
            return new CookieCheckHandler();
        }

        if (path.startsWith("/site-check")) {
            return new SiteCheckHandler();
        }

        if ("true".equals(HotReloadConfigurationService.getString("seats.enable-fiddler", "false"))) {
            if (path.startsWith("/roster-fiddler")) {
                return new RosterFiddler();
            }
        }

        return new HomeHandler();
    }

    protected abstract URL determineBaseURL();

    protected Handlebars loadHandlebars(final URL baseURL, final I18n i18n) {
        Handlebars handlebars = new Handlebars();

        handlebars.setInfiniteLoops(true);

        handlebars.registerHelper("subpage", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String subpage = options.param(0);
                try {
                    Template template = handlebars.compile("edu/nyu/classes/seats/views/" + subpage);
                    return template.apply(context);
                } catch (IOException e) {
                    LOG.warn("IOException while loading subpage", e);
                    return "";
                }
            }
        });

        handlebars.registerHelper(Handlebars.HELPER_MISSING, new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) throws IOException {
                throw new RuntimeException("Failed to find a match for: " + options.fn.text());
            }
        });

        return handlebars;
    }
}
