package org.sakaiproject.conversations.tool.handlers;

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

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class RequestParams {

    private HttpServletRequest request = null;

    public RequestParams(HttpServletRequest request) {
        this.request = request;
    }

    public String getString(String param, String defaultValue) {
        String result = request.getParameter(param);

        if (result == null || "".equals(result)) {
            return defaultValue;
        } else {
            return result;
        }
    }

    public List<String> getStrings(String param) {
        String[] result = request.getParameterValues(param);

        if (result == null) {
            return new ArrayList<String>();
        } else {
            return Arrays.asList(result);
        }
    }
}