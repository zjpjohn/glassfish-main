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

package com.sun.enterprise.web;


import com.sun.enterprise.web.session.PersistenceType;
import com.sun.logging.LogDomains;
import org.apache.catalina.Context;
import org.glassfish.hk2.Services;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
  * @author Rajiv Mordani
  */

public class PersistenceStrategyBuilderFactory {
    
    private static final Logger _logger = LogDomains.getLogger(
            PersistenceStrategyBuilderFactory.class, LogDomains.WEB_LOGGER);

    Services services;



    /**
     * Constructor.
     */
    public PersistenceStrategyBuilderFactory(
            ServerConfigLookup serverConfigLookup, Services services) {

        this.services = services;
    }


    /**
     * creates the correct implementation of PersistenceStrategyBuilder
     * if an invalid combination is input; an error is logged
     * and MemoryStrategyBuilder is returned
     */    
    public PersistenceStrategyBuilder createPersistenceStrategyBuilder(
            String persistenceType, String frequency, String scope,
            Context ctx) {
        String resolvedPersistenceFrequency = null;
        String resolvedPersistenceScope = null;

        if (persistenceType.equalsIgnoreCase(PersistenceType.MEMORY.getType()) ||
                persistenceType.equalsIgnoreCase(PersistenceType.FILE.getType()) ||
                persistenceType.equalsIgnoreCase(PersistenceType.COOKIE.getType())) {
            // Deliberately leaving frequency & scope null
        } else {
            resolvedPersistenceFrequency = frequency;
            resolvedPersistenceScope = scope;
        }

        if (_logger.isLoggable(Level.FINEST)) {
            _logger.finest("resolvedPersistenceType = " +
                           persistenceType);
            _logger.finest("resolvedPersistenceFrequency = " +
                           resolvedPersistenceFrequency);
            _logger.finest("resolvedPersistenceScope = " +
                           resolvedPersistenceScope);
        }

        PersistenceStrategyBuilder builder = services.forContract(PersistenceStrategyBuilder.class).named(persistenceType).get();
        if (builder == null) {
            builder = new MemoryStrategyBuilder();
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("Could not find PersistentStrategyBuilder for persistenceType  " + persistenceType);
            }
        } else {
                if (_logger.isLoggable(Level.FINEST)) {
                    _logger.finest(
                    "PersistenceStrategyBuilderFactory>>createPersistenceStrategyBuilder: "
                    + "CandidateBuilderClassName = " + builder.getClass());
                }

              builder.setPersistenceFrequency(frequency);
              builder.setPersistenceScope(scope);
              builder.setPassedInPersistenceType(persistenceType);
          }
        return builder;
    } 

    /**
     * returns the application id for the module
     *
     * @param ctx the context
     */    

    public String getApplicationId(Context ctx) {
        return ((com.sun.enterprise.web.WebModule)ctx).getID();
    }    

}