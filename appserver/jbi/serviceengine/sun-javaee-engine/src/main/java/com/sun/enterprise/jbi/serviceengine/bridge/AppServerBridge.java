/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.jbi.serviceengine.bridge;

import com.sun.enterprise.jbi.serviceengine.comm.MessageProcessor;
import com.sun.enterprise.jbi.serviceengine.core.Bridge;
import com.sun.enterprise.jbi.serviceengine.core.DescriptorEndpointInfo;
import com.sun.enterprise.jbi.serviceengine.core.EndpointRegistry;
import com.sun.enterprise.jbi.serviceengine.core.ServiceEngineEndpoint;
import com.sun.enterprise.jbi.serviceengine.core.ServiceEngineRuntimeHelper;
import javax.jbi.messaging.MessageExchange;
import javax.xml.namespace.QName;

/**
 * Bridge is initialized during server startup.
 * @author Manisha Umbarje
 */
public class AppServerBridge implements Bridge{
    
    /** Creates a new instance of Bridge */
    public AppServerBridge() {
    }
    
    public void initialize() {
        ServiceEngineRtObjectFactory.getInstance().
                setFacade(ServiceEngineFacadeImpl.getInstance());
        
        //Instantiates Endpoint Helper
        EndpointHelper.getInstance().initialize();
        EndpointRegistry registry = EndpointRegistry.getInstance();

        EndpointInfoCollector endpointInfoCollector = ServiceEngineRuntimeHelper.getRuntime().getEndpointInfoCollector();
        endpointInfoCollector.initialize();
        endpointInfoCollector.mergeEndpointRegistry(registry.getWSEndpoints());
    }
    
    public MessageProcessor getMessageProcessor(MessageExchange me) {
        
        QName jbiServiceName = me.getEndpoint().getServiceName();
        String jbiEndpointName = me.getEndpoint().getEndpointName();
        
        String key = DescriptorEndpointInfo.getDEIKey(
                jbiServiceName, jbiEndpointName);
        DescriptorEndpointInfo dei = EndpointRegistry.getInstance().
                getWSDLEndpts().get(key);
        
        QName actualServiceName = dei !=null ? dei.getServiceName() : jbiServiceName;
        String actualEndpointName = dei != null ? dei.getEndpointName() : jbiEndpointName;
        
        ServiceEngineEndpoint seEndpoint =  EndpointRegistry.getInstance().
                get(actualServiceName, actualEndpointName);
        
        if(seEndpoint.isJAXWSEndpoint()) {
            return new JAXWSMessageProcessor();
        } else
            return new JAXRPCMessageProcessor();
    }
        
    public void destroy() {
        EndpointHelper.getInstance().destroy();
    }
}
