package com.firefly.net.tcp.secure.openssl.nativelib;

import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;
import io.netty.internal.tcnative.SniHostNameMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static com.firefly.net.tcp.secure.openssl.nativelib.ObjectUtil.checkNotNull;


/**
 * A server-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 * <p>Instances of this class must be {@link #release() released} or else native memory will leak!
 * <p>
 * <p>Instances of this class <strong>must not</strong> be released before any {@link ReferenceCountedOpenSslEngine}
 * which depends upon the instance of this class is released. Otherwise if any method of
 * {@link ReferenceCountedOpenSslEngine} is called which uses this class's JNI resources the JVM may crash.
 */
public final class ReferenceCountedOpenSslServerContext extends ReferenceCountedOpenSslContext {
    private static final Logger logger = LoggerFactory.getLogger("firefly-system");
    private static final byte[] ID = {'n', 'e', 't', 't', 'y'};
    private final OpenSslServerSessionContext sessionContext;
    private final OpenSslKeyMaterialManager keyMaterialManager;

    ReferenceCountedOpenSslServerContext(
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, String[] protocols, boolean startTls,
            boolean enableOcsp) throws SSLException {
        this(trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword, keyManagerFactory, ciphers,
                cipherFilter, toNegotiator(apn), sessionCacheSize, sessionTimeout, clientAuth, protocols, startTls,
                enableOcsp);
    }

    private ReferenceCountedOpenSslServerContext(
            X509Certificate[] trustCertCollection, TrustManagerFactory trustManagerFactory,
            X509Certificate[] keyCertChain, PrivateKey key, String keyPassword, KeyManagerFactory keyManagerFactory,
            Iterable<String> ciphers, CipherSuiteFilter cipherFilter, OpenSslApplicationProtocolNegotiator apn,
            long sessionCacheSize, long sessionTimeout, ClientAuth clientAuth, String[] protocols, boolean startTls,
            boolean enableOcsp) throws SSLException {
        super(ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, SSL.SSL_MODE_SERVER, keyCertChain,
                clientAuth, protocols, startTls, enableOcsp, true);
        // Create a new SSL_CTX and configure it.
        boolean success = false;
        try {
            ServerContext context = newSessionContext(this, ctx, engineMap, trustCertCollection, trustManagerFactory,
                    keyCertChain, key, keyPassword, keyManagerFactory);
            sessionContext = context.sessionContext;
            keyMaterialManager = context.keyMaterialManager;
            success = true;
        } finally {
            if (!success) {
                release();
            }
        }
    }

    @Override
    public OpenSslServerSessionContext sessionContext() {
        return sessionContext;
    }

    @Override
    OpenSslKeyMaterialManager keyMaterialManager() {
        return keyMaterialManager;
    }

    static final class ServerContext {
        OpenSslServerSessionContext sessionContext;
        OpenSslKeyMaterialManager keyMaterialManager;
    }

    static ServerContext newSessionContext(ReferenceCountedOpenSslContext thiz, long ctx, OpenSslEngineMap engineMap,
                                           X509Certificate[] trustCertCollection,
                                           TrustManagerFactory trustManagerFactory,
                                           X509Certificate[] keyCertChain, PrivateKey key,
                                           String keyPassword, KeyManagerFactory keyManagerFactory)
            throws SSLException {
        ServerContext result = new ServerContext();
        try {
            SSLContext.setVerify(ctx, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);
            if (!OpenSsl.useKeyManagerFactory()) {
                if (keyManagerFactory != null) {
                    throw new IllegalArgumentException(
                            "KeyManagerFactory not supported");
                }
                checkNotNull(keyCertChain, "keyCertChain");

                setKeyMaterial(ctx, keyCertChain, key, keyPassword);
            } else {
                // javadocs state that keyManagerFactory has precedent over keyCertChain, and we must have a
                // keyManagerFactory for the server so build one if it is not specified.
                if (keyManagerFactory == null) {
                    keyManagerFactory = buildKeyManagerFactory(
                            keyCertChain, key, keyPassword, keyManagerFactory);
                }
                X509KeyManager keyManager = chooseX509KeyManager(keyManagerFactory.getKeyManagers());
                result.keyMaterialManager = useExtendedKeyManager(keyManager) ?
                        new OpenSslExtendedKeyMaterialManager(
                                (X509ExtendedKeyManager) keyManager, keyPassword) :
                        new OpenSslKeyMaterialManager(keyManager, keyPassword);
            }
        } catch (Exception e) {
            throw new SSLException("failed to set certificate and key", e);
        }
        try {
            if (trustCertCollection != null) {
                trustManagerFactory = buildTrustManagerFactory(trustCertCollection, trustManagerFactory);
            } else if (trustManagerFactory == null) {
                // Mimic the way SSLContext.getInstance(KeyManager[], null, null) works
                trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
            }

            final X509TrustManager manager = chooseTrustManager(trustManagerFactory.getTrustManagers());

            // IMPORTANT: The callbacks set for verification must be static to prevent memory leak as
            //            otherwise the context can never be collected. This is because the JNI code holds
            //            a global reference to the callbacks.
            //
            //            See https://github.com/netty/netty/issues/5372

            // Use this to prevent an error when running on java < 7
            if (useExtendedTrustManager(manager)) {
                SSLContext.setCertVerifyCallback(ctx,
                        new ExtendedTrustManagerVerifyCallback(engineMap, (X509ExtendedTrustManager) manager));
            } else {
                SSLContext.setCertVerifyCallback(ctx, new TrustManagerVerifyCallback(engineMap, manager));
            }

            X509Certificate[] issuers = manager.getAcceptedIssuers();
            if (issuers != null && issuers.length > 0) {
                long bio = 0;
                try {
                    bio = toBIO(issuers);
                    if (!SSLContext.setCACertificateBio(ctx, bio)) {
                        throw new SSLException("unable to setup accepted issuers for trustmanager " + manager);
                    }
                } finally {
                    freeBio(bio);
                }
            }

            if (PlatformDependent.javaVersion() >= 8) {
                // Only do on Java8+ as SNIMatcher is not supported in earlier releases.
                // IMPORTANT: The callbacks set for hostname matching must be static to prevent memory leak as
                //            otherwise the context can never be collected. This is because the JNI code holds
                //            a global reference to the matcher.
                SSLContext.setSniHostnameMatcher(ctx, new OpenSslSniHostnameMatcher(engineMap));
            }
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException("unable to setup trustmanager", e);
        }

        result.sessionContext = new OpenSslServerSessionContext(thiz);
        result.sessionContext.setSessionIdContext(ID);
        return result;
    }

    private static final class TrustManagerVerifyCallback extends AbstractCertificateVerifier {
        private final X509TrustManager manager;

        TrustManagerVerifyCallback(OpenSslEngineMap engineMap, X509TrustManager manager) {
            super(engineMap);
            this.manager = manager;
        }

        @Override
        void verify(ReferenceCountedOpenSslEngine engine, X509Certificate[] peerCerts, String auth)
                throws Exception {
            manager.checkClientTrusted(peerCerts, auth);
        }
    }

    private static final class ExtendedTrustManagerVerifyCallback extends AbstractCertificateVerifier {
        private final X509ExtendedTrustManager manager;

        ExtendedTrustManagerVerifyCallback(OpenSslEngineMap engineMap, X509ExtendedTrustManager manager) {
            super(engineMap);
            this.manager = manager;
        }

        @Override
        void verify(ReferenceCountedOpenSslEngine engine, X509Certificate[] peerCerts, String auth)
                throws Exception {
            manager.checkClientTrusted(peerCerts, auth, engine);
        }
    }

    private static final class OpenSslSniHostnameMatcher implements SniHostNameMatcher {
        private final OpenSslEngineMap engineMap;

        OpenSslSniHostnameMatcher(OpenSslEngineMap engineMap) {
            this.engineMap = engineMap;
        }

        @Override
        public boolean match(long ssl, String hostname) {
            ReferenceCountedOpenSslEngine engine = engineMap.get(ssl);
            if (engine != null) {
                return engine.checkSniHostnameMatch(hostname);
            }
            logger.warn("No ReferenceCountedOpenSslEngine found for SSL pointer: {}", ssl);
            return false;
        }
    }
}
