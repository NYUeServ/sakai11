package edu.nyu.classes.seats.brightspace;

import edu.nyu.classes.seats.storage.db.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.apache.http.impl.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.net.URLEncoder;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OAuth {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth.class);

    private String accessToken;

    private static final String APP_ID = "seating-tool";

    private static final String AUTH_URL = "https://auth.brightspace.com/oauth2/auth";
    private static final String TOKEN_URL = "https://auth.brightspace.com/core/connect/token";
    private static final String SCOPE = "core:*:* enrollment:*:* reporting:*:* datahub:*:*";

    private static class OAuthApplication {
        public String clientId;
        public String clientSecret;

        public OAuthApplication(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }
    }

    private static class RefreshToken {
        public OAuthApplication application;
        public String refreshToken;

        public RefreshToken(OAuthApplication application, String refreshToken) {
            this.application = application;
            this.refreshToken = refreshToken;
        }
    }

    private AtomicReference<Map<String, OAuthApplication>> oauthApplicationByClientId = new AtomicReference(new HashMap<>());

    public OAuth() {
        reloadOauthClientProperties();
    }

    private void reloadOauthClientProperties() {
        Map<String, OAuthApplication> newProperties = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            String clientId = HotReloadConfigurationService.getString(String.format("seats.brightspace.oauth_client_id.%d", i), "");
            String secret = HotReloadConfigurationService.getString(String.format("seats.brightspace.oauth_client_secret.%d", i), "");

            if (!clientId.isEmpty()) {
                newProperties.put(clientId, new OAuthApplication(clientId, secret));
            }
        }

        oauthApplicationByClientId.set(newProperties);
    }

    private class DudRefreshToken extends Exception {
        public RefreshToken dudToken;

        public DudRefreshToken(RefreshToken refreshToken) {
            this.dudToken = refreshToken;
        }
    }

    private void loadTokens() {
        reloadOauthClientProperties();

        try {
            for (;;) {
                try {
                    RefreshToken refreshToken = readRefreshToken();
                    accessToken = redeemRefreshToken(refreshToken);
                    break;
                } catch (DudRefreshToken error) {
                    LOG.info("Refresh token wasn't valid.  Retrying...");

                    deleteRefreshToken(error.dudToken.refreshToken);

                    try {
                        Thread.sleep((long)Math.floor(Math.random() * 5000));
                    } catch (InterruptedException e) {}
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String accessToken() {
        return accessToken(null);
    }

    // Return an access token that isn't this one!
    public synchronized String accessToken(String knownInvalidToken) {
        if (accessToken == null || (knownInvalidToken != null && accessToken.equals(knownInvalidToken))) {
            loadTokens();
        }

        return accessToken;
    }

    // Find an application needing a refresh token generated for the first time.  Null if there isn't one.
    public String buildAuthRedirectUrl(String redirectUrl) {
        try {
            List<String> happyClientIds = listClientIdsWithRefreshTokens();

            for (String clientId : oauthApplicationByClientId.get().keySet()) {
                if (happyClientIds.contains(clientId)) {
                    continue;
                }

                return AUTH_URL + String.format("?response_type=code&client_id=%s&state=%s&redirect_uri=%s&scope=%s",
                                                clientId,
                                                clientId,
                                                URLEncoder.encode(redirectUrl, "UTF-8"),
                                                URLEncoder.encode(SCOPE, "UTF-8"));
            }

            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String redeemRefreshToken(RefreshToken refreshToken) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost req = new HttpPost(TOKEN_URL);
            req.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("grant_type", "refresh_token"),
                                                                 new BasicNameValuePair("refresh_token", refreshToken.refreshToken),
                                                                 new BasicNameValuePair("scope", SCOPE),
                                                                 new BasicNameValuePair("client_id", refreshToken.application.clientId),
                                                                 new BasicNameValuePair("client_secret", refreshToken.application.clientSecret))));

            try (CloseableHttpResponse response = client.execute(req)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    // OK
                    JSON tokens = JSON.parse(EntityUtils.toString(response.getEntity()));
                    String newRefreshToken = tokens.path("refresh_token").asString(null);

                    if (newRefreshToken != null) {
                        storeRefreshToken(new RefreshToken(refreshToken.application, newRefreshToken));
                    }

                    return tokens.path("access_token").asStringOrDie();
                } else {
                    if (response.getStatusLine().getStatusCode() == 400) {
                        JSON error = JSON.parse(EntityUtils.toString(response.getEntity()));

                        if ("invalid_grant".equals(error.path("error").asString(""))) {
                            throw new DudRefreshToken(refreshToken);
                        }
                    }

                    throw new RuntimeException(String.format("Failed redeeming refresh token: %d (%s)",
                                                             response.getStatusLine().getStatusCode(),
                                                             EntityUtils.toString(response.getEntity())));
                }
            }
        }
    }

    public String redeemAuthCode(String clientId, String authCode, String redirectUri) {
        OAuthApplication app = oauthApplicationByClientId.get().get(clientId);

        if (app == null) {
            throw new RuntimeException("Client ID not recognized: " + clientId);
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost req = new HttpPost(TOKEN_URL);
            req.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("grant_type", "authorization_code"),
                                                                 new BasicNameValuePair("code", authCode),
                                                                 new BasicNameValuePair("scope", SCOPE),
                                                                 new BasicNameValuePair("redirect_uri", redirectUri),
                                                                 new BasicNameValuePair("client_id", clientId),
                                                                 new BasicNameValuePair("client_secret", app.clientSecret))));

            try (CloseableHttpResponse response = client.execute(req)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    // OK
                    JSON tokens = JSON.parse(EntityUtils.toString(response.getEntity()));
                    String newRefreshToken = tokens.path("refresh_token").asString(null);

                    if (app != null && newRefreshToken != null) {
                        storeRefreshToken(new RefreshToken(app, newRefreshToken));
                    }

                    return tokens.path("access_token").asStringOrDie();
                } else {
                    throw new RuntimeException(String.format("Failed redeeming auth code: %d (%s)",
                                                             response.getStatusLine().getStatusCode(),
                                                             EntityUtils.toString(response.getEntity())));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RefreshToken readRefreshToken() {
        return DB.transaction
            ("Get an available refresh token",
             (DBConnection db) -> {
                AtomicReference<RefreshToken> result = new AtomicReference<>(null);

                db.run("SELECT client_id, refresh_token from nyu_t_brightspace_oauth where system = ? order by client_id")
                    .param(APP_ID)
                    .executeQuery()
                    .each((row) -> {
                            if (result.get() == null) {
                                OAuthApplication app = oauthApplicationByClientId.get().get(row.getString("client_id"));

                                if (app != null) {
                                    result.set(new RefreshToken(app, row.getString("refresh_token")));
                                }
                            }
                        });

                if (result.get() != null) {
                    return result.get();
                } else {
                    throw new RuntimeException("Failed to find an active refresh token for system: " + APP_ID);
                }
            });
    }

    private void storeRefreshToken(RefreshToken newToken) {
        DB.transaction
            ("Write the latest refresh token",
             (DBConnection db) -> {
                db.run("delete from nyu_t_brightspace_oauth where client_id = ?")
                    .param(newToken.application.clientId)
                    .executeUpdate();

                db.run("insert into nyu_t_brightspace_oauth (client_id, refresh_token) values (?, ?)")
                    .param(newToken.application.clientId)
                    .param(newToken.refreshToken)
                    .executeUpdate();

                db.commit();

                return null;
            });
    }

    private void deleteRefreshToken(String dudToken) {
        DB.transaction
            ("Delete an invalidated refresh token",
             (DBConnection db) -> {
                db.run("delete from nyu_t_brightspace_oauth where refresh_token = ?")
                    .param(dudToken)
                    .executeUpdate();

                db.commit();

                return null;
            });
    }

    private List<String> listClientIdsWithRefreshTokens() {
        return DB.transaction
            ("List clients with refresh tokens",
             (DBConnection db) -> {
                RefreshToken result = null;

                return db.run("SELECT client_id from nyu_t_brightspace_oauth where system = ?")
                    .param(APP_ID)
                    .executeQuery()
                    .getStringColumn("client_id");
            });
    }

}
