package net.wasdev.gameon.concierge;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * A wrapped/encapsulation of outbound REST requests to the player service.
 * <p>
 * The URL for the player service is injected via CDI: {@code <jndiEntry />}
 * elements defined in server.xml maps the environment variable to the JNDI
 * value.
 * </p>
 * <p>
 * CDI will create this (the {@code PlayerClient} as an application scoped bean.
 * This bean will be created when the application starts, and can be injected
 * into other CDI-managed beans for as long as the application is valid.
 * </p>
 *
 * @see ApplicationScoped
 */
public class PlayerClient {

    /**
     * The player URL injected from JNDI via CDI.
     * 
     * @see {@code playerUrl} in
     *      {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "playerUrl")
    String playerLocation;
    
    // Keystore info for jwt parsing / creation.
    @Resource(lookup = "jwtKeyStore")
    String keyStore;
    @Resource(lookup = "jwtKeyStorePassword")
    String keyStorePW;
    @Resource(lookup = "jwtKeyStoreAlias")
    String keyStoreAlias;
    
    /** The Key to Sign JWT's with (once it's loaded) */
    private static Key signingKey = null;

    /**
     * The root target used to define the root path and common query parameters
     * for all outbound requests to the concierge service.
     *
     * @see WebTarget
     */
    WebTarget root;

    /**
     * A Trust Manager that checks nothing.. ideal for talking via SSL via self signed
     * certificates etc.
     */
    private static class NullX509TrustManager implements X509TrustManager {
     public void checkClientTrusted(X509Certificate[] chain, String authType)
       throws CertificateException {
     }
     
     public void checkServerTrusted(X509Certificate[] chain, String authType)
       throws CertificateException {
     }
     
     public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
     }
     
    }
    
    /** The one and only (thread-safe) TrustManager */
    private static X509TrustManager tm = new NullX509TrustManager();
     
    /** 
     * A hostname verifier that also doesn't check anything, ideal when talking 
     * to SSL hosts that have certificates with one hostname, that are being 
     * accessed via another hostname (eg, via docker network)
     */
    private static class NullHostnameVerifier implements HostnameVerifier {
     public boolean verify(String hostname, SSLSession session) {
      return true;
     }
    }

    /** The one and only (thread-safe) HostName Verifier */
    private static HostnameVerifier hv = new NullHostnameVerifier();
    
    /**
     * The {@code @PostConstruct} annotation indicates that this method should
     * be called immediately after the {@code ConciergeClient} is instantiated
     * with the default no-argument constructor.
     *
     * @see PostConstruct
     * @see ApplicationScoped
     */
    @PostConstruct
    public void initClient() throws IOException{
    	//detect local test environment.
    	if(System.getenv("CONCIERGE_PLAYER_URL").contains("player:9443")){
	        try {
				SSLContext context = SSLContext.getDefault();
				context = SSLContext.getInstance(context.getProtocol());
				TrustManager[] tms = { tm };
				context.init(null, tms, null);
				
		        Client client = ClientBuilder.newBuilder()
		        		.hostnameVerifier(hv)
		        		.sslContext(context)
		        		.build();
		        
		        this.root = client.target(playerLocation);
		        
			} catch (NoSuchAlgorithmException e) {
				throw new IOException(e);
			} catch (KeyManagementException e) {
				throw new IOException(e);
			}
    	}else{
	        Client client = ClientBuilder.newBuilder()
	        		.hostnameVerifier(hv)
	        		.build();
	        
	        this.root = client.target(playerLocation);
    	}
    }
    
    /**
     * Obtain the key we'll use to sign the jwts we use to talk to Player endpoints.
     *
     * @throws IOException
     *             if there are any issues with the keystore processing.
     */
    private synchronized void getKeyStoreInfo() throws IOException {
        try {
            // load up the keystore..
            FileInputStream is = new FileInputStream(keyStore);
            KeyStore signingKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
            signingKeystore.load(is, keyStorePW.toCharArray());

            // grab the key we'll use to sign
            signingKey = signingKeystore.getKey(keyStoreAlias, keyStorePW.toCharArray());

        } catch (KeyStoreException e) {
            throw new IOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        } catch (CertificateException e) {
            throw new IOException(e);
        } catch (UnrecoverableKeyException e) {
            throw new IOException(e);
        }

    }
    
    /**
     * Obtain a JWT for the player id that can be used to invoke player REST services.
     * 
     * We can create this, because the concierge has access to the private certificate 
     * required to sign such a JWT. 
     * 
     * @param playerId The id to build the JWT for
     * @return The JWT as a string.
     * @throws IOException
     */
    private String getClientJwtForId(String playerId) throws IOException{
        // grab the key if needed
        if (signingKey == null)
            getKeyStoreInfo();

        Claims onwardsClaims = Jwts.claims();

        // Set the subject using the "id" field from our claims map.
        onwardsClaims.setSubject(playerId);

        // We'll use this claim to know this is a user token
        onwardsClaims.setAudience("client");

        // we set creation time to 24hrs ago, to avoid timezone issues in the
        // browser
        // verification of the jwt.
        Calendar calendar1 = Calendar.getInstance();
        calendar1.add(Calendar.HOUR, -24);
        onwardsClaims.setIssuedAt(calendar1.getTime());

        // client JWT has 24 hrs validity from now.
        Calendar calendar2 = Calendar.getInstance();
        calendar2.add(Calendar.HOUR, 24);
        onwardsClaims.setExpiration(calendar2.getTime());

        // finally build the new jwt, using the claims we just built, signing it
        // with our signing key, and adding a key hint as kid to the encryption 
        // header, which is optional, but can be used by the receivers of the 
        // jwt to know which key they should verifiy it with.
        String newJwt = Jwts.builder().setHeaderParam("kid", "playerssl").setClaims(onwardsClaims)
                .signWith(SignatureAlgorithm.RS256, signingKey).compact();

        
        return newJwt;
    }


    
    /**
     * Obtain apiKey for player id.
     *
     * @param playerId
     *            The player id
     * @return The apiKey for the player
     */
    public String getApiKey(String playerId) throws IOException {

    	String jwt = getClientJwtForId(playerId);

        WebTarget target = this.root.path("{playerId}")
        		.resolveTemplate("playerId", playerId)
        		.queryParam("jwt",jwt);

        try {
            // Make GET request using the specified target, get result as a
            // string containing JSON
            String result = target.request().get(String.class);
            
            // Parse the JSON response, and retrieve the apiKey field value.
            ObjectMapper om = new ObjectMapper();
            JsonNode jn = om.readValue(result,JsonNode.class);
            
            return jn.get("apiKey").textValue();
        } catch (ResponseProcessingException rpe) {
        	System.out.println("Error processing response "+rpe.getResponse().toString());
            throw new IOException(rpe);
        } catch (ProcessingException | WebApplicationException ex) {
        	//bad stuff.
        	System.out.println("Hmm.. "+ex.getMessage());
        	throw new IOException(ex);
        }

    }

}
