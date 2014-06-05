/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.HandshakeRequest;

import org.apache.tomcat.util.res.StringManager;

/**
 * Represents the request that this session was opened under.
 */
public class WsHandshakeRequest implements HandshakeRequest {

    private static final StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);

    private final URI requestUri;
    private final Map<String,List<String>> parameterMap;
    private final String queryString;
    private final Principal userPrincipal;
    private final Map<String,List<String>> headers;
    private final Object httpSession;

    private volatile HttpServletRequest request;


    public WsHandshakeRequest(HttpServletRequest request) {

        this.request = request;

        queryString = request.getQueryString();
        userPrincipal = request.getUserPrincipal();
        httpSession = request.getSession(false);

        // URI
        // Based on request.getRequestURL() implementation
        StringBuilder sb = new StringBuilder();
        String scheme = request.getScheme();
        int port = request.getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        if (scheme.equals("http")) {
            sb.append("ws");
        } else if (scheme.equals("https")) {
            sb.append("wss");
        } else {
            throw new IllegalArgumentException(
                    sm.getString("wsHandshakeRequest.unknownScheme", scheme));
        }
        sb.append("://");
        sb.append(request.getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            sb.append(':');
            sb.append(port);
        }
        sb.append(request.getRequestURI());
        try {
            requestUri = new URI(sb.toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        // ParameterMap
        Map<String,String[]> originalParameters = request.getParameterMap();
        Map<String,List<String>> newParameters =
                new HashMap<>(originalParameters.size());
        for (Entry<String,String[]> entry : originalParameters.entrySet()) {
            newParameters.put(entry.getKey(),
                    Collections.unmodifiableList(
                            Arrays.asList(entry.getValue())));
        }
        parameterMap = Collections.unmodifiableMap(newParameters);

        // Headers
        Map<String,List<String>> newHeaders = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            newHeaders.put(headerName, Collections.unmodifiableList(
                    Collections.list(request.getHeaders(headerName))));
        }

        headers = Collections.unmodifiableMap(newHeaders);
    }

    @Override
    public URI getRequestURI() {
        return requestUri;
    }

    @Override
    public Map<String,List<String>> getParameterMap() {
        return parameterMap;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public Map<String,List<String>> getHeaders() {
        return headers;
    }

    @Override
    public boolean isUserInRole(String role) {
        if (request == null) {
            throw new IllegalStateException();
        }

        return request.isUserInRole(role);
    }

    @Override
    public Object getHttpSession() {
        return httpSession;
    }

    /**
     * Called when the HandshakeRequest is no longer required. Since an instance
     * of this class retains a reference to the current HttpServletRequest that
     * reference needs to be cleared as the HttpServletRequest may be reused.
     *
     * There is no reason for instances of this class to be accessed once the
     * handshake has been completed.
     */
    void finished() {
        request = null;
    }
}
