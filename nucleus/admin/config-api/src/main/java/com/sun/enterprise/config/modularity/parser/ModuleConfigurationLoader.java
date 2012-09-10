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
package com.sun.enterprise.config.modularity.parser;

import com.sun.enterprise.config.modularity.ConfigModularityUtils;
import com.sun.enterprise.config.modularity.annotation.HasNoDefaultConfiguration;
import com.sun.enterprise.config.modularity.customization.ConfigBeanDefaultValue;
import com.sun.enterprise.util.LocalStringManager;
import com.sun.enterprise.util.LocalStringManagerImpl;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.admin.config.ConfigExtension;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigBean;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.ConfigView;
import org.jvnet.hk2.config.Dom;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

import java.beans.PropertyVetoException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Containing shared functionalists between different derived classes like ConfigSnippetLoader and so on.
 * Shared functionalists includes finding, loading the configuration and creating a ConFigBean from it.
 *
 * @author Masoud Kalali
 */
public class ModuleConfigurationLoader<C extends ConfigBeanProxy, U extends ConfigBeanProxy> {
    private final static Logger LOG = Logger.getLogger(ModuleConfigurationLoader.class.getName());
    protected final C extensionOwner;

    public ModuleConfigurationLoader(C extensionOwner) {
        this.extensionOwner = extensionOwner;
    }

    public U createConfigBeanForType(Class<U> configExtensionType) throws TransactionFailure {
        if (ConfigModularityUtils.hasCustomConfig(configExtensionType)) {
            addConfigBeanFor(configExtensionType, extensionOwner);
        } else {
            if (configExtensionType.getAnnotation(HasNoDefaultConfiguration.class) != null) {
                return null;
            }
            final Class<U> childElement = configExtensionType;
            ConfigSupport.apply(new SingleConfigCode<ConfigBeanProxy>() {
                @Override
                public Object run(ConfigBeanProxy parent) throws PropertyVetoException, TransactionFailure {
                    U child = parent.createChild(childElement);
                    Dom unwrappedChild = Dom.unwrap(child);
                    boolean writeDefaultElementsToXml = Boolean.parseBoolean(System.getProperty("writeDefaultElementsToXml"));
                    if (!writeDefaultElementsToXml) {
                        //Do not write default snippets to the domain.xml
                        unwrappedChild.skipFromXml();
                    }

                    unwrappedChild.addDefaultChildren();
                    getExtensions(parent).add(child);
                    return child;
                }
            }, extensionOwner);
        }

        List<U> extensions = getExtensions(extensionOwner);
        for (ConfigBeanProxy extension : extensions) {
            try {
                U configBeanInstance = configExtensionType.cast(extension);
                if (configBeanInstance instanceof ConfigExtension) {
                    ConfigBean cb = (ConfigBean) ((ConfigView) Proxy.getInvocationHandler(configBeanInstance)).getMasterView();
                    Habitat habitat = cb.getHabitat();
                    ServiceLocatorUtilities.addOneDescriptor(habitat,
                            BuilderHelper.createConstantDescriptor(configBeanInstance, ServerEnvironment.DEFAULT_INSTANCE_NAME,
                                    ConfigSupport.getImpl(configBeanInstance).getProxyType()));
                }
                return configBeanInstance;
            } catch (Exception e) {
                // ignore, not the right type.
            }
        }
        return null;
    }


    protected void addConfigBeanFor(Class<U> extensionType, C extensionOwner) {
        ConfigBean cb = (ConfigBean) ((ConfigView) Proxy.getInvocationHandler(extensionOwner)).getMasterView();
        Habitat habitat = cb.getHabitat();
        List<ConfigBeanDefaultValue> configBeanDefaultValueList = ConfigModularityUtils.getDefaultConfigurations(extensionType);
        ConfigurationParser configurationParser = new ConfigurationParser();
        configurationParser.prepareAndSetConfigBean(habitat, configBeanDefaultValueList);
    }

    private <U extends ConfigBeanProxy> List<U> getExtensions(ConfigBeanProxy parent) {

        Method m = null;
        try {
            if (parent != null) {
                m = parent.getClass().getMethod("getExtensions");
            }
        } catch (NoSuchMethodException e) {
            LocalStringManager localStrings =
                    new LocalStringManagerImpl(ConfigurationPopulator.class);
            final String msg = localStrings.getLocalString(this.getClass(),
                    "invalid.extension.point",
                    Dom.unwrap(parent).getProxyType().getName());
            LOG.log(Level.SEVERE, msg, e);
        }
        if (m != null) {
            try {
                return (List<U>) m.invoke(parent);
            } catch (IllegalAccessException e) {
                LocalStringManager localStrings =
                        new LocalStringManagerImpl(ConfigurationPopulator.class);
                final String msg = localStrings.getLocalString(this.getClass(),
                        "invalid.extension.point",
                        Dom.unwrap(parent).getProxyType().getName());
                LOG.log(Level.SEVERE, msg, e);
            } catch (InvocationTargetException e) {
                LocalStringManager localStrings =
                        new LocalStringManagerImpl(ConfigurationPopulator.class);
                final String msg = localStrings.getLocalString(this.getClass(),
                        "invalid.extension.point",
                        Dom.unwrap(parent).getProxyType().getName());
                LOG.log(Level.SEVERE, msg, e);
            }
        }
        return null;
    }
}