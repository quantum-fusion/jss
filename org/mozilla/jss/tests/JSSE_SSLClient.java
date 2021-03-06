/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.jss.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This program connects to any SSL Server to exercise
 * all ciphers supported by JSSE for a given JDK/JRE
 * version.  The result is listing of common ciphers
 * between the server and this JSSE client.
 *
 */
public class JSSE_SSLClient {

    public static Logger logger = LoggerFactory.getLogger(JSSE_SSLClient.class);

    // Local members
    private String  sslRevision         = "TLS";
    private String  host                = null;
    private int     port                = -1;
    private String  cipherName          = null;
    private String  path                = null;
    private int     debug_level         = 0;
    private String  EOF                 = "test";
    private String  keystoreLoc         = "rsa.pfx";
    private SSLSocketFactory    factory  = null;

    /* ciphersuites to test */
    private ArrayList<String> ciphersToTest      = new ArrayList<>();

    /* h_ciphers is for ciphersuite that were able to successfully
     * connect to the server */
    private ArrayList<String> h_ciphers          = new ArrayList<>();

    /* f_ciphers is for ciphersuite that failed to connect to the server */
    private ArrayList<String> f_ciphers          = new ArrayList<>();

    private boolean bVerbose             = false;
    private boolean bFipsMode            = false;


    /**
     * Set the protocol type and revision
     * @param fSslRevision
     */
    public void setSslRevision(String fSslRevision) {

        if (!(fSslRevision.equals("TLS") || fSslRevision.equals("SSLv3"))) {
            logger.error("type must equal \'TLS\' or \'SSLv3\'\n");
            System.exit(1);
        }
        this.sslRevision = fSslRevision;
    }

    /**
     * Set the host name to connect to.
     * @param fHost
     */
    public void setHost(String fHost) {
        this.host = fHost;
    }

    /**
     * Set the port number to connect to.
     * @param fPort
     */
    public void setPort(int fPort) {
        this.port = fPort;
    }

    /**
     * Set the cipher suite name to use.
     * @param fCipherSuite
     */
    public void setCipherSuite(String fCipherSuite) {
        this.cipherName = fCipherSuite;
    }

    /**
     * Set the location of rsa.pfx
     * @param fKeystoreLoc
     */
    public void setKeystoreLoc(String fKeystoreLoc) {
        keystoreLoc = fKeystoreLoc + "/" + keystoreLoc;
    }

    /**
     * Get the location of rsa.pfx
     * @return String fKeystoreLoc
     */
    public String getKeystoreLoc() {
        return keystoreLoc;
    }

    /**
     * Default constructor.
     */
    public JSSE_SSLClient() {
        //Do nothing.
    }

    public boolean isServerAlive() {
        boolean isServerAlive = false;
        SSLSocket           socket   = null;
        if (factory == null) {
            initSocketFactory();
        }
        for (int i = 0 ; i < 20 ; i++) {
            try {

                Thread.sleep(1000);
                logger.info("Testing Connection:" + host + ":" + port);
                socket = (SSLSocket)factory.createSocket(host, port);
                socket.setEnabledCipherSuites(factory.getDefaultCipherSuites());

                if (socket.isBound()) {
                    logger.info("connect isBound");
                    isServerAlive = true;
                    socket.close();
                    break;
                }

            }  catch (java.net.ConnectException ex) {
                //not able to connect
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }

        return isServerAlive;
    }
    /**
     * Test communication with SSL server S
     */
    public void testCiphersuites() {
        SSLSocket           socket   = null;
        int i = 0;
        if (factory == null) {
            initSocketFactory();
        }

        if (!isServerAlive()) {
            logger.error("Unable to connect to " + host + ":" + port + " exiting.");
            System.exit(1);
        }
        Iterator<String> iter = ciphersToTest.iterator();
        while (iter.hasNext()) {
            String cs = iter.next();
            String ciphers[] = {cs};
            try {
                socket = (SSLSocket)factory.createSocket(host, port);
                socket.setEnabledCipherSuites(ciphers);
                testSSLSocket(socket, cs, i++);
            } catch (Exception ex) {
                logger.warn("failed ciphersuite" + ciphers[0]);
                f_ciphers.add(ciphers[0]);
            }
        }
    }


    public void configureCipherSuites(String server, String CipherSuite) {

        boolean testCipher = true;

        if (factory == null) {
            initSocketFactory();
        }

        String ciphers[] = factory.getSupportedCipherSuites();

        for (int i = 0; i < ciphers.length;  ++i) {
            String ciphersuite = ciphers[i];
            testCipher = true;
            if (bVerbose) {
                System.out.print(ciphersuite);
            }
            if (server.equalsIgnoreCase("JSS")) {
                //For JSS SSLServer don't test
                if ((ciphersuite.indexOf("_DHE_") != -1)||
                        (ciphersuite.indexOf("_DES40_") != -1) ||
                        (ciphersuite.indexOf("TLS_EMPTY_RENEGOTIATION_INFO_SCSV") != -1) ||
                        (ciphersuite.indexOf("SHA256") != -1) || // reenable in bug 776597
                        (ciphersuite.indexOf("_anon_") != -1) ||
                        (ciphersuite.indexOf("_KRB5_") != -1)) {
                    if (bVerbose) System.out.print(" -");
                    testCipher = false;
                }
            }
            if (server.equalsIgnoreCase("JSSE")) {
                //For JSSE SSLServers don't test _DHE_, _EXPORT_, _anon_, _KRB5_
                /*
                if ((ciphersuite.indexOf("_DHE_") != -1) ||
                    (ciphersuite.indexOf("_EXPORT_") != -1) ||
                    (ciphersuite.indexOf("_anon_") != -1) ||
                    (ciphersuite.indexOf("_KRB5_") != -1) ) {
                    if (bVerbose) System.out.print(" -");
                    testCipher = false;
                }
                 */
            }

            if (testCipher) {
                ciphersToTest.add(ciphers[i]);
                if (bVerbose) System.out.print(" - Testing");
            }
        }

        if (bVerbose) System.out.print("\n");

        if(bVerbose) System.out.println("\nTesting " + ciphersToTest.size() +
                " ciphersuites.");

    }

    private void initSocketFactory() {

        SSLContext          ctx      = null;
        KeyManagerFactory   kmf      = null;
        TrustManagerFactory tmf      = null;
        KeyStore            ks       = null;
        KeyStore            ksTrust  = null;
        String              provider = "SunJCE";



        /*
         * Set up a key manager for client authentication
         * if asked by the server.  Use the implementation's
         * default TrustStore and secureRandom routines.
         */
        char[] passphrase      = "m1oZilla".toCharArray();
        try {


            String javaVendor      = System.getProperty("java.vendor");
            logger.debug("JSSE_SSLClient: java.vendor: " + javaVendor);

            // Initialize the system
            if (javaVendor.equals("IBM Corporation")) {
                System.setProperty("java.protocol.handler.pkgs",
                        "com.ibm.net.ssl.www.protocol.Handler");
                java.security.Security.addProvider((java.security.Provider)
                Class.forName("com.ibm.jsse2.IBMJSSEProvider2").newInstance());
                provider = "IBMJCE";
            } else {
                System.setProperty("java.protocol.handler.pkgs",
                        "com.sun.net.ssl.internal.www.protocol");
                java.security.Security.addProvider((java.security.Provider)
                Class.forName("com.sun.crypto.provider.SunJCE").newInstance());
            }

            // Load the keystore that contains the certificate
            String certificate = new String("SunX509");
            ks  = KeyStore.getInstance("PKCS12");
            if (javaVendor.equals("IBM Corporation")) {
                certificate = new String("IbmX509");
                ks  = KeyStore.getInstance("PKCS12", provider);
            }

            try {
                kmf = KeyManagerFactory.getInstance(certificate);

                try (FileInputStream in = new FileInputStream(getKeystoreLoc())) {
                    ks.load(in, passphrase);
                }

            } catch (Exception keyEx) {
                if (System.getProperty("java.vendor").equals("IBM Corporation")) {
                    logger.error("Using IBM JDK: Cannot load keystore due "+
                            "to strong security encryption settings\nwith limited " +
                            "Jurisdiction policy files :\n" +
                            "http://www-1.ibm.com/support/docview.wss?uid=swg21169931");
                    System.exit(0);
                } else {
                    logger.error(keyEx.getMessage(), keyEx);
                }
                throw keyEx;
            }
            kmf.init(ks, passphrase);

            // trust manager that trusts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[]
                            getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] chain,
                            String authType) {}
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] chain,
                            String authType) {}
                }
            };

            ctx = SSLContext.getInstance(sslRevision);
            ctx.init(kmf.getKeyManagers(), trustAllCerts, null);
            factory = ctx.getSocketFactory();

            String[] JSSE_ciphers = factory.getSupportedCipherSuites();
        } catch (KeyStoreException ex) {
            ex.printStackTrace();
        } catch (NoSuchProviderException ex) {
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        } catch (InstantiationException ex) {
            ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * sendServerShutdownMsg
     */
    public void sendServerShutdownMsg() {
        try {
            SSLSocket           socket   = null;
            if (factory == null) {
                initSocketFactory();
            }

            socket = (SSLSocket)factory.createSocket(host, port);
            socket.setEnabledCipherSuites(factory.getDefaultCipherSuites());


            if (bVerbose) {
                logger.info("Sending shutdown message to server.");
            }
            socket.startHandshake();
            OutputStream os    = socket.getOutputStream();
            PrintWriter out    = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(os)));
            out.println("shutdown");
            out.flush();
            out.close();
            socket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private void testSSLSocket(SSLSocket socket, String ciphersuite,
            int socketID) {
            /*
             * register a callback for handshaking completion event
             */
        try {
            socket.addHandshakeCompletedListener(
                    new HandshakeCompletedListener() {
                public void handshakeCompleted(
                        HandshakeCompletedEvent event) {
                    h_ciphers.add(event.getCipherSuite());
                    logger.info(event.getCipherSuite());
                    logger.info("SessionId " + event.getSession() +
                                " Test Status : PASS");
                }
            }
            );
        } catch (Exception handshakeEx) {
            logger.error(handshakeEx.getMessage(), handshakeEx);
            System.exit(1);
        }

        try {
            // Set socket timeout to 10 sec
            socket.setSoTimeout(10 * 1000);
            socket.startHandshake();

            String outputLine  = null;
            String inputLine   = null;
            InputStream  is    = socket.getInputStream();
            OutputStream os    = socket.getOutputStream();
            BufferedReader bir = new BufferedReader(
                    new InputStreamReader(is));
            PrintWriter out;
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os)));

            //write then read on the connection once.
            outputLine = ciphersuite + ":" + socketID + "\n";
            if (bVerbose) {
                logger.info("Sending: " + outputLine);
            }
            out.print(outputLine);
            out.flush();
            inputLine = bir.readLine();
            if (bVerbose) {
                logger.info("Received: " + inputLine + " on Client-" + socketID);
            }
            bir.close();
            out.close();
        } catch (SSLHandshakeException ex) {
            f_ciphers.add(ciphersuite);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            System.exit(1);
        }
        try {
            socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }



    public void outputCipherResults() {
        String banner = new String
                ("\n----------------------------------------------------------\n");

        logger.info(banner);
        logger.info("JSSE has " +
                factory.getSupportedCipherSuites().length + " ciphersuites and " +
                ciphersToTest.size() + " were configured and tested.");

        if (ciphersToTest.size() == h_ciphers.size()) {
            logger.info("All " + ciphersToTest.size() +
                    " configured ciphersuites tested Successfully!\n");
        }

        if (!h_ciphers.isEmpty()) {
            if (!f_ciphers.isEmpty()) {
                logger.info(banner);
                logger.info(h_ciphers.size() +
                        " ciphersuites successfully connected to the "+
                        "server\n");
            }
            Iterator<String> iter = h_ciphers.iterator();
            while (iter.hasNext()) {
                logger.info(iter.next());

            }
        }
        if (bFipsMode) {
            logger.info("Note: ciphersuites that have the prefix " +
                    "\"SSL\" or \"SSL3\" were used in TLS mode.");
        }

        if (ciphersToTest.size() != (h_ciphers.size() + f_ciphers.size())) {
            logger.warn("did not test all expected ciphersuites");
        }
        if (!f_ciphers.isEmpty()) {
            logger.info(banner);
            logger.info(f_ciphers.size() +
                    " ciphersuites that did not connect to the "+
                    "server\n\n");
            Iterator<String> iter = f_ciphers.iterator();
            while (iter.hasNext()) {
                logger.info(iter.next());

            }
            logger.error("we should have no failed ciphersuites!");
            System.exit(1);
        }

        logger.info(banner);

    }




    /**
     * Main method for local unit testing.
     */
    public static void main(String [] args) {

        String testCipher       = null;
        String testHost         = "localhost";
        String keystoreLocation = "rsa.pfx";
        int    testPort         = 29750;
        String serverType       = "JSSE";
        String usage            = "java org.mozilla.jss.tests.JSSE_SSLClient" +
                "\n<keystore location> " +
                "<test port> <test host> <server type> <test cipher>";

        try {
            if ( args[0].toLowerCase().equals("-h") || args.length < 1) {
                System.out.println(usage);
                System.exit(1);
            }

            if ( args.length >= 1 ) {
                keystoreLocation = args[0];
            }
            if ( args.length >= 2) {
                testPort         = new Integer(args[1]).intValue();
                System.out.println("using port: " + testPort);
            }
            if ( args.length >= 3) {
                testHost       = args[2];
            }
            if ( args.length == 4) {
                serverType         = args[3];
            }
            if ( args.length == 5) {
                testCipher         = args[4];
            }
        } catch (Exception e) {
            System.out.println(usage);
            System.exit(1);
        }

        JSSE_SSLClient sslSock = new JSSE_SSLClient();

        sslSock.setHost(testHost);
        sslSock.setPort(testPort);
        sslSock.setKeystoreLoc(keystoreLocation);

        sslSock.setCipherSuite(testCipher);
        sslSock.configureCipherSuites(serverType, testCipher);
        try {
            sslSock.testCiphersuites();
        } catch (Exception e) {
            logger.error("Exception caught testing ciphersuites: " + e.getMessage(), e);
            System.exit(1);
        }
        sslSock.sendServerShutdownMsg();
        sslSock.outputCipherResults();


        System.exit(0);
    }
}
