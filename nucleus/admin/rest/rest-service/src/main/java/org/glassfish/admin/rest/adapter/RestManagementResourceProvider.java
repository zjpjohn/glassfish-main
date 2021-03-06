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


package org.glassfish.admin.rest.adapter;

import com.sun.enterprise.config.serverbeans.Domain;
import org.glassfish.admin.rest.RestResource;
import org.glassfish.admin.rest.generator.ASMResourcesGenerator;
import org.glassfish.admin.rest.generator.ResourcesGenerator;
import org.glassfish.admin.rest.resources.GeneratorResource;
import org.glassfish.admin.rest.resources.StatusGenerator;
import org.glassfish.admin.rest.resources.custom.ManagementProxyResource;
import org.glassfish.admin.restconnector.Constants;
import org.glassfish.api.container.EndpointRegistrationException;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.internal.api.ServerContext;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.jackson.JacksonBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.jvnet.hk2.config.Dom;

import javax.security.auth.Subject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for providing ReST resources for management operations.
 */
public class RestManagementResourceProvider extends AbstractRestResourceProvider {

    public RestManagementResourceProvider() {
        super();
    }

    @Override
    public AbstractBinder getJsonBinder() {
        return new JacksonBinder();
    }

    @Override
    public String getContextRoot() {
        return Constants.REST_MANAGEMENT_CONTEXT_ROOT;
    }

    @Override
    public ResourceConfig getResourceConfig(Set<Class<?>> classes,
                                            final ServerContext sc,
                                            final ServiceLocator habitat,
                                            final Class<? extends Factory<Ref<Subject>>> subjectReferenceFactory)
            throws EndpointRegistrationException {
        ResourceConfig rc = super.getResourceConfig(classes, sc, habitat, subjectReferenceFactory);
        rc.addClasses(ExceptionFilter.class);
        return rc;
    }

    @Override
    public Set<Class<?>> getResourceClasses(ServiceLocator habitat) {
//         return getLazyJersey().getResourcesConfigForManagement(habitat);
        Class domainResourceClass = null;//org.glassfish.admin.rest.resources.generated.DomainResource.class;

        generateASM(habitat);
        try {
            domainResourceClass = Class.forName("org.glassfish.admin.rest.resources.generatedASM.DomainResource");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(RestManagementAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }

        final Set<Class<?>> r = new HashSet<Class<?>>();
        addCompositeResources(r, habitat);

        // uncomment if you need to run the generator:
//        r.add(GeneratorResource.class);
        r.add(StatusGenerator.class);
//        r.add(ClientGenerator.class);
//        r.add(ModelResource.class);
        //r.add(ActionReportResource.class);

        r.add(domainResourceClass);
//        r.add(DomainResource.class);
        r.add(ManagementProxyResource.class);
        r.add(org.glassfish.admin.rest.resources.SessionsResource.class);

        //TODO this needs to be added to all rest adapters that want to be secured. Decide on it after the discussion to unify RestAdapter is concluded
        r.add(org.glassfish.admin.rest.resources.StaticResource.class);

        //body readers, not in META-INF/services anymore
        r.add(org.glassfish.admin.rest.readers.RestModelReader.class);
        r.add(org.glassfish.admin.rest.readers.RestModelListReader.class);
        r.add(org.glassfish.admin.rest.readers.FormReader.class);
        r.add(org.glassfish.admin.rest.readers.ParameterMapFormReader.class);
        r.add(org.glassfish.admin.rest.readers.JsonHashMapProvider.class);
        r.add(org.glassfish.admin.rest.readers.JsonPropertyListReader.class);
        r.add(org.glassfish.admin.rest.readers.JsonParameterMapProvider.class);

        r.add(org.glassfish.admin.rest.readers.XmlHashMapProvider.class);
        r.add(org.glassfish.admin.rest.readers.XmlPropertyListReader.class);

        //body writers
        r.add(org.glassfish.admin.rest.provider.ActionReportResultHtmlProvider.class);
        r.add(org.glassfish.admin.rest.provider.ActionReportResultJsonProvider.class);
        r.add(org.glassfish.admin.rest.provider.ActionReportResultXmlProvider.class);

        r.add(org.glassfish.admin.rest.provider.ResponseBodyWriter.class);
        r.add(org.glassfish.admin.rest.provider.RestCollectionProvider.class);
        r.add(org.glassfish.admin.rest.provider.RestModelWriter.class);
//        r.add(ProxyMessageBodyWriter.class);


        r.add(org.glassfish.admin.rest.provider.FormWriter.class);

        r.add(org.glassfish.admin.rest.provider.GetResultListHtmlProvider.class);
        r.add(org.glassfish.admin.rest.provider.GetResultListJsonProvider.class);
        r.add(org.glassfish.admin.rest.provider.GetResultListXmlProvider.class);

        r.add(org.glassfish.admin.rest.provider.OptionsResultJsonProvider.class);
        r.add(org.glassfish.admin.rest.provider.OptionsResultXmlProvider.class);
        
        r.add(org.glassfish.jersey.media.sse.OutboundEventWriter.class);
        r.add(org.glassfish.admin.rest.provider.AdminCommandStateCmdResultJsonProvider.class);
        r.add(org.glassfish.admin.rest.provider.ProgressStatusJsonProvider.class);
        r.add(org.glassfish.admin.rest.provider.ProgressStatusEventJsonProvider.class);

        return r;
    }

    @Override
    public Map<String, Boolean> getFeatures() {
        final Map<String, Boolean> features = super.getFeatures();
        //features.put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        return features;
    }

    private void generateASM(ServiceLocator habitat) {
        try {
            Domain entity = habitat.getService(Domain.class);
            Dom dom = Dom.unwrap(entity);

            ResourcesGenerator resourcesGenerator = new ASMResourcesGenerator(habitat);
            resourcesGenerator.generateSingle(dom.document.getRoot().model, dom.document);
            resourcesGenerator.endGeneration();
        } catch (Exception ex) {
            Logger.getLogger(GeneratorResource.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void addCompositeResources(Set<Class<?>> r, ServiceLocator habitat) {
        List<ServiceHandle<RestResource>> handles = habitat.getAllServiceHandles(RestResource.class);
        for (ServiceHandle<RestResource> handle : handles) {
            ActiveDescriptor<RestResource> ad = handle.getActiveDescriptor();
            if (!ad.isReified()) {
                ad = (ActiveDescriptor<RestResource>) habitat.reifyDescriptor(ad);
            }
            r.add(ad.getImplementationClass());
        }
    }
}
