/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.container.common.impl;

import com.sun.enterprise.container.common.spi.EjbNamingReferenceManager;
import com.sun.enterprise.container.common.spi.WebServiceReferenceManager;
import com.sun.enterprise.container.common.spi.util.CallFlowAgent;
import com.sun.enterprise.container.common.spi.util.ComponentEnvManager;
import com.sun.enterprise.deployment.*;
import com.sun.enterprise.naming.spi.NamingObjectFactory;
import com.sun.enterprise.naming.spi.NamingUtils;
import org.glassfish.api.admin.ProcessEnvironment;
import org.glassfish.api.invocation.ComponentInvocation;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.api.naming.ComponentNamingUtil;
import org.glassfish.api.naming.GlassfishNamingManager;
import org.glassfish.api.naming.JNDIBinding;
import org.glassfish.api.naming.NamingObjectProxy;
import org.glassfish.deployment.common.Descriptor;
import org.glassfish.deployment.common.JavaEEResourceType;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.javaee.services.CommonResourceProxy;
import org.glassfish.resourcebase.resources.api.ResourceDeployer;
import org.glassfish.resourcebase.resources.util.ResourceManagerFactory;
import org.jvnet.hk2.annotations.Service;
import com.sun.enterprise.deployment.ResourceDescriptor;

import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;
import javax.validation.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.glassfish.deployment.common.JavaEEResourceType.*;

@Service
public class ComponentEnvManagerImpl
    implements ComponentEnvManager {

    private static final String JAVA_COLON = "java:";
    private static final String JAVA_COMP_ENV_STRING = "java:comp/env/";
    private static final String JAVA_COMP_PREFIX = "java:comp/";
    private static final String JAVA_MODULE_PREFIX = "java:module/";
    private static final String JAVA_APP_PREFIX = "java:app/";
    private static final String JAVA_GLOBAL_PREFIX = "java:global/";

    @Inject
    private ServiceLocator habitat;

    @Inject
    private Logger _logger;

    @Inject
    GlassfishNamingManager namingManager;

    @Inject
    ComponentNamingUtil componentNamingUtil;

    @Inject
    transient private CallFlowAgent callFlowAgent;

    @Inject
    transient private TransactionManager txManager;

    @Inject
    private ProcessEnvironment processEnv;

    // TODO: container-common shouldn't depend on EJB stuff, right?
    // this seems like the abstraction design failure.
    @Inject
    private NamingUtils namingUtils;

    @Inject
    private InvocationManager invMgr;

    private ConcurrentMap<String, RefCountJndiNameEnvironment> compId2Env =
            new ConcurrentHashMap<String, RefCountJndiNameEnvironment>();

    /*
     * Keep track of number of components using the same component ID
     * so that we can match register calls with unregister calls.
     * EJBs in war files will use the same component ID as the web
     * bundle.
     */
    private static class RefCountJndiNameEnvironment {
        public RefCountJndiNameEnvironment(JndiNameEnvironment env) {
            this.env = env;
            this.refcnt = new AtomicInteger(1);
        }
        public JndiNameEnvironment env;
        public AtomicInteger refcnt;
    }

    public void register(String componentId, JndiNameEnvironment env) {
        RefCountJndiNameEnvironment nrj = new RefCountJndiNameEnvironment(env);
        RefCountJndiNameEnvironment rj =
            compId2Env.putIfAbsent(componentId, nrj);
        if (rj != null)
            rj.refcnt.incrementAndGet();
    }

    public void unregister(String componentId) {
        RefCountJndiNameEnvironment rj = compId2Env.get(componentId);
        if (rj != null && rj.refcnt.decrementAndGet() == 0)
            compId2Env.remove(componentId);
    }

    public JndiNameEnvironment getJndiNameEnvironment(String componentId) {
        RefCountJndiNameEnvironment rj = compId2Env.get(componentId);
        if (componentId != null && _logger.isLoggable(Level.FINEST)) {
            _logger.finest("ComponentEnvManagerImpl: " +
                "getJndiNameEnvironment " + componentId + " is " +
                (rj == null ? "NULL" : rj.env.getClass().toString()));
        }
        return rj == null ? null : rj.env;
    }

    public JndiNameEnvironment getCurrentJndiNameEnvironment() {
        JndiNameEnvironment desc = null;
        ComponentInvocation inv = invMgr.getCurrentInvocation();
        if (inv != null) {
            if (inv.componentId != null) {
                desc = getJndiNameEnvironment(inv.componentId);
                if (_logger.isLoggable(Level.FINEST)) {
                    _logger.finest("ComponentEnvManagerImpl: " +
                        "getCurrentJndiNameEnvironment " + inv.componentId +
                        " is " + desc.getClass());
                }
            }
        }

        return desc;
    }


    public String bindToComponentNamespace(JndiNameEnvironment env)
        throws NamingException {

        String compEnvId = getComponentEnvId(env);

        Collection<JNDIBinding> bindings = new ArrayList<JNDIBinding>();

        // Add all java:comp, java:module, and java:app(except for app clients) dependencies
        // for the specified environment
        addJNDIBindings(env, ScopeType.COMPONENT, bindings);
        addDefaultJNDIBindings(env, ScopeType.COMPONENT, bindings);
        addJNDIBindings(env, ScopeType.MODULE, bindings);

        if (!(env instanceof ApplicationClientDescriptor)) {
            addJNDIBindings(env, ScopeType.APP, bindings);
        }

        if( env instanceof Application) {
            Application app = (Application) env;
            // Add any java:app entries defined by any app clients.  These must
            // live in the server so they are accessible by other modules in the .ear.  Likewise,
            // those same entries will not be registered within the app client JVM itself.
            for(JndiNameEnvironment next : app.getBundleDescriptors(ApplicationClientDescriptor.class)) {
                addJNDIBindings(next, ScopeType.APP, bindings);
            }

            namingManager.bindToAppNamespace(getApplicationName(env), bindings);
        } else {
            
            boolean treatComponentAsModule = getTreatComponentAsModule(env);

            // Bind dependencies to the namespace for this component
            namingManager.bindToComponentNamespace(getApplicationName(env), getModuleName(env),
                    compEnvId, treatComponentAsModule, bindings);
            compEnvId = getComponentEnvId(env);

        }

        if (!(env instanceof ApplicationClientDescriptor)) {
            // Publish any dependencies with java:global names defined by the current env
            // to the global namespace
            Collection<JNDIBinding> globalBindings = new ArrayList<JNDIBinding>();
            addJNDIBindings(env, ScopeType.GLOBAL, globalBindings);

            if( env instanceof Application ) {
                Application app = (Application) env;
                // Add any java:global entries defined by any app clients.  These must
                // live in the server so they are accessible by other modules in the .ear.  Likewise,
                // those same entries will not be registered within the app client JVM itself.
                for(JndiNameEnvironment next : app.getBundleDescriptors(ApplicationClientDescriptor.class)) {
                    addJNDIBindings(next, ScopeType.GLOBAL, globalBindings);
                }
            }

            for(JNDIBinding next : globalBindings) {
                namingManager.publishObject(next.getName(), next.getValue(), true);
            }
        }

        // If the app contains any application client modules (and the given env isn't
        // an application client)  register any java:app dependencies under a well-known
        // internal portion of the global namespace based on the application name.  This
        // will allow app client access to application-wide objects within the server.
        Application app = getApplicationFromEnv(env);
        if( !(env instanceof ApplicationClientDescriptor) &&
            app.getBundleDescriptors(ApplicationClientDescriptor.class).size() > 0 ) {
            for(JNDIBinding next : bindings) {
                if( dependencyAppliesToScope(next.getName(), ScopeType.APP) ) {
                    String internalGlobalJavaAppName =
                            componentNamingUtil.composeInternalGlobalJavaAppName(app.getAppName(),
                                    next.getName());
                    namingManager.publishObject(internalGlobalJavaAppName, next.getValue(), true);
                }
            }
        }


        if( compEnvId != null ) {
            if (_logger.isLoggable(Level.FINEST))
                _logger.finest("ComponentEnvManagerImpl: " +
                    "register " + compEnvId + " is " + env.getClass());
            this.register(compEnvId, env);
        }

        return compEnvId;
    }


    public void addToComponentNamespace(JndiNameEnvironment origEnv,
                                        Collection<EnvironmentProperty> envProps,
                                        Collection<ResourceReferenceDescriptor> resRefs)
        throws NamingException {

        String compEnvId = getComponentEnvId(origEnv);

        Collection<JNDIBinding> bindings = new ArrayList<JNDIBinding>();

        addEnvironmentProperties(ScopeType.COMPONENT, envProps.iterator(), bindings);
        addResourceReferences(ScopeType.COMPONENT, resRefs.iterator(), bindings);

        boolean treatComponentAsModule = getTreatComponentAsModule(origEnv);

        // Bind dependencies to the namespace for this component
        namingManager.bindToComponentNamespace(getApplicationName(origEnv), getModuleName(origEnv),
                compEnvId, treatComponentAsModule, bindings);

        return;
    }

    private String getResourceId(JndiNameEnvironment env, Descriptor desc){

        String resourceId = "";
        if(dependencyAppliesToScope(desc, ScopeType.COMPONENT)){
            resourceId = getApplicationName(env) +    "/" + getModuleName(env) + "/" +
                    getComponentEnvId(env) ;
        } else if(dependencyAppliesToScope(desc, ScopeType.MODULE)){
            resourceId = getApplicationName(env) +    "/" + getModuleName(env) ;
        } else if(dependencyAppliesToScope(desc, ScopeType.APP)){
            resourceId = getApplicationName(env)  ;
        }
        
        return resourceId;

    }

    private void addAllDescriptorBindings(JndiNameEnvironment env, ScopeType scope, Collection<JNDIBinding> jndiBindings) {

        Set<ResourceDescriptor> allDescriptors = new HashSet<ResourceDescriptor>();
        Set<ResourceDescriptor> dsds = env.getResourceDescriptors(JavaEEResourceType.DSD);
        Set<ResourceDescriptor> jmscfdds = env.getResourceDescriptors(JavaEEResourceType.JMSCFDD);
        Set<ResourceDescriptor> msds =env.getResourceDescriptors(JavaEEResourceType.MSD);
        Set<ResourceDescriptor> jmsddds = env.getResourceDescriptors(JavaEEResourceType.JMSDD);
        if(!(env instanceof ApplicationClientDescriptor)) {
            Set<ResourceDescriptor> ccrdds = env.getResourceDescriptors(JavaEEResourceType.CRD);
            allDescriptors.addAll(ccrdds);
        } else {
            _logger.fine("Do not support connector-resource in client module.");
        }
        if(!(env instanceof ApplicationClientDescriptor)) {
           Set<ResourceDescriptor> aodd = env.getResourceDescriptors(JavaEEResourceType.AODD);
           allDescriptors.addAll(aodd);
        } else {
           _logger.fine("Do not support administered-object in client module.");
        }
        allDescriptors.addAll(dsds);
        allDescriptors.addAll(jmscfdds);
        allDescriptors.addAll(msds);
        allDescriptors.addAll(jmsddds);

        for (ResourceDescriptor descriptor : allDescriptors) {

            if (!dependencyAppliesToScope(descriptor, scope)) {
                continue;
            }

            if(descriptor.getResourceType().equals(DSD)) {
                if (((DataSourceDefinitionDescriptor)descriptor).isDeployed()) {
                    continue;
                }
            }

            String resourceId = getResourceId(env, descriptor);
            descriptor.setResourceId(resourceId);

            CommonResourceProxy proxy = habitat.getService(CommonResourceProxy.class);
            proxy.setDescriptor(descriptor);

            String logicalJndiName = descriptorToLogicalJndiName(descriptor);
            CompEnvBinding envBinding = new CompEnvBinding(logicalJndiName, proxy);
            jndiBindings.add(envBinding);
        }
    }

    private ResourceDeployer getResourceDeployer(Object resource) {
        return habitat.<ResourceManagerFactory>getService(ResourceManagerFactory.class).getResourceDeployer(resource);
    }


    public void unbindFromComponentNamespace(JndiNameEnvironment env)
        throws NamingException {

        //undeploy all descriptors
        undeployAllDescriptors(env);

        // Unpublish any global entries exported by this environment
        Collection<JNDIBinding> globalBindings = new ArrayList<JNDIBinding>();
        addJNDIBindings(env, ScopeType.GLOBAL, globalBindings);

        for(JNDIBinding next : globalBindings) {
            namingManager.unpublishObject(next.getName());
        }

        Application app = getApplicationFromEnv(env);

        //undeploy data-sources & mail-sessions exposed by app-client descriptors.
        Set<ApplicationClientDescriptor> appClientDescs = app.getBundleDescriptors(ApplicationClientDescriptor.class);
        for(ApplicationClientDescriptor acd : appClientDescs){
            undeployAllDescriptors(acd);
        }

        if( !(env instanceof ApplicationClientDescriptor ) &&
            (app.getBundleDescriptors(ApplicationClientDescriptor.class).size() > 0) ) {
            Collection<JNDIBinding> appBindings = new ArrayList<JNDIBinding>();
            addJNDIBindings(env, ScopeType.APP, appBindings);
            for(JNDIBinding next : appBindings) {

                String internalGlobalJavaAppName =
                        componentNamingUtil.composeInternalGlobalJavaAppName(app.getAppName(),
                                next.getName());
                namingManager.unpublishObject(internalGlobalJavaAppName);

            }
        }

        if( env instanceof Application) {
            namingManager.unbindAppObjects(getApplicationName(env));
        } else {
            // Unbind anything in the component namespace
            String compEnvId = getComponentEnvId(env);
            namingManager.unbindComponentObjects(compEnvId);
            this.unregister(compEnvId);
        }

    }

    private void undeployAllDescriptors(JndiNameEnvironment env) {

        Set<ResourceDescriptor> allDescriptors = env.getAllResourcesDescriptors(env.getClass());

        for (ResourceDescriptor descriptor : allDescriptors) {
            switch (descriptor.getResourceType()) {
                case DSD:
                    DataSourceDefinitionDescriptor dataSourceDefinitionDescriptor = (DataSourceDefinitionDescriptor)descriptor;
                    if(dataSourceDefinitionDescriptor.isDeployed()) {
                        if(undepoyResource(dataSourceDefinitionDescriptor)) {
                            dataSourceDefinitionDescriptor.setDeployed(false);
                        }
                    }
                    break;
                case MSD:
                    MailSessionDescriptor mailSessionDescriptor = (MailSessionDescriptor)descriptor;
                    if(mailSessionDescriptor.isDeployed()) {
                        if(undepoyResource(mailSessionDescriptor)) {
                            mailSessionDescriptor.setDeployed(false);
                        }
                    }
                    break;
                case CRD:
                    ConnectorResourceDefinitionDescriptor connectorResourceDefinitionDescriptor = (ConnectorResourceDefinitionDescriptor)descriptor;
                    undepoyResource(connectorResourceDefinitionDescriptor);
                    break;
                case JMSCFDD:
                    JMSConnectionFactoryDefinitionDescriptor jmsConnectionFactoryDefinitionDescriptor = (JMSConnectionFactoryDefinitionDescriptor)descriptor;
                    undepoyResource(jmsConnectionFactoryDefinitionDescriptor);
                    break;
                case AODD:
                    AdministeredObjectDefinitionDescriptor administeredObjectDefinitionDescriptor = (AdministeredObjectDefinitionDescriptor)descriptor;
                    undepoyResource(administeredObjectDefinitionDescriptor);
                    break;
            }
        }
    }

    private boolean undepoyResource(Descriptor descriptor) {
        try{
            ResourceDeployer deployer = getResourceDeployer(descriptor);
            deployer.undeployResource(descriptor);
            return true;
        }catch(Exception e){
            _logger.log(Level.WARNING, "Unable to undeploy Descriptor [ " + descriptor.getName() + " ] ", e);
            return false;
        }
    }

    private void addEnvironmentProperties(ScopeType scope, Iterator envItr, Collection<JNDIBinding> jndiBindings) {

        while( envItr.hasNext() ) {

            EnvironmentProperty next = (EnvironmentProperty) envItr.next();

            if( !dependencyAppliesToScope(next, scope)) {
                continue;
            }

            // Only env-entries that have been assigned a value are
            // eligible for look up
            if (next.hasAValue()) {
                String name = descriptorToLogicalJndiName(next);
                Object value;
                if(next.hasLookupName()) {
                    value = namingUtils.createLazyNamingObjectFactory(name, next.getLookupName(), true);
                } else if(next.getMappedName().length() > 0) {
                    value = namingUtils.createLazyNamingObjectFactory(name, next.getMappedName(), true);
                } else {
                    value = namingUtils.createSimpleNamingObjectFactory(name, next.getValueObject());
                }
                jndiBindings.add(new CompEnvBinding(name, value));
            }
        }
    }

    private void addResourceReferences(ScopeType scope, Iterator resRefItr, Collection<JNDIBinding> jndiBindings) {

        while( resRefItr.hasNext() ) {
            ResourceReferenceDescriptor resourceRef =
                (ResourceReferenceDescriptor) resRefItr.next();

            if( !dependencyAppliesToScope(resourceRef, scope)) {
                continue;
            }
            resourceRef.checkType();

            String name = descriptorToLogicalJndiName(resourceRef);
            Object value = null;
            String physicalJndiName = resourceRef.getJndiName();
            
            // the jndi-name of URL resource can be either the actual URL value,
            // or another jndi-name that can be looked up
            if (resourceRef.isURLResource()) {
                if(physicalJndiName.startsWith(JAVA_GLOBAL_PREFIX) ||
                   physicalJndiName.startsWith(JAVA_APP_PREFIX) ||
                   physicalJndiName.startsWith(JAVA_MODULE_PREFIX) ||
                   physicalJndiName.startsWith(JAVA_COMP_PREFIX)) {
                    //for jndi-name or lookup-name like "java:module/env/url/testUrl"
                    value = namingUtils.createLazyNamingObjectFactory(name, physicalJndiName, false);
                } else {
                    try {
                        //for jndi-name like "http://localhost:8080/index.html"
                        Object obj = new java.net.URL(physicalJndiName);
                        NamingObjectFactory factory = namingUtils.createSimpleNamingObjectFactory(name, obj);
                        value = namingUtils.createCloningNamingObjectFactory(name, factory);
                    } catch (MalformedURLException e) {
                        // for jndi-name or lookup-name like "url/testUrl"
                        value = namingUtils.createLazyNamingObjectFactory(name, physicalJndiName, false);
                    }
                }
            } else if (resourceRef.isORB()) {
                // TODO handle non-default ORBs
                value = namingUtils.createLazyNamingObjectFactory(name, physicalJndiName, false);
            } else if (resourceRef.isWebServiceContext()) {
                WebServiceReferenceManager wsRefMgr = habitat.getService(WebServiceReferenceManager.class);
                if (wsRefMgr != null )  {
                    value = wsRefMgr.getWSContextObject();
                } else {
                    _logger.log (Level.SEVERE,
                            "Cannot find the following class to proceed with @Resource WebServiceContext" +
                                    wsRefMgr +
                                    "Please confirm if webservices module is installed ");
                }

            } else {
              value = namingUtils.createLazyNamingObjectFactory(name, physicalJndiName, false);
            }

            jndiBindings.add(new CompEnvBinding(name, value));

        }
    }

    private void addJNDIBindings(JndiNameEnvironment env, ScopeType scope, Collection<JNDIBinding> jndiBindings) {

        // Create objects to be bound for each env dependency.  Only add bindings that
        // match the given scope.

        addEnvironmentProperties(scope, env.getEnvironmentProperties().iterator(), jndiBindings);

        for (Iterator itr =
             env.getResourceEnvReferenceDescriptors().iterator();
             itr.hasNext();) {
            ResourceEnvReferenceDescriptor next =
                (ResourceEnvReferenceDescriptor) itr.next();

            if( !dependencyAppliesToScope(next, scope)) {
                continue;
            }
            next.checkType();
            jndiBindings.add(getCompEnvBinding(next));
        }

        addAllDescriptorBindings(env,scope,jndiBindings);

        for (Iterator itr = env.getEjbReferenceDescriptors().iterator();
             itr.hasNext();) {
            EjbReferenceDescriptor next = (EjbReferenceDescriptor) itr.next();

            if( !dependencyAppliesToScope(next, scope)) {
                continue;
            }

            String name = descriptorToLogicalJndiName(next);
            EjbReferenceProxy proxy = new EjbReferenceProxy(next);
            jndiBindings.add(new CompEnvBinding(name, proxy));
        }


        for (Iterator itr = env.getMessageDestinationReferenceDescriptors().
                 iterator(); itr.hasNext();) {
            MessageDestinationReferenceDescriptor next =
                (MessageDestinationReferenceDescriptor) itr.next();

            if( !dependencyAppliesToScope(next, scope)) {
                continue;
            }

            jndiBindings.add(getCompEnvBinding(next));
        }

        addResourceReferences(scope, env.getResourceReferenceDescriptors().iterator(), jndiBindings);

        for (EntityManagerFactoryReferenceDescriptor next :
                 env.getEntityManagerFactoryReferenceDescriptors()) {

            if( !dependencyAppliesToScope(next, scope)) {
                continue;
            }

            String name = descriptorToLogicalJndiName(next);
            Object value = new FactoryForEntityManagerFactoryWrapper(next.getUnitName(),
                    invMgr, this);
            jndiBindings.add(new CompEnvBinding(name, value));
         }


        for (Iterator itr = env.getServiceReferenceDescriptors().iterator();
             itr.hasNext();) {
            ServiceReferenceDescriptor next =
                (ServiceReferenceDescriptor) itr.next();

            if( !dependencyAppliesToScope(next, scope)) {
                continue;
            }

            if(next.getMappedName() != null) {
                next.setName(next.getMappedName());
            }
            
            String name = descriptorToLogicalJndiName(next);
            WebServiceRefProxy value = new WebServiceRefProxy(next);
            jndiBindings.add(new CompEnvBinding(name,value))  ;
            // Set WSDL File URL here if it null (happens during server restart)
           /* if((next.getWsdlFileUrl() == null)  &&
                (next.getWsdlFileUri() != null)) {
                try {
                    if(next.getWsdlFileUri().startsWith("http")) {
                        // HTTP URLs set as is
                        next.setWsdlFileUrl(new URL(next.getWsdlFileUri()));
                    } else if((new File(next.getWsdlFileUri())).isAbsolute()) {
                        // Absolute WSDL file paths set as is
                        next.setWsdlFileUrl((new File(next.getWsdlFileUri())).toURL());
                    } else {

                        WebServiceContractImpl wscImpl = WebServiceContractImpl.getInstance();
                        ServerEnvironment servEnv = wscImpl.getServerEnvironmentImpl();
                        String deployedDir = servEnv.getApplicationRepositoryPath().getAbsolutePath();
                        if(deployedDir != null) {
                            File fileURL;
                            if(next.getBundleDescriptor().getApplication().isVirtual()) {
                                fileURL = new File(deployedDir+File.separator+next.getWsdlFileUri());
                            } else {
                                fileURL = new File(deployedDir+File.separator+
                                        next.getBundleDescriptor().getModuleDescriptor().getArchiveUri().replaceAll("\\.", "_") +
                                        File.separator +next.getWsdlFileUri());
                            }
                            next.setWsdlFileUrl(fileURL.toURL());
                        }
                    }
                    }
                } catch (Throwable mex) {
                    throw new NamingException(mex.getLocalizedMessage());
                }*/
            }

         for (EntityManagerReferenceDescriptor next :
             env.getEntityManagerReferenceDescriptors()) {

             if( !dependencyAppliesToScope(next, scope)) {
                continue;
             }

             String name = descriptorToLogicalJndiName(next);
             FactoryForEntityManagerWrapper value =
                new FactoryForEntityManagerWrapper(next, this);
            jndiBindings.add(new CompEnvBinding(name, value));
         }

        return;
    }

    private void addDefaultJNDIBindings(JndiNameEnvironment env, ScopeType scope, Collection<JNDIBinding> jndiBindings) {
        if (ScopeType.COMPONENT.equals(scope)) {
            String logicalJndiName = "java:comp/DefaultJMSConnectionFactory";
            String physicalJndiName = "jms/__defaultConnectionFactory";
            Object value = namingUtils.createLazyNamingObjectFactory(logicalJndiName, physicalJndiName, false);
            if (env instanceof Application) {
                jndiBindings.add(new CompEnvBinding(logicalJndiName, value));
            } else {
                String appName = getApplicationName(env);
                String moduleName = getModuleName(env);
                if (appName != null && moduleName != null) {
                    // avoid NPE of few default web applications for they may not have names.
                    jndiBindings.add(new CompEnvBinding(logicalJndiName, value));
                }
            }
        }
    }

    private CompEnvBinding getCompEnvBinding(final ResourceEnvReferenceDescriptor next) {
        final String name = descriptorToLogicalJndiName(next);
            Object value = null;
            if (next.isEJBContext()) {
                value = new EjbContextProxy(next.getRefType());
            } else if( next.isValidator() ) {
                value = new ValidatorProxy(_logger);
            } else if( next.isValidatorFactory() ) {
                value = new ValidatorFactoryProxy(_logger);
            } else if( next.isCDIBeanManager() ) {
                value = namingUtils.createLazyNamingObjectFactory(name, "java:comp/BeanManager", false);
            } else if( next.isManagedBean() ) {
                ManagedBeanDescriptor managedBeanDesc = next.getManagedBeanDescriptor();
                if( processEnv.getProcessType().isServer() ) {
                    value = namingUtils.createLazyNamingObjectFactory(name, next.getJndiName(), false);
                } else {
                    value = namingUtils.createLazyNamingObjectFactory(name, managedBeanDesc.getAppJndiName(), false);
                }
            } else {
                // we lookup first in the InitialContext, if not found we look up in the habitat.
                value = new NamingObjectFactory() {
                    // It might be mapped to a managed bean, so turn off caching to ensure that a
                    // new instance is created each time.
                    NamingObjectFactory delegate = namingUtils.createLazyNamingObjectFactory(name, next.getJndiName(), false);
                    public boolean isCreateResultCacheable() {
                        return false;
                    }

                    public Object create(Context ic) throws NamingException {
                        try {
                            return delegate.create(ic);
                        } catch(NamingException e) {
                            ActiveDescriptor<?> ad = habitat.getBestDescriptor(
                                    BuilderHelper.createNameAndContractFilter(
                                            next.getRefType(), next.getMappedName()));
                            if (ad == null) throw e;
                            
                            Object ref = habitat.getServiceHandle(ad).getService();
                            if (ref!=null) {
                                return ref;
                            }
                            throw e;
                        }
                    }
                };
            }

        return new CompEnvBinding(name, value);
    }

    private CompEnvBinding getCompEnvBinding(MessageDestinationReferenceDescriptor next) {
        String name = descriptorToLogicalJndiName(next);
        String physicalJndiName = null;
        if (next.isLinkedToMessageDestination()) {
            physicalJndiName = next.getMessageDestination().getJndiName();
        } else {
            physicalJndiName = next.getJndiName();
        }

        Object value = namingUtils.createLazyNamingObjectFactory(name, physicalJndiName, true);
            return new CompEnvBinding(name, value);
    }

    private boolean dependencyAppliesToScope(Descriptor descriptor, ScopeType scope) {
      
        String name = descriptor.getName();

        return dependencyAppliesToScope(name, scope);

    }

    private boolean dependencyAppliesToScope(String name, ScopeType scope) {
        boolean appliesToScope = false;

        switch(scope) {
            case COMPONENT :
                // Env names without an explicit java: prefix default to java:comp
                appliesToScope = name.startsWith(JAVA_COMP_PREFIX) || !name.startsWith(JAVA_COLON);
                break;
            case MODULE :
                appliesToScope = name.startsWith(JAVA_MODULE_PREFIX);
                break;
            case APP :
                appliesToScope = name.startsWith(JAVA_APP_PREFIX);
                break;
            case GLOBAL :
                appliesToScope = name.startsWith(JAVA_GLOBAL_PREFIX);
                break;
            default :
                break;
        }

        return appliesToScope;
    }

    private boolean getTreatComponentAsModule(JndiNameEnvironment env) {

        boolean treatComponentAsModule = false;

        if( env instanceof WebBundleDescriptor ) {
            treatComponentAsModule = true;
        } else {

            if (env instanceof EjbDescriptor ) {

                EjbDescriptor ejbDesc = (EjbDescriptor) env;
                EjbBundleDescriptor ejbBundle = ejbDesc.getEjbBundleDescriptor();
                if( ejbBundle.getModuleDescriptor().getDescriptor() instanceof WebBundleDescriptor ) {
                    treatComponentAsModule = true;
                }
            }

        }
                
        return treatComponentAsModule;
    }

    /**
     * Generate the name of an environment dependency in the java:
     * namespace.  This is the lookup string used by a component to access
     * the dependency.
     */
    private String descriptorToLogicalJndiName(Descriptor descriptor) {
        // If no java: prefix is specified, default to component scope.
        String rawName = descriptor.getName();
        return (rawName.startsWith(JAVA_COLON)) ?
                rawName : JAVA_COMP_ENV_STRING + rawName;
    }


    private static final String ID_SEPARATOR = "_";

    /**
     * Generate a unique id name for each J2EE component.
     */
    public String getComponentEnvId(JndiNameEnvironment env) {
	    String id = null;

        if (env instanceof EjbDescriptor) {
            // EJB component
	        EjbDescriptor ejbEnv = (EjbDescriptor) env;

            // Make jndi name flat so it won't result in the creation of
            // a bunch of sub-contexts.
            String flattedJndiName = ejbEnv.getJndiName().replace('/', '.');

            EjbBundleDescriptor ejbBundle = ejbEnv.getEjbBundleDescriptor();
            Descriptor d = ejbBundle.getModuleDescriptor().getDescriptor();
            // if this EJB is in a war file, use the same component ID
            // as the web bundle, because they share the same JNDI namespace
            if (d instanceof WebBundleDescriptor) {
                // copy of code below
                WebBundleDescriptor webEnv = (WebBundleDescriptor) d;
                id = webEnv.getApplication().getName() + ID_SEPARATOR +
                    webEnv.getContextRoot();
                _logger.finer("ComponentEnvManagerImpl: " +
                                "converting EJB to web bundle id " + id);
            } else {
                id = ejbEnv.getApplication().getName() + ID_SEPARATOR +
                    ejbBundle.getModuleDescriptor().getArchiveUri()
                    + ID_SEPARATOR +
                    ejbEnv.getName() + ID_SEPARATOR + flattedJndiName +
                    ejbEnv.getUniqueId();
            }
        } else if(env instanceof WebBundleDescriptor) {
            WebBundleDescriptor webEnv = (WebBundleDescriptor) env;
	    id = webEnv.getApplication().getName() + ID_SEPARATOR +
                webEnv.getContextRoot();
        } else if (env instanceof ApplicationClientDescriptor) {
            ApplicationClientDescriptor appEnv =
		(ApplicationClientDescriptor) env;
	    id = "client" + ID_SEPARATOR + appEnv.getName() +
                ID_SEPARATOR + appEnv.getMainClassName();
        } else if( env instanceof ManagedBeanDescriptor ) {
            id = ((ManagedBeanDescriptor) env).getGlobalJndiName();
        } else if( env instanceof EjbBundleDescriptor ) {
            EjbBundleDescriptor ejbBundle = (EjbBundleDescriptor) env;
            id = "__ejbBundle__" + ID_SEPARATOR + ejbBundle.getApplication().getName() +
                    ID_SEPARATOR + ejbBundle.getModuleName();                  
        }

        if(_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, getApplicationName(env)
                + "Component Id: " + id);
        }
        return id;
    }

    private Application getApplicationFromEnv(JndiNameEnvironment env) {
        Application app = null;

        if (env instanceof EjbDescriptor) {
            // EJB component
	        EjbDescriptor ejbEnv = (EjbDescriptor) env;
            app = ejbEnv.getApplication();
        } else if ( env instanceof EjbBundleDescriptor ) {
            EjbBundleDescriptor ejbBundle = (EjbBundleDescriptor) env;
            app  = ejbBundle.getApplication();
        } else if (env instanceof WebBundleDescriptor) {
            WebBundleDescriptor webEnv = (WebBundleDescriptor) env;
	        app = webEnv.getApplication();
        } else if (env instanceof ApplicationClientDescriptor) {
            ApplicationClientDescriptor appEnv = (ApplicationClientDescriptor) env;
	        app=  appEnv.getApplication();
        } else if ( env instanceof ManagedBeanDescriptor ) {
            ManagedBeanDescriptor mb = (ManagedBeanDescriptor) env;
            app = mb.getBundle().getApplication();
        } else if ( env instanceof Application ) {
            app = ((Application)env);
        } else {
            throw new IllegalArgumentException("IllegalJndiNameEnvironment : env");
        }

        return app;
    }

    private String getApplicationName(JndiNameEnvironment env) {
        String appName = "";

        Application app = getApplicationFromEnv(env);
        if( app != null) {
            appName = app.getAppName();
        } else {
            throw new IllegalArgumentException("IllegalJndiNameEnvironment : env");
        }

        return appName;
    }

    private String getModuleName(JndiNameEnvironment env) {

        String moduleName = null;

        if (env instanceof EjbDescriptor) {
            // EJB component
            EjbDescriptor ejbEnv = (EjbDescriptor) env;
            EjbBundleDescriptor ejbBundle = ejbEnv.getEjbBundleDescriptor();
            moduleName = ejbBundle.getModuleDescriptor().getModuleName();
        } else if( env instanceof EjbBundleDescriptor ) {
            EjbBundleDescriptor ejbBundle = (EjbBundleDescriptor) env;
            moduleName = ejbBundle.getModuleDescriptor().getModuleName();
        } else if (env instanceof WebBundleDescriptor) {
            WebBundleDescriptor webEnv = (WebBundleDescriptor) env;
            moduleName = webEnv.getModuleName();
        } else if (env instanceof ApplicationClientDescriptor) {
            ApplicationClientDescriptor appEnv = (ApplicationClientDescriptor) env;
            moduleName =  appEnv.getModuleName();
        } else if ( env instanceof ManagedBeanDescriptor ) {
            ManagedBeanDescriptor mb = (ManagedBeanDescriptor) env;
            moduleName = mb.getBundle().getModuleName();
        } else {
            throw new IllegalArgumentException("IllegalJndiNameEnvironment : env");
        }

        return moduleName;

    }

    private class FactoryForEntityManagerWrapper
        implements NamingObjectProxy {

        private final EntityManagerReferenceDescriptor refDesc;
        private final ComponentEnvManager compEnvMgr;

        FactoryForEntityManagerWrapper(EntityManagerReferenceDescriptor refDesc,
            ComponentEnvManager compEnvMgr) {
            this.refDesc = refDesc;
            this.compEnvMgr = compEnvMgr;
        }

        public Object create(Context ctx) {
            EntityManagerWrapper emWrapper = new EntityManagerWrapper(txManager, invMgr, compEnvMgr, callFlowAgent);
            emWrapper.initializeEMWrapper(refDesc.getUnitName(),
                    refDesc.getPersistenceContextType(),
                    refDesc.getProperties());

            return emWrapper;
        }
    }

    private class EjbContextProxy
        implements NamingObjectProxy {

        private volatile EjbNamingReferenceManager ejbRefMgr;
        private String contextType;

        EjbContextProxy(String contextType) {
            this.contextType = contextType;
        }

        public Object create(Context ctx)
                throws NamingException {
            Object result = null;

            if (ejbRefMgr==null) {
                ejbRefMgr = habitat.getService(EjbNamingReferenceManager.class);
            }

            if (ejbRefMgr != null) {
                result = ejbRefMgr.getEJBContextObject(contextType);    
            }

            if( result == null ) {
                throw new NameNotFoundException("Can not resolve EJB context of type " +
                    contextType);
            }

            return result;
        }

    }

    private static class ValidatorProxy
        implements NamingObjectProxy {
        private static final String nameForValidator = "java:comp/Validator";
        private volatile ValidatorFactory validatorFactory;
        private volatile Validator validator;
        private final Logger _logger;

        private ValidatorProxy(Logger logger) {
            this._logger = logger;
        }

        @Override
        public Object create(Context ctx)
                throws NamingException {
            String exceptionMessage = "Can not obtain reference to Validator instance ";

            // Phase 1, obtain a reference to the Validator

            // case 1, try to look in the ctx
            if (null == validator) {
                try {
                    validator = (Validator) ctx.lookup(nameForValidator);
                } catch (NamingException ne) {
                    exceptionMessage = "Unable to lookup " +
                        nameForValidator + ":" + ne.toString();
                }
            }

            // case 2, Create a new Validator instance
            if (null == validator) {

                // case 2a no validatorFactory
                if (null == validatorFactory) {
                    ValidatorFactoryProxy factoryProxy = new ValidatorFactoryProxy(_logger);
                    validatorFactory = (ValidatorFactory) factoryProxy.create(ctx);
                }

                // Use the ValidatorFactory to create a Validator
                if (null != validatorFactory) {
                    ValidatorContext validatorContext = validatorFactory.usingContext();
                    validator = validatorContext.getValidator();
                }
            }

            if( validator == null ) {
                throw new NameNotFoundException(exceptionMessage);
            }

            return validator;
        }

    }

    private static class ValidatorFactoryProxy implements NamingObjectProxy {
        private static final String nameForValidatorFactory = "java:comp/ValidatorFactory";
        private volatile ValidatorFactory validatorFactory;
        private final Logger _logger;

        private ValidatorFactoryProxy(Logger logger) {
            _logger = logger;
        }

        @Override
        public Object create(Context ctx)
                throws NamingException {
            // Phase 1, obtain a reference to the ValidatorFactory

            // case 1, try to look in the ctx
            if (null == validatorFactory) {
                try {
                    validatorFactory = (ValidatorFactory)
                            ctx.lookup(nameForValidatorFactory);
                } catch (NamingException ne) {  //ignore
                }
            }

            // case 2, create the ValidatorFactory using the spec.
            if (null == validatorFactory) {
                try {
                    validatorFactory = Validation.buildDefaultValidatorFactory();
                } catch (ValidationException e) {
                    _logger.log(Level.WARNING,
                            "Unable to lookup {0}, or build a default Bean Validator Factory: {1}",
                            new Object[]{nameForValidatorFactory, e});
                    NameNotFoundException ne = new NameNotFoundException();
                    ne.initCause(e);
                    throw ne;
                }
            }

            return validatorFactory;
        }
    }

    private class WebServiceRefProxy
            implements NamingObjectProxy {


        private WebServiceReferenceManager wsRefMgr;
        private ServiceReferenceDescriptor serviceRef;

        WebServiceRefProxy(ServiceReferenceDescriptor servRef) {

            this.serviceRef = servRef;
        }

        public Object create(Context ctx)
                throws NamingException {
            Object result = null;

            wsRefMgr = habitat.getService(WebServiceReferenceManager.class);
            if (wsRefMgr != null )  {
                result = wsRefMgr.resolveWSReference(serviceRef,ctx);
            } else {
                //A potential cause for this is this is a web.zip and the corresponding
                //metro needs to be dowloaded from UC
                _logger.log (Level.SEVERE,
                        "Cannot find the following class to proceed with @WebServiceRef" + wsRefMgr +
                                "Please confirm if webservices module is installed ");
            }

            if( result == null ) {
                throw new NameNotFoundException("Can not resolve webservice context of type " +
                        serviceRef.getName());
            }

            return result;
        }

    }

    private class EjbReferenceProxy
        implements NamingObjectProxy {

        private EjbReferenceDescriptor ejbRef;

        private volatile EjbNamingReferenceManager ejbRefMgr;

        private volatile Object cachedResult = null;

        private Boolean cacheable;

        // Note : V2 had a limited form of ejb-ref caching.  It only applied
        // to EJB 2.x Home references where the target lived in the same application
        // as the client.  It's not clear how useful that even is and it's of limited
        // value given the behavior is different for EJB 3.x references.  For now,
        // all ejb-ref caching is turned off.

        EjbReferenceProxy(EjbReferenceDescriptor ejbRef) {
            this.ejbRef = ejbRef;
        }

        public Object create(Context ctx)
                throws NamingException {

            Object result = null;
            if (ejbRefMgr == null) {
                synchronized (this) {
                    if (ejbRefMgr == null) {
                        ejbRefMgr = habitat.getService(EjbNamingReferenceManager.class);
                        cacheable = ejbRefMgr.isEjbReferenceCacheable(ejbRef);
                    }
                }
            }

            if (ejbRefMgr != null) {
                if ((cacheable != null) && (cacheable.booleanValue() == true)) {
                    if (cachedResult != null) {
                        result = cachedResult;
                    } else {
                        result = cachedResult = ejbRefMgr.resolveEjbReference(ejbRef, ctx);
                    }
                } else {
                    result = ejbRefMgr.resolveEjbReference(ejbRef, ctx);
                }
            }

            if( result == null ) {
                throw new NameNotFoundException("Can not resolve ejb reference " + ejbRef.getName() +
                    " : " + ejbRef);
            }

            return result;
        }
    }

    private static class CompEnvBinding
        implements JNDIBinding {

        private String name;
        private Object value;

        CompEnvBinding(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }

    }

    private enum ScopeType {

        COMPONENT,
        MODULE,
        APP,
        GLOBAL
    }

}
