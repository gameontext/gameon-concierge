package net.wasdev.gameon.concierge;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebFilter(
		filterName = "registrationAuthFilter",
		urlPatterns = {"/*"}
		  )
public class ConciergeAuthFilter implements Filter{
	private static final String CHAR_SET = "UTF-8";
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static long timeoutMS = 5000;		//timeout for requests, default to 5 seconds
	
    /** CDI injection of client for Player CRUD operations */
    @Inject
    PlayerClient playerClient;
	
	@Resource(lookup="registrationSecret")
	String registrationSecret;
	@Resource(lookup="querySecret")
	String querySecret;
	

	Map<String,TimestampedKey> apiKeyForId = Collections.synchronizedMap( new HashMap<String,TimestampedKey>() );
	
	/**
	 * Timestamped API Key
	 * Equality / Hashcode is determined by apikey string alone.
	 * Sort order is provided by key timestamp.
	 */
	private final static class TimestampedKey implements Comparable<TimestampedKey> {
		private final String apiKey;
		private final Long time;
		public TimestampedKey(String a){
			this.apiKey=a; this.time=System.currentTimeMillis();
		}
		public TimestampedKey(String a,Long t){
			this.apiKey=a; this.time=t;
		}
		@Override
		public int compareTo(TimestampedKey o) {
			return o.time.compareTo(time);
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((apiKey == null) ? 0 : apiKey.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimestampedKey other = (TimestampedKey) obj;
			if (apiKey == null) {
				if (other.apiKey != null)
					return false;
			} else if (!apiKey.equals(other.apiKey))
				return false;
			return true;
		}
	}
	
	private static final Set<TimestampedKey> usedKeys = 
			Collections.synchronizedSet(new LinkedHashSet<TimestampedKey>());	//keys already received, prevent replay attacks
	
	//the authentication steps that are performed on an incoming request
	private enum AuthenticationState {
		hasQueryString,			//starting state
		hasAPIKeyParam,
		isAPIKeyValid,
		hasKeyExpired,
		checkReplay,
		PASSED,					//end state
		ACCESS_DENIED			//end state
	}
	
	//ensure consistent parameter names
	public enum Params {
		apikey,
		serviceID,
		stamp;
		
		public String toString() {
			return "&" + this.name() + "=";
		}		
	}
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}
	
	//a collection of details built up during validation.
	private static class ValidationContext {
		public String apiKey;
		long time;
		int apiKeyOffset;
		PrintWriter failLog;
	}
	
	/**
	 * Check if the time stamp is considered expired.
	 */
	private static boolean hasExpired(Long value){
		return (System.currentTimeMillis() - value) > timeoutMS;
	}
	
	/**
	 * Check if the request has a query string.
	 */
	private boolean validateQueryStringIsPresent(ServletRequest request, ValidationContext ctx){
		//check that there is a query string which will contain the service ID and api key
		String queryString = ((HttpServletRequest) request).getQueryString();
		ctx.failLog.println("AUTH: hasQuery? "+(queryString != null));
		return (queryString != null);
	}
	
	/**
	 * Check if the request has an apiKey parameter
	 */
	private boolean validateApiKeyParamIsPresent(ServletRequest request, ValidationContext ctx){
		//check there is an apikey parameter
		String queryString = ((HttpServletRequest) request).getQueryString();
		int pos = queryString.lastIndexOf(Params.apikey.toString());
		ctx.apiKeyOffset = pos;
		ctx.failLog.println("AUTH: hasApiKey? "+(pos != -1));
		return (pos != -1); 
	}
	
	/**
	 * Obtain the id from the request, or null if there wasn't one.
	 */
	private String getIdForRequest(ServletRequest request, ValidationContext ctx){
		String id = request.getParameter("id");
		return id;
	}
	
	/**
	 * Obtain the apiKey for the given id, using a local cache to avoid hitting couchdb too much.
	 */
	private String getAPIKeyForId(String id, ValidationContext ctx){
		String key = null;
		//check cache for this id.
		TimestampedKey t = apiKeyForId.get(id);
		ctx.failLog.println("AUTH: cache hit? "+(t == null));
		if(t!=null){
			//cache hit, but is the key still valid?
			long current = System.currentTimeMillis();
			current -= t.time;			
			//if the key is older than this time period.. we'll consider it dead.
			boolean valid = current < TimeUnit.DAYS.toMillis(1);	
			ctx.failLog.println("AUTH: cache still valid? "+(valid));
			if(valid){							
				//key is good.. we'll use it.
				key = t.apiKey;
			}else{
				//key has expired.. forget it.
				apiKeyForId.remove(id);
				t=null;
			}
		}
		if(t == null){
			//key was not in cache, or was expired..
			//go obtain the apiKey via player Rest endpoint.
			try{
				key = playerClient.getApiKey(id);
			}catch(Exception e){
				ctx.failLog.println("AUTH: unable to obtain key from player service?"+(key!=null));
				key=null;
			}			
			ctx.failLog.println("AUTH: obtained key from player service?"+(key!=null));
			//got a key ? add it to the cache.
			if(key!=null){
				t = new TimestampedKey(key);
				apiKeyForId.put(id, t);
			}
		}
		return key;
	}
	
	/**
	 *	Validate the apikey on the request matches the expected value for this user id.
	 */
	private boolean validateApiKeyContent(ServletRequest request, String id, String secret, ValidationContext ctx) throws IOException{
		String queryString = ((HttpServletRequest) request).getQueryString();
		String sharedSecret = secret;
		
		//if there's an id present in the request, then we need to look up the apiKey for that id.
		ctx.failLog.println("AUTH: id param? "+(id == null));
		if(id!=null){						
			sharedSecret = getAPIKeyForId(id, ctx);
		}		
		
		if(sharedSecret==null){
			return false;
		}
		
		//validate API key against all parameters (except the API key itself)
		
		//remove API key from end of query string
		queryString = queryString.substring(0, ctx.apiKeyOffset);	
		
		//calculate hmac using API key.
		String hmac = request.getParameter(Params.apikey.name());
		String apikey = digest(queryString,sharedSecret);
		
		//store the apiKey for the replay check
		ctx.apiKey = apikey;
		
		ctx.failLog.println("AUTH: api key validated?"+(apikey.equals(hmac)));
		return apikey.equals(hmac); 
	}
	
	/**
	 * Check if the key for this request is considered expired
	 */
	private boolean validateIfKeyIsStillValid(ServletRequest request, ValidationContext ctx){
		//check that key has not timed out
		long time = Long.parseLong(request.getParameter(Params.stamp.name()));
		ctx.failLog.println("AUTH: api key expired?"+hasExpired(time));
		ctx.time = time;
		return !hasExpired(time);
	}
	
	/**
	 * Check if we have seen this key before.
	 */
	private boolean validateIfKeyIsNotReplay(ServletRequest request, ValidationContext ctx){
		//simple replay check - only allows the one time use of API keys, storing time allows expired keys to be purged
		boolean notAlreadyPresent = usedKeys.add(new TimestampedKey(ctx.apiKey, ctx.time));
		//the set of keys is sorted with oldest (smallest) timestamp first so we can iterate from the oldest key, 
		//and remove all expired ones.
		synchronized(usedKeys){
			Iterator<TimestampedKey> i = usedKeys.iterator();
			while(i.hasNext()){
				TimestampedKey k = i.next();
				if(hasExpired(k.time)){
					i.remove();
				}else{
					break;
				}
			}
		}
		ctx.failLog.println("AUTH: api key isReplay?"+notAlreadyPresent);
		return notAlreadyPresent;	
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		//we're a single filter, but we protect different paths with different keys.		
		HttpServletRequest http = (HttpServletRequest) request;		
		String requestUri = http.getRequestURI();
		String path = requestUri.substring(http.getContextPath().length());
		
		//create a log of the steps we perform during validation to assist with auth failures.
		StringWriter sw = new StringWriter();
		PrintWriter authLog = new PrintWriter(sw);
		
		//decide which secret to secure the request with, if registration
		//we use the registration secret, othewise, use query secret.
		String sharedSecret;
		if("/registerRoom".equals(path)){
			sharedSecret = registrationSecret;
			authLog.println("AUTH: room registration request");
		}else{
			sharedSecret = querySecret;
			authLog.println("AUTH: concierge query request");
		}
		
		ValidationContext ctx = new ValidationContext();
		ctx.failLog = authLog;
		
		String playerId = null;
	
		AuthenticationState state = AuthenticationState.hasQueryString;		//default
		while(!state.equals(AuthenticationState.PASSED)) {
			switch(state) {
				case hasQueryString :
					state = validateQueryStringIsPresent(request,ctx) ? AuthenticationState.hasAPIKeyParam : AuthenticationState.ACCESS_DENIED;
					break;
				case hasAPIKeyParam :	
					state = validateApiKeyParamIsPresent(request,ctx)? AuthenticationState.isAPIKeyValid : AuthenticationState.ACCESS_DENIED;
					break;
				case isAPIKeyValid :	
					//remember the id for the request, to pass to the service if validation succeeds.
					playerId = getIdForRequest(request, ctx);
					state = validateApiKeyContent(request,playerId,sharedSecret,ctx) ?  AuthenticationState.hasKeyExpired : AuthenticationState.ACCESS_DENIED;
					break;
				case hasKeyExpired :	
					state = validateIfKeyIsStillValid(request,ctx) ?  AuthenticationState.checkReplay : AuthenticationState.ACCESS_DENIED;
					break;
				case checkReplay : 
					state = validateIfKeyIsNotReplay(request,ctx) ? AuthenticationState.PASSED : AuthenticationState.ACCESS_DENIED;
					break;
				case ACCESS_DENIED :
				default :
					((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN, sw.toString());
					return;
			}
		}
		authLog.close();
		
		//request has passed all validation checks, so allow it to proceed
		//set the validated player id into the request as an attribute.
        request.setAttribute("player.id", playerId);
		request.setAttribute(Params.serviceID.name(), request.getParameter(Params.serviceID.name()));
		
		//invoke the service
		chain.doFilter(request, response);		
	}
	
	/*
	 * Construct a HMAC for this request.
	 * It is then base 64 and URL encoded ready for transmission as a query parameter.
	 */
	private String digest(String message, String sharedSecret) throws IOException {
		try {
			byte[] data = message.getBytes(CHAR_SET);
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			SecretKeySpec key = new SecretKeySpec(sharedSecret.getBytes(CHAR_SET), HMAC_ALGORITHM);
			mac.init(key);
			return javax.xml.bind.DatatypeConverter.printBase64Binary(mac.doFinal(data));
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	@Override
	public void destroy() {
	}

}
