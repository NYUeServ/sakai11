/**********************************************************************************
 *
 * Copyright (c) 2019 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.conversations.tool.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.conversations.tool.models.Poster;
import org.sakaiproject.conversations.tool.models.Topic;
import org.sakaiproject.conversations.tool.storage.ConversationsStorage;

public class IndexHandler implements Handler {

    private String redirectTo = null;

    public IndexHandler() {
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            RequestParams p = new RequestParams(request);

            String siteId = (String)context.get("siteId");

            ConversationsStorage storage = new ConversationsStorage();

//            List<Topic> topics = storage.getAllTopics(siteId);
//            List<String> topicUuids = new ArrayList<String>();
//
//            for (Topic topic : topics) {
//                topicUuids.add(topic.getUuid());
//            }
//
//            Map<String, List<Poster>> topicPosters = storage.getPostersForTopics(topicUuids);
//            Map<String, Long> postCounts = storage.getPostCountsForTopics(topicUuids);
//            Map<String, Long> lastActivityTimes = storage.getLastActivityTimeForTopics(topicUuids);
//
//            for (Topic topic : topics) {
//                if (topicPosters.containsKey(topic.getUuid())) {
//                    topic.setPosters(topicPosters.get(topic.getUuid()));
//                }
//                if (postCounts.containsKey(topic.getUuid())) {
//                    if (postCounts.get(topic.getUuid()) > 1) {
//                        topic.setPostCount(postCounts.get(topic.getUuid()));
//                    }
//                }
//                if (lastActivityTimes.containsKey(topic.getUuid())) {
//                    topic.setLastActivityTime(lastActivityTimes.get(topic.getUuid()));
//                }
//            }
//
//            context.put("topics", topics);
            
            context.put("page", 0);
            context.put("order_by", "last_activity_at");
            context.put("order_direction", "desc");
            context.put("subpage", "index");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasRedirect() {
        return (redirectTo != null);
    }

    public String getRedirect() {
        return redirectTo;
    }

    public Errors getErrors() {
        return null;
    }

    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }
}