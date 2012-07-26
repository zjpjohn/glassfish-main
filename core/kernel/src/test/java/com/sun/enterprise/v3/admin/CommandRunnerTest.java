/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.v3.admin;

import static org.junit.Assert.*;

import org.glassfish.common.util.admin.CommandModelImpl;
import org.junit.Test;
import org.junit.Before;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import org.glassfish.api.Param;
import org.glassfish.api.admin.ParameterMap;

import java.lang.reflect.AnnotatedElement;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandModel;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.ComponentException;

/**
 * junit test to test CommandRunner class
 */
public class CommandRunnerTest {
    private CommandRunnerImpl cr = null;

    @Test
    public void getUsageTextTest() {
        String expectedUsageText = "Usage: dummy-admin --foo=foo [--bar=false] --hello=there world ";
        DummyAdminCommand dac = new DummyAdminCommand();
        CommandModel model = new CommandModelImpl(DummyAdminCommand.class);
        String actualUsageText = cr.getUsageText(dac, model);
        assertEquals(expectedUsageText, actualUsageText);
    }

    @Test
    public void validateParametersTest() {
        ParameterMap params = new ParameterMap();
        params.set("foo", "bar");
        params.set("hello", "world");
        params.set("one", "two");
        try {
            cr.validateParameters(new CommandModelImpl(DummyAdminCommand.class), params);
        }
        catch (ComponentException ce) {
            String expectedMessage = " Invalid option: one";
            assertEquals(expectedMessage, ce.getMessage());
        }
    }

    @Test
    public void skipValidationTest() {
        DummyAdminCommand dac = new DummyAdminCommand();
        assertFalse(cr.skipValidation(dac));
        SkipValidationCommand svc = new SkipValidationCommand();
        assertTrue(cr.skipValidation(svc));        
    }
    
    @Before
    public void setup() {
        cr = new CommandRunnerImpl();
    }

        //mock-up DummyAdminCommand object
    @Service(name="dummy-admin")
    public class DummyAdminCommand implements AdminCommand {
        @Param(optional=false)
        String foo;

        @Param(name="bar", defaultValue="false", optional=true)
        String foobar;

        @Param(optional=false, defaultValue="there")
        String hello;

        @Param(optional=false, primary=true)
        String world;
            
        public void execute(AdminCommandContext context) {}
    }

        //mock-up SkipValidationCommand
    public class SkipValidationCommand implements AdminCommand {
        boolean skipParamValidation=true;
        public void execute(AdminCommandContext context) {}        
    }
    

}