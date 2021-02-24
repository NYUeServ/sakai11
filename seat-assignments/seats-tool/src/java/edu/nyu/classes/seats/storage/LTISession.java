package edu.nyu.classes.seats.storage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;

public class LTISession {
    private static final ThreadLocal<String> activeSession = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> activeSessionStorage = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> activeSessionChanged = new ThreadLocal<>();

    private static final String SESSION_MTIME = "__session_mtime";

    public static String setCurrentSession(String sessionIdOrNull) {
        String sessionId = sessionIdOrNull;

        Map<String, Object> session = null;

        if (sessionId != null) {
            // Session should exist in the DB already
            session = loadSession(sessionId);

            if (!session.containsKey(SESSION_MTIME)) {
                // No dice.  Have a new one.
                sessionId = null;
                session = null;
            }
        }

        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            session = loadSession(sessionId);
        }

        activeSession.set(sessionId);
        activeSessionStorage.set(session);
        activeSessionChanged.set(false);

        return sessionId;
    }

    private static Object mapValueToType(String value, String typeName) {
        if ("string".equals(typeName)) {
            if (value == null) {
                return "";
            } else {
                return value;
            }
        } else if ("boolean".equals(typeName)) {
            return "true".equals(value);
        } else if ("long".equals(typeName)) {
            return Long.valueOf(value);
        } else {
            throw new RuntimeException("Unknown type: " + typeName);
        }
    }

    private static String typeNameForValue(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Long || value instanceof Integer) {
            return "long";
        } else {
            throw new RuntimeException("No typeName for: " + value);
        }
    }


    private static Map<String, Object> loadSession(String sessionId) {
        final Map<String, Object> result = new HashMap<>();

        DB.transaction
            ("Load session " + sessionId,
             (DBConnection db) -> {
                db.run("select key, value, type, mtime from seat_lti_session where session_id = ?")
                    .param(sessionId)
                    .executeQuery()
                    .each((row) -> {
                            result.put(row.getString("key"),
                                       mapValueToType(row.getString("value"), row.getString("type")));
                            result.put(SESSION_MTIME, row.getLong("mtime"));
                        });

                return null;
            });

        return result;
    }

    public static long activeSessionAge() {
        if (activeSession.get() == null) {
            throw new RuntimeException("No active session");
        }

        Map<String, Object> storage = activeSessionStorage.get();

        long now = System.currentTimeMillis();

        return now - (Long)storage.getOrDefault(SESSION_MTIME, now);
    }


    public static void expireOldSessions(long maxAgeMs) {
        DB.transaction
            ("Expire old Seating tool LTI sessions",
             (DBConnection db) -> {
                db.run("delete from seat_lti_session where mtime < ?")
                    .param(maxAgeMs)
                    .executeUpdate();

                db.commit();

                return null;
            });
    }

    public static String getString(String key, String defaultValue) {
        if (activeSession.get() == null) {
            throw new RuntimeException("No active session");
        }

        String sessionId = activeSession.get();
        Map<String, Object> storage = activeSessionStorage.get();

        return (String)storage.getOrDefault(key, defaultValue);
    }

    public static void put(String key, Object value) {
        if (activeSession.get() == null) {
            throw new RuntimeException("No active session");
        }

        if (value == null) {
            return;
        }

        activeSessionChanged.set(true);

        String sessionId = activeSession.get();
        Map<String, Object> storage = activeSessionStorage.get();

        storage.put(key, value);
    }

    public static boolean isActiveSessionChanged() {
        return activeSessionChanged.get();
    }

    public static void clearActiveSession() {
        activeSession.set(null);
        activeSessionStorage.set(null);
        activeSessionChanged.set(false);
    }

    public static void writeSession(String sessionId) {
        Map<String, Object> storage = activeSessionStorage.get();

        try {
            DB.transaction
                ("Write " + sessionId,
                 (DBConnection db) -> {
                    db.run("delete from seat_lti_session where session_id = ?")
                        .param(sessionId)
                        .executeUpdate();

                    long now = System.currentTimeMillis();

                    for (Map.Entry<String, Object> e : storage.entrySet()) {
                        if (SESSION_MTIME.equals(e.getKey())) {
                            continue;
                        }

                        db.run("insert into seat_lti_session (session_id, key, value, type, mtime) values (?, ?, ?, ?, ?)")
                            .param(sessionId)
                            .param(e.getKey())
                            .param(String.valueOf(e.getValue()))
                            .param(typeNameForValue(e.getValue()))
                            .param(now)
                            .executeUpdate();
                    }

                    db.commit();

                    return null;
                });
        } finally {
            clearActiveSession();
        }
    }

    public static boolean isActive() {
        if (activeSession.get() == null) {
            return false;
        }

        Map<String, Object> session = activeSessionStorage.get();

        return "true".equals(session.get("session_active"));
    }

    public static void populateContext(Map<String, Object> context) {
        if (activeSession.get() == null) {
            throw new RuntimeException("No active session");
        }

        Map<String, Object> session = activeSessionStorage.get();

        context.putAll(session);
        activeSessionChanged.set(true);
    }

}
