/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.paas.tenantmanager.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.glassfish.paas.tenantmanager.api.TenantConfigService;
import org.glassfish.paas.tenantmanager.config.TenantManagerConfig;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigParser;
import org.jvnet.hk2.config.TransactionListener;
import org.jvnet.hk2.config.Transactions;

import com.sun.enterprise.module.ModulesRegistry;


/**
 * Default implementation for {@link TenantConfigService}.
 * 
 * @author Andriy Zhdanov
 * 
 */
@Service
public class TenantConfigServiceImpl implements TenantConfigService {
    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(Class<T> config) {
        String name = getCurrentTenant();
        Habitat habitat = getHabitat(name);
        // TODO: assert Tenant/Environment/Service is requested
        return  habitat.getComponent(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCurrentTenant() {
        String name = currentTenant.get();
        if (name == null) {
            // TODO: loggin, error handlng, i18n
            throw new IllegalArgumentException("No current tenant set");
        }
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentTenant(String name) {
        currentTenant.set(name);
    }

    private ThreadLocal<String> currentTenant = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return null;
        }
    };

    private Habitat getHabitat(String name) {
        if (!habitats.containsKey(name))  {
            synchronized(habitats) {
                if (!habitats.containsKey(name))  {
                    habitats.put(name, getNewHabitat(name)); 
                }                
            }
        }
        return habitats.get(name);
    }
    
    private Habitat getNewHabitat(String name) {
        final Habitat habitat = registry.createHabitat("default");
        habitat.getComponent(Transactions.class).addTransactionsListener(transactionListener);
        populate(habitat, name);
        return habitat;
    }

    private void populate(Habitat habitat, String name) {
        String filePath = config.getFileStore().toString() + name + "/tenant.xml";
        ConfigParser parser = new ConfigParser(habitat);
        URL fileUrl = null;
        try {
            fileUrl = new URL(filePath);
        } catch (MalformedURLException e) {
            // should not happen, filePath obtained from URL + name
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        parser.parse(fileUrl, new TenantDocument(habitat, fileUrl));
    }
    
    private Map<String, Habitat> habitats = new HashMap<String, Habitat>();

    // TODO: obtain from server-config
    @Inject
    private TenantManagerConfig config;
    
    @Inject
    private ModulesRegistry registry;
    
    @Inject
    private TransactionListener transactionListener;
    
}
