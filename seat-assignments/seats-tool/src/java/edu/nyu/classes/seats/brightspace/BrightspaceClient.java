package edu.nyu.classes.seats.brightspace;

import java.io.*;
import java.util.*;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;


import edu.nyu.classes.seats.storage.db.DBConnection;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.stream.*;




public class BrightspaceClient {

    private static final Logger LOG = LoggerFactory.getLogger(BrightspaceClient.class);

    private OAuth tokens;

    private ExecutorService requestPool;

    private static Map<String, String> apiVersions;

    static {
        apiVersions = new HashMap();
        apiVersions.put("lp", "1.25");
        apiVersions.put("le", "1.25");
    }

    public BrightspaceClient() {
        tokens = new OAuth();
        requestPool = Executors.newFixedThreadPool(32);
    }

    private String stripIdPrefix(String prefixedSiteId) {
        return prefixedSiteId.replace("brightspace:", "");
    }

    public Map<String, String> netIdsForUserIds(List<String> brightspaceUserIds) {
        Map<String, String> result = new HashMap<>();

        List<Future<HTTPResponse>> requests = brightspaceUserIds
            .stream()
            .map((userId) -> httpGet(endpoint("lp", String.format("/users/%s", userId))))
            .collect(Collectors.toList());

        for (int i = 0; i < requests.size(); i++) {
            Future<HTTPResponse> futureResponse = requests.get(i);
            String brightspaceUserId = brightspaceUserIds.get(i);

            try (HTTPResponse response = futureResponse.get()) {
                JSON userData = response.json(JSON.parse("{}"));

                result.put(brightspaceUserId, userData.path("UserName").asString(null));
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }

    public class CourseOfferingData {
        public String title;
        public boolean isPublished;
    }

    public CourseOfferingData fetchCourseData(String siteId) {
        try {
            String courseOfferingId = stripIdPrefix(siteId);

            Future<HTTPResponse> courseOfferingLookup =
                httpGet(endpoint("lp", String.format("/courses/%s", courseOfferingId)));

            try (HTTPResponse response = courseOfferingLookup.get()) {
                JSON courseData = response.json(JSON.parse("{}"));

                CourseOfferingData data = new CourseOfferingData();
                data.title = courseData.path("Name").asString("Untitled");
                data.isPublished = courseData.path("IsActive").asBoolean(false);

                return data;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public BrightspaceSectionInfo getSectionInfo(DBConnection db, String siteId) {
        try {
            String courseOfferingId = stripIdPrefix(siteId);

            List<JSON> userPages = new ArrayList<>();

            String bookmark = "";

            for (;;) {
                Future<HTTPResponse> usersRequest = httpGet(endpoint("lp", String.format("/enrollments/orgUnits/%s/users/", courseOfferingId)),
                                                            "bookmark", bookmark);

                try (HTTPResponse usersResponse = usersRequest.get()) {
                    JSON userData = usersResponse.json(JSON.parse("[]"));

                    userPages.add(userData);

                    if (userData.path("PagingInfo > HasMoreItems").asBoolean(false)) {
                        bookmark = userData.path("PagingInfo > BookMark").asStringOrDie();
                    } else {
                        break;
                    }
                }
            }

            Future<HTTPResponse> sectionsRequest = httpGet(endpoint("lp", String.format("/%s/sections/", courseOfferingId)));

            try (HTTPResponse sectionsResponse = sectionsRequest.get()) {
                JSON sectionData = sectionsResponse.json(JSON.parse("[]"));

                return new BrightspaceSectionInfo(db, this, sectionData, userPages);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private class HTTPResponse implements AutoCloseable {
        private CloseableHttpClient client;
        public CloseableHttpResponse response;
        private String requestId;
        public Throwable error;

        public HTTPResponse(CloseableHttpClient client, String requestId, CloseableHttpResponse origResponse) {
            this.client = client;
            this.requestId = requestId;
            this.response = origResponse;
        }

        public HTTPResponse(CloseableHttpClient client, String requestId, Throwable error) {
            this.client = client;
            this.requestId = requestId;
            this.error = error;
        }

        private void assertOK() {
            if (this.error != null) {
                throw new RuntimeException(this.error);
            }
        }

        public JSON json() {
            try {
                successOrDie();
                return JSON.parse(bodyString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public JSON json(JSON dflt) {
            try {
                successOrDie();

                if (response.getStatusLine().getStatusCode() == 404) {
                    // Brightspace returns 404 for "nothing here"
                    return dflt;
                }
                return JSON.parse(bodyString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void successOrDie() {
            assertOK();

            if (response.getStatusLine().getStatusCode() == 200) {
                // Hooray
                return;
            }

            String body = "";

            try {
                body = EntityUtils.toString(response.getEntity());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            throw new RuntimeException(String.format("Failed request %s: %d (%s)",
                                                     this.requestId,
                                                     response.getStatusLine().getStatusCode(),
                                                     body));
        }

        public String bodyString() throws IOException {
            assertOK();
            return EntityUtils.toString(this.response.getEntity());
        }

        public void close() {
            if (response != null) {
                try { response.close(); } catch (IOException e) {}
            }

            if (client != null) {
                try { client.close(); } catch (IOException e) {}
            }
        }
    }

    private final int MAX_TOKEN_ATTEMPTS = 3;

    private Future<HTTPResponse> httpGet(String uri, String ...params) {
        try {
            URIBuilder builder = new URIBuilder(uri);

            for (int i = 0; i < params.length; i += 2) {
                builder.setParameter(params[i], params[i + 1]);
            }

            HttpGet request = new HttpGet(builder.build());

            return httpRequest("GET " + uri, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Future<HTTPResponse> httpRequest(String request_id, HttpRequestBase req) {
        return this.requestPool.submit(() -> {
                CloseableHttpClient client = null;

                try {
                    for (int attempt = 0; ; attempt++) {
                        if (attempt > 0) {
                            try {
                                LOG.info("Retrying request");
                                Thread.sleep((long)Math.floor(1000 + (5000 * Math.random())));
                            } catch (InterruptedException ie) {}
                        }

                        RequestConfig config = RequestConfig
                            .custom()
                            .setConnectTimeout(60000)
                            .setConnectionRequestTimeout(60000)
                            .setSocketTimeout(60000)
                            .build();

                        client = HttpClients
                            .custom()
                            .setDefaultRequestConfig(config)
                            .build();

                        String accessToken = this.tokens.accessToken();

                        req.addHeader("Authorization", String.format("Bearer %s", accessToken));

                        CloseableHttpResponse response = client.execute(req);

                        int code = response.getStatusLine().getStatusCode();
                        if ((attempt + 1) < MAX_TOKEN_ATTEMPTS && (code == 401 || code == 403)) {
                            // Bad token.  Probably expired?  Force a refresh if we have attempts left.
                            this.tokens.accessToken(accessToken);
                        } else {
                            return new HTTPResponse(client, request_id, response);
                        }
                    }
                } catch (Exception e) {
                    return new HTTPResponse(client, request_id, e);
                }
            });
    }

    private String endpoint(String product, String uri) {
        // FIXME: default to production
        String baseURL = HotReloadConfigurationService.getString("seats.brightspace_api_url", "https://nyutest.brightspace.com/d2l/api").replace("/+", "");

        if (product.isEmpty()) {
            return String.format("%s%s", baseURL, uri);
        } else {
            String productVersion = apiVersions.get(product);

            return String.format("%s/%s/%s%s",
                                 baseURL,
                                 product,
                                 productVersion,
                                 uri);
        }
    }
}
