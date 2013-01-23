package org.dasein.cloud.terremark;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
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
	
	// Error Tags & Attributes
	public final static String ERROR_TAG        = "Error";
	public final static String MESSAGE_ATTR     = "message";
	public final static String MAJOR_CODE_ATTR  = "majorErrorCode";
	public final static String MINOR_CODE_ATTR  = "minorErrorCode";

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

			if (parameters != null ){
				URIBuilder uri = null;
				try {
					uri = new URIBuilder(url);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				for (NameValuePair parameter : parameters) {
					uri.addParameter(parameter.getName(), parameter.getValue());
				}
				url = uri.toString();
			}

			HttpUriRequest method = null;
			if (methodType.equals(HttpMethodName.GET)){
				method = new HttpGet(url);
			}
			else if (methodType.equals(HttpMethodName.POST)){
				method = new HttpPost(url);
			}
			else if (methodType.equals(HttpMethodName.DELETE)){
				method = new HttpDelete(url);
			}
			else if (methodType.equals(HttpMethodName.PUT)){
				method = new HttpPut(url);
			}
			else if (methodType.equals(HttpMethodName.HEAD)){
				method = new HttpHead(url);
			}
			else {
				method = new HttpGet(url);
			}
			HttpResponse status = null;
			try {
				HttpClient client = new DefaultHttpClient();
				HttpParams params = new BasicHttpParams();

				HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
				HttpProtocolParams.setContentCharset(params, "UTF-8");
				HttpProtocolParams.setUserAgent(params, "Dasein Cloud");

				attempts++;

				String proxyHost = provider.getProxyHost();
				if( proxyHost != null ) {
					int proxyPort = provider.getProxyPort();
					boolean ssl = url.startsWith("https");
					params.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, proxyPort, ssl ? "https" : "http"));
				}
				for( Map.Entry<String, String> entry : headers.entrySet() ) {
					method.addHeader(entry.getKey(), entry.getValue());
				}
				if (body != null && body != "" && (methodType.equals(HttpMethodName.PUT) || methodType.equals(HttpMethodName.POST))) {
					try {
						HttpEntity entity = new StringEntity(body, "UTF-8");
						((HttpEntityEnclosingRequestBase) method).setEntity(entity);
					} catch (UnsupportedEncodingException e) {
						logger.warn(e);
					}
				}
				if( wire.isDebugEnabled() ) {

					wire.debug(methodType.name() + " " + method.getURI());
					for( Header header : method.getAllHeaders() ) {
						wire.debug(header.getName() + ": " + header.getValue());
					}
					if (body != null) {
						wire.debug(body);
					}
				}
				try {
					status =  client.execute(method);
					if( wire.isDebugEnabled() ) {
						wire.debug("HTTP STATUS: " + status);
					}
				} 
				catch( IOException e ) {
					logger.error("I/O error from server communications: " + e.getMessage());
					e.printStackTrace();
					throw new InternalException(e);
				}
				int statusCode = status.getStatusLine().getStatusCode();
				if( statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_ACCEPTED ) {
					try {
						InputStream input = status.getEntity().getContent();

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
						throw new CloudException(CloudErrorType.COMMUNICATION, statusCode, null, e.getMessage());
					}
				}
				else if ( statusCode == HttpStatus.SC_NO_CONTENT ) {
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
				else if( statusCode == HttpStatus.SC_FORBIDDEN ) {
					String msg = "OperationNotAllowed ";
					try {
						msg += parseResponseToString(status.getEntity().getContent());
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					wire.error(msg);
					throw new TerremarkException(statusCode, "OperationNotAllowed", msg);
				}
				else {
					String response = "Failed to parse response.";
					ParsedError parsedError = null;
					try {
						response = parseResponseToString(status.getEntity().getContent());
						parsedError = parseErrorResponse(response);
					} catch (IllegalStateException e1) {
						e1.printStackTrace();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					if( logger.isDebugEnabled() ) {
						logger.debug("Received " + status + " from " + url);
					}
					if( statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE || statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR ) {
						if( attempts >= 5 ) {
							String msg;
							wire.warn(response);
							if( statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE ) {
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
							if (parsedError != null) {
								throw new TerremarkException(parsedError);
							}
							else {
								throw new CloudException("HTTP Status " + statusCode + msg);
							}
						}
						else {
							try { Thread.sleep(5000L); }
							catch( InterruptedException e ) { /* ignore */ }
							return invoke();
						}
					}
					wire.error(response);
					if (parsedError != null) {
						throw new TerremarkException(parsedError);
					}
					else {
						String msg = "\nResponse from server was:\n" + response;
						logger.error(msg);
						throw new CloudException("HTTP Status " + statusCode + msg);
					}			
				}
			}
			finally {
				try {
					if (status != null) {
						EntityUtils.consume(status.getEntity());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
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
	
	private static ParsedError parseErrorResponse(String responseBody) throws CloudException, InternalException {
		try {
			ByteArrayInputStream bas = new ByteArrayInputStream(responseBody.getBytes());

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
			Document doc = parser.parse(bas);

			bas.close();
			
			Node errorNode = doc.getElementsByTagName(ERROR_TAG).item(0);
			ParsedError error = new ParsedError();
			if (errorNode != null) {
				error.message = errorNode.getAttributes().getNamedItem(MESSAGE_ATTR).getNodeValue();
				error.code = Integer.parseInt(errorNode.getAttributes().getNamedItem(MAJOR_CODE_ATTR).getNodeValue());
			}
			
			return error;
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

	private String parseResponseToString(InputStream responseBodyAsStream) throws CloudException, InternalException {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(responseBodyAsStream));
			StringBuilder sb = new StringBuilder();
			String line;

			while( (line = in.readLine()) != null ) {
				sb.append(line);
				sb.append("\n");
			}
			in.close();

			return sb.toString();
		}
		catch( IOException e ) {
			throw new CloudException(e);
		}			
	}

	public static void main(String[] args) throws CloudException, InternalException{

	}
}
