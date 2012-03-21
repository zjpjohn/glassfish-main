/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.gms.admin;

import com.sun.logging.LogDomains;
import org.glassfish.gms.bootstrap.GMSAdapterService;
import org.glassfish.gms.bootstrap.GMSAdapter;
import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.ee.cms.core.GMSConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import javax.inject.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.PerLookup;

import java.util.*;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.ee.cms.core.GroupManagementService;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


@Service(name = "_gms-announce-before-start-cluster-command")
@Supplemental(value = "start-cluster", on = Supplemental.Timing.Before, ifFailure = FailurePolicy.Warn)
@Scoped(PerLookup.class)
@RestEndpoints({
    @RestEndpoint(configBean=Domain.class,
        opType=RestEndpoint.OpType.POST, 
        path="_gms-announce-before-start-cluster-command", 
        description="_gms-announce-before-start-cluster-command")
})
public class GMSAnnounceBeforeStartClusterCommand implements AdminCommand {

    private static final Logger logger = LogDomains.getLogger(
        GMSAnnounceBeforeStartClusterCommand.class, LogDomains.GMS_LOGGER);

    @Inject
    private ServerEnvironment env;
    @Inject
    private Habitat habitat;
    @Param(optional = false, primary = true)
    private String clusterName;
    @Inject
    private Domain domain;
    @Inject
    private CommandRunner runner;
    @Param(optional = true, defaultValue = "false")
    private boolean verbose;

    @Inject
    GMSAdapterService gmsAdapterService;

    private GroupManagementService gms = null;
    private boolean gmsStartCluster = false;
    private List<String> clusterMembers = EMPTY_LIST;
    private GMSAdapter gmsadapter = null;

    static final private List<String> EMPTY_LIST = new LinkedList<String>();


    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        try {
            if (gmsAdapterService.isGmsEnabled()) {
                gmsadapter = gmsAdapterService.getGMSAdapterByName(clusterName);

                //  gmsadapter can be null if GMSEnabled=false for clusterName.
                if (gmsadapter != null) {
                    gms = gmsadapter.getModule();
                    if (gms != null) {
                        clusterMembers = getClusterMembers();

                        // no need to announce a zero instance cluster.
                        if (clusterMembers != null && clusterMembers.size() > 0) {

                            // one or more clustered instances for this cluster in domain.xml.
                            // now check if any clustered instance are already running.
                            // DAS is a SPECTATOR so it will not be in list of current core members.
                            // If one or more clustered instances is already running,  do not consider this a GROUP_STARTUP,
                            // but treat as a series of individual INSTANCE_STARTUP for instances that do get started.
                            // no gms calls needed if not a GROUP_STARTUP.
                            List<String> startedGMSMembers = gms.getGroupHandle().getCurrentCoreMembers();
                            if (startedGMSMembers.size() == 0) {
                                try {
                                    // must be called on DAS only.
                                    gms.announceGroupStartup(clusterName, GMSConstants.groupStartupState.INITIATED, clusterMembers);
                                    gmsStartCluster = true;
                                } catch (Throwable t) {

                                    // ensure gms group startup announcement does not interfere with starting cluster.
                                    // any exception here should not interfere with starting cluster.
                                    logger.log(Level.WARNING,
                                        "cluster.start.exception",
                                        t.getLocalizedMessage());
                                }
                            } // else from GMS perspective treat remaining instances getting started as INSTANCE_START, not GROUP_START.
                            // nothing gms specific to do for this case.
                        }
                    }
                }
            }
        } finally {
            if (gms != null) {
                GMSAnnounceSupplementalInfo result = new GMSAnnounceSupplementalInfo(clusterMembers, gmsStartCluster, gmsadapter);
                report.setResultType(GMSAnnounceSupplementalInfo.class,  result);
            }
        }
    }

    private List<String> getClusterMembers() {
        List<String> clusterMembers = EMPTY_LIST;
        com.sun.enterprise.config.serverbeans.Cluster cluster = domain.getClusterNamed(clusterName);
        List<Server> targetServers = null;
        if (cluster != null) {
            // Get the list of servers in the cluster.
            targetServers = domain.getServersInTarget(clusterName);
            if (targetServers != null) {
                clusterMembers = new ArrayList<String>(targetServers.size());
                for (Server server : targetServers) {
                    clusterMembers.add(server.getName());
                }
            }
        }
        return clusterMembers;
    }
}