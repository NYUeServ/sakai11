// This was originally written as an Axis web service, but Axis 1.4 is fairly
// long in the tooth these days and doesn't work (reliably) under Java 1.8.
//
// The original plan was to migrate to CXF, but the systems that consume this
// service are somewhat particular about how the XML looks--wanting different
// SoapAction and URI prefix in different environments and that sort of thing.
//
// Rather than risk regressions stemming from incompatibilities between SOAP
// libraries I've just taken the small number of requests and responses we care
// about and produced templates based on the responses from the original
// implementation.

package edu.nyu.classes.nyugrades.ws;

import edu.nyu.classes.nyugrades.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Locale;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.regex.Pattern;
import java.security.MessageDigest;

public class NYUGradesWS extends HttpServlet
{
    String GRADES_ADMIN_USER = "admin";

    private static final Log LOG = LogFactory.getLog(NYUGradesWS.class);

    private UserDirectoryService userDirectoryService;
    private ServerConfigurationService serverConfigurationService;
    private SessionManager sakaiSessionManager;
    private NYUGradesSessionService nyuGradesSessions;
    private NYUGradesService nyuGrades;


    private String[] permittedUsernames;


    public NYUGradesWS()
    {
        serverConfigurationService = (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class.getName());
        userDirectoryService = (UserDirectoryService) ComponentManager.get(UserDirectoryService.class.getName());
        sakaiSessionManager = (SessionManager) ComponentManager.get(SessionManager.class.getName());

        nyuGradesSessions = (NYUGradesSessionService) ComponentManager.get("edu.nyu.classes.nyugrades.api.NYUGradesSessionService");
        nyuGrades = (NYUGradesService) ComponentManager.get("edu.nyu.classes.nyugrades.api.NYUGradesService");

        permittedUsernames = HotReloadConfigurationService.getString("nyu.grades-service.allowed_users", "admin").split(", *");
    }


    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        new RequestDispatcher(request, response).dispatch();
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        new RequestDispatcher(request, response).dispatch();
    }

    private class RequestDispatcher {
        private HttpServletRequest request;
        private HttpServletResponse response;

        public RequestDispatcher(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }

        private void reject() {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

        private void dispatch() throws ServletException, IOException {
            String allowedPatterns = HotReloadConfigurationService.getString("webservices.allow", null);
            String remoteIP = request.getRemoteAddr();

            if (allowedPatterns == null) {
                reject();
                return;
            }

            boolean matched = false;
            for (String pattern : allowedPatterns.split(" *, *")) {
                Pattern expr = Pattern.compile(pattern.replace("\\\\", "\\"));

                if (expr.matcher(remoteIP).matches()) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                reject();
                return;
            }


            // Served the WSDL if requested
            if (request.getQueryString() != null && request.getQueryString().toLowerCase().indexOf("wsdl") >= 0) {
                try {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setHeader("Content-Type", "text/xml;charset=utf-8");
                    respondWithTemplate("wsdl", new String[] { "REQUEST_SUFFIX", request.getRequestURI().endsWith("Production.jws") ? "Production" : "" });
                } catch (Exception e) {
                    LOG.error("Error while serving WSDL: " + e);
                    e.printStackTrace();
                }

                return;
            }

            String action = getSoapAction();

            if (action == null) {
                throw new ServletException("You must provide a SoapAction header");
            }

            try {
                SOAPRequest soapRequest = new SOAPRequest(request);

                // We'll use startsWith here because SIS requests "loginProduction" in
                // production environment, but just "login" in others.
                if (action.startsWith("login")) {
                    String sessionId = login(soapRequest.get("username"), soapRequest.get("password"));

                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setHeader("Content-Type", "text/xml;charset=utf-8");
                    respondWithTemplate("login_response", new String[] { "SESSION", StringEscapeUtils.escapeXml(sessionId) });
                } else if (action.startsWith("logout")) {
                    String status = logout(soapRequest.get("sessionId"));

                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setHeader("Content-Type", "text/xml;charset=utf-8");
                    respondWithTemplate("logout_response", new String[] { "STATUS", StringEscapeUtils.escapeXml(status) });
                } else if (action.startsWith("getGradesForSite")) {
                    GradeSet grades = getGradesForSite(soapRequest.get("sessionId"),
                            soapRequest.get("courseId"),
                            soapRequest.get("term"),
                            soapRequest.get("sessionCode"),
                            soapRequest.get("classSection"));

                    respondWithGrades(grades);
                } else {
                    throw new ServletException("Unrecognized SoapAction header: " + action);
                }
            } catch (Exception e) {
                LOG.error(e);
                e.printStackTrace();

                try {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    respondWithTemplate("error_response", new String[] { "ERROR_MESSAGE", StringEscapeUtils.escapeXml(e.getMessage()) });
                } catch (Exception e2) {
                    LOG.error("Additionally, failed to write an error response with exception: " + e2);
                    e2.printStackTrace();
                }
            }
        }

        private String getSoapAction() {
            String action = request.getHeader("SoapAction");

            if (action == null) {
                return null;
            } else {
                return action.replace("\"", "");
            }
        }

        private void respondWithGrades(GradeSet grades) throws Exception {
            StringBuilder gradeString = new StringBuilder();

            for (Grade g : grades) {
                gradeString.append(fillTemplate("single_grade", new String[] {
                            "NETID", StringEscapeUtils.escapeXml(g.netId),
                            "NETID_TYPE", soapTypeFor(g.netId),
                            "EMPLID", StringEscapeUtils.escapeXml(g.emplId),
                            "EMPLID_TYPE", soapTypeFor(g.emplId),
                            "GRADELETTER", StringEscapeUtils.escapeXml(g.gradeletter),
                            "GRADELETTER_TYPE", soapTypeFor(g.gradeletter)
                        }));
            }

            String result = fillTemplate("grades_response", new String[] {});

            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("Content-Type", "text/xml;charset=utf-8");

            respondWithString(result.replace("{{LIST_OF_GRADES}}", gradeString.toString()));
        }

        private String soapTypeFor(String value) {
            return (value == null ? "xsi:nil=\"true\"" : "xsi:type=\"soapenc:string\"");
        }

        private String fillTemplate(String templateName, String[] keysAndValues) throws Exception {
            URL templateResource = this.getClass().getResource("/edu/nyu/classes/nyugrades/ws/response_templates/" + templateName + ".xml");

            if (templateResource == null) {
                throw new ServletException("Internal error: failed to load template for: " + templateName);
            }

            String templateContent = new String(Files.readAllBytes(Paths.get(templateResource.toURI())),
                    "UTF-8").trim();

            if ((keysAndValues.length % 2) != 0) {
                throw new ServletException("Internal error: keysAndValues should have an even number of elements.");
            }

            for (int i = 0; i < keysAndValues.length; i += 2) {
                String key = keysAndValues[i];
                String value = keysAndValues[i + 1];
                templateContent = templateContent.replace("{{" + key + "}}",
                        (value == null) ? "" : value);
            }

            return templateContent;
        }

        private void respondWithTemplate(String template, String[] keysAndValues) throws Exception {
            String templateContent = fillTemplate(template, keysAndValues);

            respondWithString(templateContent);
        }

        private void respondWithString(String result) throws Exception {
            // Some special values
            result = result.replace("{{BASE_URL}}", serverConfigurationService.getServerUrl() + request.getRequestURI());
            result = result.replace("{{SERVER_HOST}}", serverConfigurationService.getServerName());
            if (getSoapAction() != null) {
                result = result.replace("{{SOAP_ACTION}}", getSoapAction());
            }

            if ("true".equals(HotReloadConfigurationService.getString("nyugrades.log-requests", "false"))) {
                try {
                    LOG.info("Responding with XML document: " + result);
                    LOG.info("Base64 response: " + base64(result));
                } catch (Exception ex) {}
            }

            response.getWriter().write(result);
        }

        private String base64(String source) throws Exception {
            return Base64.getEncoder().encodeToString(source.getBytes("UTF-8"));
        }

        private boolean passwordValid(String username, String password)
        {
            if (!serverConfigurationService.getServerId().startsWith("hercules")) {
                String testUsername = HotReloadConfigurationService.getString("nyugrades.test-account.username", null);
                String testPassword = HotReloadConfigurationService.getString("nyugrades.test-account.password", null);

                if (testUsername != null && testPassword != null) {
                    if (secureEquals(testUsername, username) && secureEquals(testPassword, password)) {
                        return true;
                    }
                }
            }

            return (userDirectoryService.authenticate(username, password) != null);
        }


        private boolean secureEquals(String s1, String s2) {
            try {
                byte[] d1 = MessageDigest.getInstance("SHA-256").digest(s1.getBytes("UTF-8"));
                byte[] d2 = MessageDigest.getInstance("SHA-256").digest(s2.getBytes("UTF-8"));

                return MessageDigest.isEqual(d1, d2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


        private boolean usernameIsPermitted(String username)
        {
            for (String permittedUsername : permittedUsernames) {
                if (permittedUsername.equalsIgnoreCase(username)) {
                    return true;
                }
            }

            return false;
        }


        private String login(String username, String password) throws RequestFailedException
        {
            if (!passwordValid(username, password) || !usernameIsPermitted(username)) {
                LOG.warn("Rejected request from " + username);
                throw new RequestFailedException("Permission denied");
            }

            nyuGradesSessions.expireSessions();
            return nyuGradesSessions.createSession(username);
        }


        private String logout(String sessionId) throws RequestFailedException
        {
            nyuGradesSessions.deleteSession(sessionId);

            return "OK";
        }


        private GradeSet getGradesForSite(String sessionId,
                                          String courseId,
                                          String term,
                                          String sessionCode,
                                          String classSection)
            throws RequestFailedException, AuditLogException
        {
            if (!nyuGradesSessions.checkSession(sessionId)) {
                LOG.warn("Rejected invalid sessionId");
                throw new RequestFailedException("Permission denied");
            }

            Session sakaiSession = sakaiSessionManager.startSession();
            try {
                sakaiSessionManager.setCurrentSession(sakaiSession);

                sakaiSession.setUserId(GRADES_ADMIN_USER);
                sakaiSession.setUserEid(GRADES_ADMIN_USER);

                String sectionEid = null;
                try {
                    sectionEid = nyuGrades.findSingleSection(courseId, term, sessionCode, classSection);
                    return nyuGrades.getGradesForSection(sectionEid);
                } catch (SectionNotFoundException e) {
                    throw new RequestFailedException(String.format("Failed to find a section for CRSE_ID; STRM; SESSION_CODE; CLASS_SECTION = %s; %s; %s; %s",
                            courseId, term, sessionCode, classSection));
                } catch (SiteNotFoundForSectionException e) {
                    throw new RequestFailedException(String.format("Failed to find site for section: %s",
                            sectionEid));
                } catch (MultipleSectionsMatchedException e) {
                    throw new RequestFailedException(String.format("Multiple sections matched for CRSE_ID; STRM; SESSION_CODE; CLASS_SECTION = %s; %s; %s; %s",
                            courseId, term, sessionCode, classSection));
                } catch (MultipleSitesFoundForSectionException e) {
                    throw new RequestFailedException(String.format("Multiple sites found for section: %s",
                            sectionEid));
                } catch (GradePullDisabledException e) {
                    throw new RequestFailedException(String.format("Grade pull is currently disabled for section: %s",
                            sectionEid));
                } catch (AmbiguousGradeMappingException e) {
                    throw new RequestFailedException(e.getMessage());
                }

            } finally {
                sakaiSession.invalidate();
            }
        }
    }

    private class RequestFailedException extends Exception {
        public RequestFailedException(String message) {
            super(message);
        }
    }

    private class SOAPRequest {
        private Document doc;
        private XPath xpath;

        public SOAPRequest(HttpServletRequest request) throws Exception {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            String xmlInput = readInputStream(request.getInputStream());

            if ("true".equals(HotReloadConfigurationService.getString("nyugrades.log-requests", "false"))) {
                logRequest(request, xmlInput);
            }

            doc = builder.parse(new ByteArrayInputStream(xmlInput.getBytes("UTF-8")));
            xpath = XPathFactory.newInstance().newXPath();
        }

        public String get(String parameter) throws Exception {
            XPathExpression expr = xpath.compile("//*[translate(local-name(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')='body']/*/*");
            NodeList matches = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);

            for (int i = 0; i < matches.getLength(); i++) {
                Node node = matches.item(i);
                String nodeName = node.getNodeName();

                if (nodeName == null) {
                    continue;
                }

                if (nodeName.indexOf(":") >= 0) {
                    nodeName = nodeName.substring(nodeName.indexOf(":") + 1);
                }

                if (parameter.equalsIgnoreCase(nodeName)) {
                    return node.getTextContent();
                }
            }

            throw new RequestFailedException("Missing value for required parameter: " + parameter);
        }

        private String readInputStream(InputStream in) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int len;

            while ((len = in.read(buffer)) >= 0) {
                bytes.write(buffer, 0, len);
            }

            return bytes.toString("UTF-8");
        }

        private void logRequest(HttpServletRequest request, String xmlInput) {
            System.err.println("NYU Grades received the following request:");

            System.err.println("METHOD: " + request.getMethod());
            System.err.println("HEADERS:");

            Enumeration<String> headerNames = request.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                System.err.println(header + ": " + request.getHeader(header));
            }

            System.err.println("\n");

            System.err.println(xmlInput);

            System.err.println("================================================");
        }
    }
}
