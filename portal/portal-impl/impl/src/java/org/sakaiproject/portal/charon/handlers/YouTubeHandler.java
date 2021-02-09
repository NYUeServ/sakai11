package org.sakaiproject.portal.charon.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.tool.api.Session;

public class YouTubeHandler extends BasePortalHandler
{
	private static final Log log = LogFactory.getLog(YouTubeHandler.class);
	private static final String URL_FRAGMENT = "youtube";

	public YouTubeHandler()
	{
		setUrlFragment("youtube");
	}

	public int doPost(String[] parts, HttpServletRequest req, HttpServletResponse res, Session session)
	throws PortalHandlerException
	{
		return NEXT;
	}

	public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res, Session session)
	throws PortalHandlerException
	{
		if ((parts.length == 2) && (parts[1].equals("youtube")))
		{
			String movie = req.getParameter("movie");

			if (movie == null) {
				res.setStatus(400);
				return END;
			}

			String oembedURL = HotReloadConfigurationService.getString("youtube.oembed.url", "https://www.youtube.com/oembed?format=json&scheme=https&iframe=1&maxwidth=480&maxheight=360&url=");

			try {
				movie = URLEncoder.encode(movie, "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
			}
			oembedURL = oembedURL + movie;
			try
			{
				InputStream response = (InputStream)new URL(oembedURL).getContent();

				res.setStatus(200);

				OutputStream out = res.getOutputStream();
				byte[] buff = new byte[4096];
				int i;
				while ((i = response.read(buff)) > 0)
					out.write(buff, 0, i);
			}
			catch (MalformedURLException e) {
				res.setStatus(400);
			} catch (IOException e) {
				res.setStatus(500);
			}

			return END;
		}

		return NEXT;
	}
}