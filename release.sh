#!/bin/sh
#------------------------------------------------------   
#--	BE SURE TO HAVE THE FOLLOWING IN YOUR SETTINGS.XML
#------------------------------------------------------
#
#    <servers>
#        <server>
#            <id>jvnet-nexus-staging</id>
#            <username>jvnet_id</username>
#            <password>password</password>
#        </server>
#    </servers>

mvn -B release:prepare -DpreparationGoals="install -P\!jvnet-release"
mvn -B release:perform -Dgoals="deploy -Dhttps.proxyHost=inet-rmmc01.oracle.com -Dhttps.proxyPort=80 -Dgpg.passphrase=glassfish"
