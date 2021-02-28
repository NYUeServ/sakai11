package org.sakaiproject.component.cover;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
   NYU modification: provide support for reading sakai.properties at runtime

 */
public class HotReloadConfigurationService
{
    private static Log LOG = LogFactory.getLog(HotReloadConfigurationService.class);

    // Recheck sakai.properties this often
    private static long MAX_STALE_MS = 60000;

    // The time we last checked sakai.properties
    private static AtomicLong lastCheckTime = new AtomicLong(0);

    // The time we last loaded sakai.properties
    private static AtomicLong lastRefreshTime = new AtomicLong(0);

    // The modification time on sakai.properties at the time we last loaded it.
    private static AtomicLong lastSeenFileMtime = new AtomicLong(0);

    // True if we're refreshing right now.
    private static AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // The actual properties.  Trimmed of whitespace and ready to go.
    private static AtomicReference<HashMap<Object, Object>> _properties = new AtomicReference(false);

    // Special properties that should be hot reloaded no matter what.  Refreshed
    // whenever sakai.properties is reloaded.
    private static AtomicReference<List<String>> hotReloadOverrideProperties = new AtomicReference(false);

    static {
        refreshProperties();
    }


    public static List<String> getHotReloadOverrideProperties() {
        maybeRefreshProperties();

        return hotReloadOverrideProperties.get();
    }

    // Unconditional refresh
    private static void refreshProperties() {
        long now = System.currentTimeMillis();

        File sakaiProperties = new File(ServerConfigurationService.getSakaiHomePath() + "/sakai.properties");
        long currentLastModifiedTime = sakaiProperties.lastModified();

        LOG.info("Reloading properties file: " + sakaiProperties);

        FileInputStream input = null;

        try {
            input = new FileInputStream(sakaiProperties);
            Properties newProperties = new Properties();
            newProperties.load(input);

            HashMap<Object, Object> propertiesMap = new HashMap<>();
            for (Map.Entry entry : newProperties.entrySet()) {
                propertiesMap.put(((String)entry.getKey()).trim(), ((String)entry.getValue()).trim());
            }

            _properties.set(propertiesMap);

            String hotReloadOverrideStr = (String)_properties.get().getOrDefault("nyu.hot-reloadable-properties", null);

            if (hotReloadOverrideStr == null || "".equals(hotReloadOverrideStr)) {
                hotReloadOverrideProperties.set(Collections.<String>emptyList());
            } else {
                hotReloadOverrideProperties.set(Arrays.asList(hotReloadOverrideStr.split(" *, *")));
            }

            lastRefreshTime.set(now);
            lastSeenFileMtime.set(currentLastModifiedTime);
        } catch (IOException e) {
            LOG.error("Exception during properties load: " + e);
            e.printStackTrace();

            if (input != null) {
                try {
                    input.close();
                } catch (IOException e2) {
                    LOG.error("Nested exception during close: " + e2);
                    e2.printStackTrace();
                }
            }
        }
    }

    private static void maybeRefreshProperties() {
        long now = System.currentTimeMillis();

        if ((now - lastCheckTime.get()) < MAX_STALE_MS) {
            // No refresh needed
            return;
        }

        if (refreshInProgress.compareAndSet(false, true)) {
            try {
                File sakaiProperties = new File(ServerConfigurationService.getSakaiHomePath() + "/sakai.properties");
                long currentLastModifiedTime = sakaiProperties.lastModified();

                if (lastSeenFileMtime.get() == currentLastModifiedTime) {
                    // No refresh needed -- file unchanged
                    lastCheckTime.set(now);
                    return;
                }

                // File was changed and we need to refresh
                refreshProperties();
            } finally {
                refreshInProgress.set(false);
            }
        }
    }


    public static String getString(String value, String defaultValue) {
        maybeRefreshProperties();

        String result = (String)_properties.get().get(value);

        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }
}
