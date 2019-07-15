/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.config13.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.ws.microprofile.config.fat.repeat.RepeatConfigActions;
import com.ibm.ws.microprofile.config.interfaces.ConfigConstants;

import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.rules.repeater.RepeatTests;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;

/**
 * Example Shrinkwrap FAT project:
 * <li> Application packaging is done in the @BeforeClass, instead of ant scripting.
 * <li> Injects servers via @Server annotation. Annotation value corresponds to the
 * server directory name in 'publish/servers/%annotation_value%' where ports get
 * assigned to the LibertyServer instance when the 'testports.properties' does not
 * get used.
 * <li> Specifies an @RunWith(FATRunner.class) annotation. Traditionally this has been
 * added to bytecode automatically by ant.
 * <li> Uses the @TestServlet annotation to define test servlets. Notice that no @Test
 * methods are defined in this class. All of the @Test methods are defined on the test
 * servlet referenced by the annotation, and will be run whenever this test class runs.
 */
@RunWith(FATRunner.class)
public class HotAddMPConfig extends FATServletClient {

    public static final String APP_NAME = "hotAddMPConfigApp";

    @Server("HotAddMPConfig")
    //@TestServlet(servlet = MapEnvVarServlet.class, contextRoot = APP_NAME)
    public static LibertyServer server;

    @ClassRule
    public static RepeatTests r = RepeatConfigActions.repeatConfig13("HotAddMPConfig");

    @BeforeClass
    public static void setUp() throws Exception {
        // Create a WebArchive that will have the file name 'app1.war' once it's written to a file
        // Include the 'app1.web' package and all of it's java classes and sub-packages
        // Automatically includes resources under 'test-applications/APP_NAME/resources/' folder
        // Exports the resulting application to the ${server.config.dir}/apps/ directory
        ShrinkHelper.defaultApp(server, APP_NAME, "com.ibm.ws.microprofile.config.hotadd.*");
        server.copyFileToLibertyServerRoot("HotAddMPConfig/noMPConfig/HotAddMPConfig.xml");
        server.copyFileToLibertyServerRoot("HotAddMPConfig/withApp/HotAddApp.xml");
        server.startServerAndValidate(true, true, false); //don't validate because the app won't have started properly
        //server.startServer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer("CWNEN0047W.*HotAddMPConfigServlet");
    }

    /**
     * Copy a server config file to the server root and wait for notification that the server config has been updated
     *
     * @param filename
     * @throws Exception
     */
    private static void copyConfigFileToLibertyServerRoot(String filename, String appName) throws Exception {
        server.setMarkToEndOfLog();
        server.copyFileToLibertyServerRoot(filename);

        if (appName == null) {
            server.waitForConfigUpdateInLogUsingMark(null, false);
        } else {
            server.waitForConfigUpdateInLogUsingMark(Collections.singleton(appName), false);
        }

        Thread.sleep(ConfigConstants.DEFAULT_DYNAMIC_REFRESH_INTERVAL * 2); // We need this pause so that the MP config change is picked up through the polling mechanism
    }

//    @Before
//    public void resetConfigFile() throws Exception {
//        copyConfigFileToLibertyServerRoot("HotAddMPConfig/noMPConfig/HotAddMPConfig.xml", null);
//        copyConfigFileToLibertyServerRoot("HotAddMPConfig/withApp/HotAddApp.xml", null);
//    }

    @Test
    public void testHotAddMPConfig() throws Exception {
        copyConfigFileToLibertyServerRoot("HotAddMPConfig/withMPConfig/HotAddMPConfig.xml", null);
        //copyConfigFileToLibertyServerRoot("HotAddMPConfig/withApp/HotAddApp.xml", APP_NAME);

        test(server, "/hotAddMPConfigApp/HotAddMPConfigServlet?testMethod=configTest");
    }

    private void test(LibertyServer server, String testUri) throws Exception {
        HttpURLConnection con = null;
        try {
            URL url = new URL("http://" + server.getHostname() + ":" + server.getHttpDefaultPort() + testUri);
            con = (HttpURLConnection) url.openConnection();
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);
            con.setRequestMethod("GET");
            InputStream is = con.getInputStream();
            assertNotNull(is);

            String output = read(is);
            assertTrue(output, output.trim().startsWith("SUCCESS"));
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private static String read(InputStream in) throws IOException {
        InputStreamReader isr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }
        return builder.toString();
    }

}
