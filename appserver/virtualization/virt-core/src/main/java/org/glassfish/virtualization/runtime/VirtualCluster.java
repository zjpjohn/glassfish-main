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

package org.glassfish.virtualization.runtime;

import com.sun.enterprise.config.serverbeans.Cluster;
import org.glassfish.virtualization.config.ServerPoolConfig;
import org.glassfish.virtualization.config.VirtualMachineConfig;
import org.glassfish.virtualization.config.VirtualMachineRef;
import org.glassfish.virtualization.spi.*;
import org.glassfish.virtualization.util.RuntimeContext;
import org.jvnet.hk2.config.*;

import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Internal runtime representation of a cluster implemented with virtual machines.
 */
public class VirtualCluster {

    final Cluster config;
    final AtomicInteger token = new AtomicInteger();
    final List<VirtualMachine> vms = new ArrayList<VirtualMachine>();

    public VirtualCluster(IAAS iaas, Cluster config) throws VirtException {
        this.config = config;
        token.set(config.getServerRef().size());
        for (VirtualMachineConfig vm :
                config.getExtensionsByType(VirtualMachineConfig.class)) {
            ServerPoolConfig serverPoolConfig = vm.getServerPool();
            ServerPool serverPool = iaas.byName(serverPoolConfig.getName());
            vms.add(serverPool.vmByName(vm.getName()));
        }
    }

    public Cluster getConfig() {
        return config;
    }

    public int allocateToken() {
        return token.addAndGet(1);
    }

    public synchronized void add(final TemplateInstance template, final VirtualMachine vm) {
        try {
            ConfigSupport.apply(new ConfigCode(){
                @Override
                public Object run(ConfigBeanProxy... params) throws PropertyVetoException, TransactionFailure {

                    Cluster wCluster = (Cluster) params[0];
                    ServerPoolConfig wServerPoolConfig = (ServerPoolConfig) params[1];
                    VirtualMachineConfig vmConfig =
                            wCluster.createChild(VirtualMachineConfig.class);
                    vmConfig.setName(vm.getName());
                    vmConfig.setTemplate(template.getConfig());
                    vmConfig.setServerPool(vm.getServerPool().getConfig());
                    wCluster.getExtensions().add(vmConfig);
                    VirtualMachineRef vmRef = wServerPoolConfig.createChild(VirtualMachineRef.class);
                    vmRef.setRef(vmConfig.getName());
                    wServerPoolConfig.getVirtualMachineRefs().add(vmRef);
                    return vmConfig;
                }
            }, config, vm.getServerPool().getConfig());
        } catch (TransactionFailure transactionFailure) {
            throw new RuntimeException(transactionFailure);
        }
        vms.add(vm);
    }

    public synchronized void remove(final VirtualMachine vm) {
        try {
            ConfigSupport.apply(new ConfigCode(){
                @Override
                public Object run(ConfigBeanProxy... params) throws PropertyVetoException, TransactionFailure {
                    Cluster wCluster = (Cluster) params[0];
                    ServerPoolConfig wServerPoolConfig = (ServerPoolConfig) params[1];
                    for (VirtualMachineConfig vmConfig :
                            config.getExtensionsByType(VirtualMachineConfig.class)) {
                        if (vmConfig.getName().equals(vm.getName())) {
                            wCluster.getExtensions().remove(vmConfig);
                            for (VirtualMachineRef ref : wServerPoolConfig.getVirtualMachineRefs()) {
                                if (ref.getRef().equals(vm.getName())) {
                                    wServerPoolConfig.getVirtualMachineRefs().remove(ref);
                                    break;
                                }
                            }
                            return null;
                        }

                    }
                    RuntimeContext.logger.log(Level.WARNING, "Cannot find virtual machine configuration under cluster");
                    return null;
                }
            }, config, vm.getServerPool().getConfig());
        } catch (TransactionFailure transactionFailure) {
            throw new RuntimeException(transactionFailure);
        }
        vms.remove(vm);
    }

    public void delete() {
        List<VirtualMachine> copy = new ArrayList<VirtualMachine>();
        copy.addAll(vms);
        for (VirtualMachine vm : copy) {
            remove(vm);
        }
    }

    public synchronized List<VirtualMachine> getVms() {
        return Collections.unmodifiableList(vms);
    }

}
