/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.admin.remote;

import com.sun.enterprise.admin.util.AuthenticationInfo;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.SecureAdmin;
import com.sun.enterprise.config.serverbeans.SecureAdminInternalUser;
import com.sun.enterprise.security.ssl.SSLUtils;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Invocation;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.security.common.MasterPassword;

/**
 * RemoteRestAdminCommand which is sent from a server (DAS or instance).
 * <p>
 * This class identifies the origin as a server (as opposed to a true
 * admin client) for server-to-server authentication.
 *
 * @author Tim Quinn
 */
public class ServerRemoteRestAdminCommand extends RemoteRestAdminCommand {

    private final static String SSL_SOCKET_PROTOCOL = "TLS";

    private ServiceLocator habitat;

    private SecureAdmin secureAdmin;

    private ServerEnvironment serverEnv;

    private SSLUtils _sslUtils = null;
    
    private MasterPassword masterPasswordHelper = null;

    public ServerRemoteRestAdminCommand(ServiceLocator habitat, String name, String host, int port,
            boolean secure, String user, String password, Logger logger)
            throws CommandException {
        super(name, host, port, secure, "admin", "", logger);
        super.setOmitCache(true); //todo: [mmar] Remove after implementation CLI->ReST done
        completeInit(habitat);
    }

    private synchronized void completeInit(final ServiceLocator habitat) {
        this.habitat = habitat;
        final Domain domain = habitat.getService(Domain.class);
        secureAdmin = domain.getSecureAdmin();
        serverEnv = habitat.getService(ServerEnvironment.class);
        this.secure = SecureAdmin.Util.isEnabled(secureAdmin);
        setInteractive(false);
    }

    @Override
    protected synchronized SSLContext getSslContext() {
        /*
         * Always use secure communication to another server process.
         * Return a connector address that uses a cert to authenticate this
         * process as a client only if a cert, rather than an admin username 
         * and password, is used for process-to-process authentication.
         */
        try {
            final String certAlias = SecureAdmin.Util.isUsingUsernamePasswordAuth(secureAdmin) ?
                    null : getCertAlias();
            return sslUtils().getAdminSSLContext(certAlias, SSL_SOCKET_PROTOCOL);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected synchronized AuthenticationInfo authenticationInfo() {
        AuthenticationInfo result = null;
        if (SecureAdmin.Util.isUsingUsernamePasswordAuth(secureAdmin)) {
            final SecureAdminInternalUser secureAdminInternalUser = SecureAdmin.Util.secureAdminInternalUser(secureAdmin);
            if (secureAdminInternalUser != null) {
                try {
                    result = new AuthenticationInfo(secureAdminInternalUser.getUsername(), 
                            masterPassword().getMasterPasswordAdapter().
                                getPasswordForAlias(secureAdminInternalUser.getPasswordAlias()));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return result;
    }
    
    /**
     * Adds the admin indicator header to the request so. Do this whether 
     * secure admin is enabled or not, because the indicator is unique among
     * domains to help make sure only processes in the same domain talk to 
     * each other.
     *
     * @param urlConnection
     */
    @Override
    protected synchronized Invocation.Builder addAdditionalHeaders(final Invocation.Builder request) {
        final String indicatorValue = SecureAdmin.Util.configuredAdminIndicator(secureAdmin);
        if (indicatorValue != null) {
            return request.header(SecureAdmin.Util.ADMIN_INDICATOR_HEADER_NAME, indicatorValue);
        }
        return request;
    }

    private synchronized String getCertAlias() {
        return (serverEnv.isDas() ? SecureAdmin.Util.DASAlias(secureAdmin) :
            SecureAdmin.Util.instanceAlias(secureAdmin));
    }

    private synchronized SSLUtils sslUtils() {
        if (_sslUtils == null) {
            _sslUtils = habitat.getService(SSLUtils.class);
        }
        return _sslUtils;
    }
    
    private synchronized MasterPassword masterPassword() {
        if (masterPasswordHelper == null) {
            masterPasswordHelper = habitat.getService(MasterPassword.class);
        }
        return masterPasswordHelper;
    }
}
