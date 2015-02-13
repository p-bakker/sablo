/*
 * Copyright (C) 2014 Servoy BV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sablo.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.CloseReason;
import javax.websocket.Session;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;
import org.sablo.BaseWebObject;
import org.sablo.Container;
import org.sablo.eventthread.IEventDispatcher;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.types.AggregatedPropertyType;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The websocket endpoint for communication between the WebSocketSession instance on the server and the browser.
 * This class handles:
 * <ul>
 * <li>creating of websocket sessions and rebinding after reconnect
 * <li>messages protocol with request/response
 * <li>messages protocol with data conversion (currently only date)
 * <li>service calls (both server to client and client to server)
 * </ul>
 *
 * @author jcompagner, rgansevles
 */
abstract public class WebsocketEndpoint implements IWebsocketEndpoint
{
	public static final Logger log = LoggerFactory.getLogger(WebsocketEndpoint.class.getCanonicalName());

	private static ThreadLocal<IWebsocketEndpoint> currentInstance = new ThreadLocal<>();

	private final WeakHashMap<Container, Object> usedContainers = new WeakHashMap<>(3); // set of used container in order to collect all changes

	public static IWebsocketEndpoint get()
	{
		IWebsocketEndpoint websocketEndpoint = currentInstance.get();
		if (websocketEndpoint == null)
		{
			throw new IllegalStateException("no current websocket endpoint set");
		}

		IWebsocketSession wsSession = websocketEndpoint.getWebsocketSession();
		if (wsSession != null)
		{
			List<IWebsocketEndpoint> registeredEndpoints = wsSession.getRegisteredEnpoints();
			if (registeredEndpoints.indexOf(websocketEndpoint) == -1)
			{
				String windowId = websocketEndpoint.getWindowId();
				if (windowId != null)
				{
					for (IWebsocketEndpoint ep : registeredEndpoints)
					{
						if (windowId.equals(ep.getWindowId()))
						{
							websocketEndpoint = ep;
							currentInstance.set(websocketEndpoint);
							break;
						}
					}
				}
			}
		}

		return websocketEndpoint;
	}

	public static boolean exists()
	{
		return currentInstance.get() != null;
	}

	public static IWebsocketEndpoint set(IWebsocketEndpoint endpoint)
	{
		IWebsocketEndpoint websocketEndpoint = currentInstance.get();
		currentInstance.set(endpoint);
		return websocketEndpoint;
	}

	/*
	 * connection with browser
	 */

	private final String endpointType;

	private Session session;

	private String windowId;

	/*
	 * user session alike http session space
	 */
	private IWebsocketSession wsSession;

	private final AtomicInteger nextMessageId = new AtomicInteger(0);
	private final Map<Integer, List<Object>> pendingMessages = new HashMap<>();
	private final List<Map<String, ? >> serviceCalls = new ArrayList<>();
	private final PropertyDescription serviceCallTypes = AggregatedPropertyType.newAggregatedProperty();

	public WebsocketEndpoint(String endpointType)
	{
		this.endpointType = endpointType;
	}

	public void start(Session newSession, String sessionid, final String windowid) throws Exception
	{
		session = newSession;

		String uuid = "null".equalsIgnoreCase(sessionid) ? null : sessionid;
		windowId = "null".equalsIgnoreCase(windowid) ? null : windowid;
		currentInstance.set(this);
		try
		{
			wsSession = WebsocketSessionManager.getOrCreateSession(endpointType, uuid, true);

			if (!wsSession.getUuid().equals(uuid)) sendMessage(new JSONStringer().object().key("sessionid").value(wsSession.getUuid()).endObject().toString());
			wsSession.registerEndpoint(this);
			wsSession.onOpen(newSession.getRequestParameterMap());
		}
		finally
		{
			currentInstance.remove();
		}
	}

	/**
	 * @return the windowId
	 */
	@Override
	public String getWindowId()
	{
		return windowId;
	}

	/**
	 * @param windowId the windowId to set
	 */
	public void setWindowId(String windowId)
	{
		this.windowId = windowId;
		try
		{
			sendMessage(new JSONStringer().object().key("windowid").value(windowId).endObject().toString());
		}
		catch (Exception e)
		{
			log.error("error sending the window id to the client", e);
		}
	}

	@Override
	public void closeSession()
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Application Server shutdown!!!!!!!"));
	}

	@Override
	public void cancelSession(String reason)
	{
		closeSession(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
	}

	private void closeSession(CloseReason closeReason)
	{
		if (session != null)
		{
			try
			{
				session.close(closeReason);
			}
			catch (IOException e)
			{
			}
			session = null;
		}
		if (wsSession != null)
		{
			wsSession.deregisterEndpoint(this);
			wsSession = null;
		}
	}

	public void onClose()
	{
		if (wsSession != null)
		{
			wsSession.deregisterEndpoint(this);
			WebsocketSessionManager.closeSession(endpointType, wsSession.getUuid());
		}
		session = null;
	}

	private final StringBuilder incomingPartialMessage = new StringBuilder();

	public void incoming(String msg, boolean lastPart)
	{
		String message = msg;
		if (!lastPart)
		{
			incomingPartialMessage.append(message);
			return;
		}
		if (incomingPartialMessage.length() > 0)
		{
			incomingPartialMessage.append(message);
			message = incomingPartialMessage.toString();
			incomingPartialMessage.setLength(0);
		}

		final JSONObject obj;
		try
		{
			currentInstance.set(this);
			obj = new JSONObject(message);
			if (obj.has("smsgid"))
			{
				wsSession.getEventDispatcher().addEvent(new Runnable()
				{

					@Override
					public void run()
					{
						// response message
						Integer suspendID = new Integer(obj.optInt("smsgid"));
						List<Object> ret = pendingMessages.remove(suspendID);
						if (ret != null) ret.add(obj.opt("ret"));

						wsSession.getEventDispatcher().resume(suspendID);
					}

				}, IWebsocketEndpoint.EVENT_LEVEL_SYNC_API_CALL);
				return;
			}

			if (obj.has("service"))
			{
				// service call
				final String serviceName = obj.optString("service");
				final IServerService service = wsSession.getServerService(serviceName);

				if (service != null)
				{
					final String methodName = obj.optString("methodname");
					final JSONObject arguments = obj.optJSONObject("args");
					int eventLevel = (service instanceof IEventDispatchAwareServerService)
						? ((IEventDispatchAwareServerService)service).getMethodEventThreadLevel(methodName, arguments) : IEventDispatcher.EVENT_LEVEL_DEFAULT;

					wsSession.getEventDispatcher().addEvent(new Runnable()
					{

						@Override
						public void run()
						{
							Object result = null;
							String error = null;
							try
							{
								result = service.executeMethod(methodName, arguments);
							}
							catch (Exception e)
							{
								error = "Error: " + e.getMessage();
								log.error(error, e);
							}

							final Object msgId = obj.opt("cmsgid");
							if (msgId != null) // client wants response
							{
								try
								{
									if (error == null)
									{
										Object resultObject = result;
										PropertyDescription objectType = null;
										if (result instanceof TypedData)
										{
											resultObject = ((TypedData< ? >)result).content;
											objectType = ((TypedData< ? >)result).contentType;
										}
										WebsocketEndpoint.get().sendResponse(msgId, resultObject, objectType, getToJSONConverter(), true);
									}
									else
									{
										WebsocketEndpoint.get().sendResponse(msgId, error, null, getToJSONConverter(), false);
									}
								}
								catch (IOException e)
								{
									log.error(e.getMessage(), e);
								}
							}
						}
					}, eventLevel);
				}
				else
				{
					log.info("unknown service called from the client: " + serviceName);
				}
				return;
			}
			if (obj.has("servicedatapush"))
			{
				String servicename = obj.optString("servicedatapush");
				IClientService service = getWebsocketSession().getService(servicename);
				JSONObject changes = obj.optJSONObject("changes");
				Iterator keys = changes.keys();
				while (keys.hasNext())
				{
					String key = (String)keys.next();
					service.putBrowserProperty(key, changes.opt(key));
				}
				return;
			}
			wsSession.handleMessage(obj);
		}
		catch (JSONException e)
		{
			log.error("JSONException", e);
			return;
		}
		finally
		{
			currentInstance.remove();
		}

	}

	protected IToJSONConverter<BaseWebObject> getToJSONConverter()
	{
		return FullValueToJSONConverter.INSTANCE;
	}

	private void addServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		// {"services":[{name:serviceName,call:functionName,args:argumentsArray}]}
		Map<String, Object> serviceCall = new HashMap<>();
		PropertyDescription typesOfThisCall = AggregatedPropertyType.newAggregatedProperty();
		serviceCall.put("name", serviceName);
		serviceCall.put("call", functionName);
		serviceCall.put("args", arguments);
		if (argumentTypes != null) typesOfThisCall.putProperty("args", argumentTypes);
		serviceCalls.add(serviceCall);
		serviceCallTypes.putProperty(String.valueOf(serviceCalls.size() - 1), typesOfThisCall);
	}

	@Override
	public void executeAsyncServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes)
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
	}

	@Override
	public Object executeServiceCall(String serviceName, String functionName, Object[] arguments, PropertyDescription argumentTypes, Map<String, ? > changes,
		PropertyDescription changesTypes) throws IOException
	{
		addServiceCall(serviceName, functionName, arguments, argumentTypes);
		return sendMessage(changes, changesTypes, false, getToJSONConverter()); // will return response from last service call
	}

	public <ContextT> Object sendMessage(final Map<String, ? > data, final PropertyDescription dataTypes, boolean async, IToJSONConverter<ContextT> converter)
		throws IOException
	{
		return sendMessage((data == null || data.size() == 0) ? null : new IToJSONWriter<ContextT>()
		{

			@Override
			public boolean writeJSONContent(JSONWriter w, String keyInParent, IToJSONConverter<ContextT> converter, DataConversion clientDataConversions)
				throws JSONException
			{
				if (data != null && data.size() > 0)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					converter.toJSONValue(w, null, data, dataTypes, clientDataConversions, null);
				}
				return false;
			}

		}, async, converter);
	}

	public Object sendMessage(IToJSONWriter dataWriter, boolean async, IToJSONConverter converter) throws IOException
	{
		if (dataWriter == null && serviceCalls.size() == 0) return null;

		try
		{
			boolean hasContentToSend = false;
			JSONStringer w = new JSONStringer();
			w.object();
			DataConversion clientDataConversions = new DataConversion();

			if (dataWriter != null)
			{
				clientDataConversions.pushNode("msg");
				hasContentToSend = dataWriter.writeJSONContent(w, "msg", converter, clientDataConversions) || hasContentToSend;
				clientDataConversions.popNode();
			}
			if (serviceCalls.size() > 0)
			{
				hasContentToSend = true;
				clientDataConversions.pushNode("services");
				converter.toJSONValue(w, "services", serviceCalls, serviceCallTypes, clientDataConversions, null);
				clientDataConversions.popNode();
			}

			Integer messageId = null;
			String text = null;

			if (hasContentToSend)
			{
				if (!async)
				{
					w.key("smsgid").value(messageId = new Integer(nextMessageId.incrementAndGet()));
				}
				JSONUtils.writeClientConversions(w, clientDataConversions);
				w.endObject();

				text = w.toString();
				sendText(text);
				serviceCalls.clear();
			}

			return (messageId == null) ? null : waitResponse(messageId, text);
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	public void flush() throws IOException
	{
		sendMessage(null, null, true, getToJSONConverter());
	}

	public void sendMessage(String txt) throws IOException
	{
		sendText("{\"msg\":" + txt + '}');
	}

	@Override
	public void sendResponse(Object msgId, Object object, PropertyDescription objectType, IToJSONConverter converter, boolean success) throws IOException
	{
		Map<String, Object> data = new HashMap<>();
		String key = success ? "ret" : "exception";
		data.put(key, object);
		data.put("cmsgid", msgId);
		PropertyDescription dataTypes = null;
		if (objectType != null)
		{
			dataTypes = AggregatedPropertyType.newAggregatedProperty();
			dataTypes.putProperty(key, objectType);
		}

		try
		{
			sendText(JSONUtils.writeDataWithConversions(converter, data, dataTypes, null));
		}
		catch (JSONException e)
		{
			throw new IOException(e);
		}
	}

	private synchronized void sendText(String txt) throws IOException
	{
		if (session == null)
		{
			throw new IOException("No session");
		}
		session.getBasicRemote().sendText(txt);
	}


	/** Wait for a response message with given messsageId.
	 * @param text
	 * @throws IOException
	 */
	protected Object waitResponse(Integer messageId, String text) throws IOException
	{
		List<Object> ret = new ArrayList<>(1);
		pendingMessages.put(messageId, ret);
		wsSession.getEventDispatcher().suspend(messageId, EVENT_LEVEL_SYNC_API_CALL); // TODO are fail-safes/timeouts needed here in case client browser gets closed or confused?

		if (ret.size() == 0)
		{
			log.warn("No response from client for message '" + text + "'");
			// Or throw an exception here?
			return null;
		}
		return ret.get(0);
	}

	public boolean hasSession()
	{
		return session != null;
	}

	@Override
	public void registerContainer(Container container)
	{
		usedContainers.put(container, new Object());
	}

	@Override
	public synchronized boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		boolean contentHasBeenWritten = false;

		for (Container fc : usedContainers.keySet())
		{
			if (fc.isVisible() && fc.isChanged())
			{
				if (!contentHasBeenWritten)
				{
					JSONUtils.addKeyIfPresent(w, keyInParent);
					w.object();
					contentHasBeenWritten = true;
				}
				String containerName = fc.getName();
				clientDataConversions.pushNode(containerName);
				fc.writeAllComponentsChanges(w, containerName, converter, clientDataConversions);
				clientDataConversions.popNode();
			}
		}
		if (contentHasBeenWritten) w.endObject();
		return contentHasBeenWritten;
	}

	@Override
	public IWebsocketSession getWebsocketSession()
	{
		return wsSession;
	}
}
