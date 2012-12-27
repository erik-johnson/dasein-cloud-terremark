package org.dasein.cloud.terremark;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


public class TerremarkMethod {

	public enum HttpMethodName {
		GET, DELETE, HEAD, POST, PUT;
	}

	static private final Logger logger = Terremark.getLogger(TerremarkMethod.class);
	static private final Logger wire = Terremark.getWireLogger(TerremarkMethod.class);

	static public class ParsedError {
		public int code;
		public String message;
	}

	private int                attempts    = 0;
	private NameValuePair[]    parameters  = null;
	private Terremark          provider    = null;
	private String             url         = null;
	private HttpMethodName     methodType  = null;
	private String             body        = null;
	private Map<String,String> headers     = null;

	public TerremarkMethod(Terremark provider, HttpMethodName methodType, String url, NameValuePair[] queryParameters, String body) throws CloudException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("Provider context is necessary for this request");
		}

		if (url.startsWith(ctx.getEndpoint())){
			url = url.replace(ctx.getEndpoint(), "");
		}
		if (url.startsWith(Terremark.DEFAULT_URI_PATH)){
			url = url.replace(Terremark.DEFAULT_URI_PATH, "");
		}
		if (!url.startsWith("/")){
			url = "/" + url;
		}

		this.headers = provider.getHeaders(ctx, methodType, url, queryParameters, body);

		url = ctx.getEndpoint() + Terremark.DEFAULT_URI_PATH + url;

		this.url = url;
		this.parameters = queryParameters;
		this.provider = provider;
		this.methodType = methodType;
		this.body = body;

	}

	public Document invoke() throws TerremarkException, CloudException, InternalException {
		return invoke(false);
	}

	public Document invoke(boolean debug) throws TerremarkException, CloudException, InternalException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + TerremarkMethod.class.getName() + ".invoke(" + debug + ")");
		}
		try {
			if( logger.isDebugEnabled() ) {
				logger.debug("Talking to server at " + url);
			}
			HttpClientParams httpClientParams = new HttpClientParams();

			HttpMethod method = null;
			if (methodType.equals(HttpMethodName.GET)){
				method = new GetMethod(url);
			}
			else if (methodType.equals(HttpMethodName.POST)){
				method = new PostMethod(url);
			}
			else if (methodType.equals(HttpMethodName.DELETE)){
				method = new DeleteMethod(url);
			}
			else if (methodType.equals(HttpMethodName.PUT)){
				method = new PutMethod(url);
			}
			else if (methodType.equals(HttpMethodName.HEAD)){
				method = new HeadMethod(url);
			}
			else {
				method = new GetMethod(url);
			}

			try {
				HttpClient client;
				int status;

				attempts++;
				httpClientParams.setParameter(HttpMethodParams.USER_AGENT, "Dasein Cloud");
				client = new HttpClient(httpClientParams);
				if( provider.getProxyHost() != null ) {
					client.getHostConfiguration().setProxy(provider.getProxyHost(), provider.getProxyPort());
				}
				for( Map.Entry<String, String> entry : headers.entrySet() ) {
					method.addRequestHeader(entry.getKey(), entry.getValue());
				}
				if (parameters != null ){
					method.setQueryString(parameters);
				}
				if (body != null && body != "") {
					try {
						RequestEntity entity = new StringRequestEntity(body,Terremark.XML, "utf-8");
						((EntityEnclosingMethod) method).setRequestEntity(entity);
					} catch (UnsupportedEncodingException e) {
						logger.warn(e);
					}
				}
				if( wire.isDebugEnabled() ) {
					if (parameters != null){
						wire.debug(methodType.name() + " " + method.getPath() + "/?" + method.getQueryString());
					}
					else {
						wire.debug(methodType.name() + " " + method.getPath());
					}
					for( Header header : method.getRequestHeaders() ) {
						wire.debug(header.getName() + ": " + header.getValue());
					}
					if (body != null) {
						wire.debug(body);
					}
				}
				try {
					status =  client.executeMethod(method);
					if( wire.isDebugEnabled() ) {
						wire.debug("HTTP STATUS: " + status);
					}
				} 
				catch( HttpException e ) {
					logger.error("HTTP error from server: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(CloudErrorType.COMMUNICATION, 0, null, e.getMessage());
				} 
				catch( IOException e ) {
					logger.error("I/O error from server communications: " + e.getMessage());
					e.printStackTrace();
					throw new InternalException(e);
				}
				if( status == HttpStatus.SC_OK || status == HttpStatus.SC_CREATED || status == HttpStatus.SC_ACCEPTED ) {
					try {
						InputStream input = method.getResponseBodyAsStream();

						try {
							return parseResponse(input);
						}
						finally {
							input.close();
						}
					} 
					catch( IOException e ) {
						logger.error("Error parsing response from Teremark: " + e.getMessage());
						e.printStackTrace();
						throw new CloudException(CloudErrorType.COMMUNICATION, status, null, e.getMessage());
					}
				}
				else if ( status == HttpStatus.SC_NO_CONTENT ) {
					logger.debug("Recieved no content in response. Creating an empty doc.");
					DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
					DocumentBuilder docBuilder = null;
					try {
						docBuilder = dbfac.newDocumentBuilder();
					} catch (ParserConfigurationException e) {
						e.printStackTrace();
					}
					return docBuilder.newDocument();
				}
				else if( status == HttpStatus.SC_FORBIDDEN ) {
					String msg = "OperationNotAllowed ";
					try {
						msg += method.getResponseBodyAsString();
					}
					catch( IOException ioException ) {
						logger.warn(ioException);
					}
					wire.error(msg);
					throw new TerremarkException(status, "OperationNotAllowed", msg);
				}
				else {
					if( logger.isDebugEnabled() ) {
						logger.debug("Received " + status + " from " + url);
					}
					if( status == HttpStatus.SC_SERVICE_UNAVAILABLE || status == HttpStatus.SC_INTERNAL_SERVER_ERROR ) {
						if( attempts >= 5 ) {
							String msg;
							String response = "";
							try {
								response = method.getResponseBodyAsString();
								wire.warn(response);
							} catch (IOException e) {
								logger.warn(e);
							}
							if( status == HttpStatus.SC_SERVICE_UNAVAILABLE ) {
								msg = "Cloud service is currently unavailable.";
							}
							else {
								msg = "The cloud service encountered a server error while processing your request.";
								try {
									msg = msg + "Response from server was:\n" + response;
								}
								catch( RuntimeException runException ) {
									logger.warn(runException);
								}
								catch( Error error ) {
									logger.warn(error);
								}
							}
							wire.error(response);
							logger.error(msg);
							throw new CloudException(msg);
						}
						else {
							try { Thread.sleep(5000L); }
							catch( InterruptedException e ) { /* ignore */ }
							return invoke();
						}
					}
					String msg = "";
					try {
						msg = "\nResponse from server was:\n" + method.getResponseBodyAsString();
					}
					catch( IOException ioException ) {
						logger.warn(ioException);
					}
					wire.error(msg);
					throw new CloudException("HTTP Status " + status + msg);
				}
			}
			finally {
				method.releaseConnection();
			}
		}
		finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + TerremarkMethod.class.getName() + ".invoke()");
			}
		}
	}

	private static Document parseResponse(String responseBody) throws CloudException, InternalException {
		try {
			if( wire.isDebugEnabled() ) {
				String[] lines = responseBody.split("\n");

				if( lines.length < 1 ) {
					lines = new String[] { responseBody };
				}
				for( String l : lines ) {
					wire.debug(l);
				}
			}
			ByteArrayInputStream bas = new ByteArrayInputStream(responseBody.getBytes());

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
			Document doc = parser.parse(bas);

			bas.close();
			return doc;
		}
		catch( IOException e ) {
			throw new CloudException(e);
		}
		catch( ParserConfigurationException e ) {
			throw new CloudException(e);
		}
		catch( SAXException e ) {
			throw new CloudException(e);
		}   
	}

	private Document parseResponse(InputStream responseBodyAsStream) throws CloudException, InternalException {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(responseBodyAsStream));
			StringBuilder sb = new StringBuilder();
			String line;

			while( (line = in.readLine()) != null ) {
				sb.append(line);
				sb.append("\n");
			}
			in.close();

			return parseResponse(sb.toString());
		}
		catch( IOException e ) {
			throw new CloudException(e);
		}			
	}

	public static void main(String[] args) throws CloudException, InternalException{

	}
}
