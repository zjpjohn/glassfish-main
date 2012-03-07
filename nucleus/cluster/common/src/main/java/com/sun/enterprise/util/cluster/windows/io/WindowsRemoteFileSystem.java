/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.enterprise.util.cluster.windows.io;

import com.sun.enterprise.util.cluster.windows.process.WindowsCredentials;
import java.net.*;
import jcifs.smb.NtlmPasswordAuthentication;

/**
 * Wrap the implementation details for the way we get access to remote
 * Windows files.
 * Note: null args == NPE
 * @author Byron Nevins
 */
public class WindowsRemoteFileSystem {
    private final String host;
    private final NtlmPasswordAuthentication authorization;

    public WindowsRemoteFileSystem(WindowsCredentials cr) {
        // if host and domain are the same we can use the IP address of the host
        // otherwise use the domain name.
        boolean useDomain;
        String hostName = cr.getHost();
        String domain = cr.getDomain();

        if(!domain.equals(hostName))
            useDomain=true;
        else
            useDomain = false;

        host = getIP(hostName);

        if(useDomain)
            authorization = new NtlmPasswordAuthentication(domain, cr.getUser(), cr.getPassword());
        else
            authorization = new NtlmPasswordAuthentication(host, cr.getUser(), cr.getPassword());
    }

    public WindowsRemoteFileSystem(String hostname, NtlmPasswordAuthentication auth) {
        host = getIP(hostname);
        authorization = auth;
    }

    public WindowsRemoteFileSystem(String hostname, String username, String password) {
        host = getIP(hostname);
        authorization = new NtlmPasswordAuthentication(host, username, password);
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the authorization
     */
    public NtlmPasswordAuthentication getAuthorization() {
        return authorization;
    }

    private String getIP(String hostname) {
        try {
            return InetAddress.getByName(hostname).getHostAddress();
        }
        catch (Exception e) {
            return hostname;
        }
    }
}
