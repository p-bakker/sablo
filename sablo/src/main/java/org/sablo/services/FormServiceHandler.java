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

package org.sablo.services;

import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.sablo.Container;
import org.sablo.WebComponent;
import org.sablo.websocket.IServerService;
import org.sablo.websocket.IWebsocketSession;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.EmbeddableJSONWriter;
import org.sablo.websocket.utils.JSONUtils.FullValueToJSONConverter;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * formService implementation to handle methods at form level.
 *
 * @author rgansevles
 *
 */
public class FormServiceHandler implements IServerService
{
	public static final Logger log = LoggerFactory.getLogger(FormServiceHandler.class.getCanonicalName());

	private final IWebsocketSession websocketSession;

	/**
	 * @param baseWebsocketSession
	 */
	public FormServiceHandler(IWebsocketSession websocketSession)
	{
		this.websocketSession = websocketSession;
	}

	/**
	 * @return the websocketSession
	 */
	public IWebsocketSession getWebsocketSession()
	{
		return websocketSession;
	}

	@Override
	public Object executeMethod(String methodName, JSONObject args) throws Exception
	{
		switch (methodName)
		{
			case "requestData" :
			{
				return requestData(args.optString("formname"));
			}

			case "dataPush" :
			{
				dataPush(args);
				break;
			}

			case "executeEvent" :
			{
				dataPush(args);
				return executeEvent(args);
			}

			default :
				log.warn("Method not implemented: '" + methodName + "'");
		}

		return null;
	}


	/**
	 * @param args
	 * @throws Exception 
	 */
	protected Object executeEvent(JSONObject obj) throws Exception
	{
		String formName = obj.getString("formname");

		Container form = getWebsocketSession().getForm(formName);
		if (form == null)
		{
			log.warn("executeEvent for unknown form '" + formName + "'");
			return null;
		}

		String beanName = obj.optString("beanname");
		WebComponent webComponent = form.getComponent(beanName);
		if (webComponent == null)
		{
			log.warn("executeEvent for unknown bean '" + beanName + "' on form '" + formName + "'");
			return null;
		}

		JSONArray jsargs = obj.getJSONArray("args");
		String eventType = obj.getString("event");
		Object[] args = new Object[jsargs == null ? 0 : jsargs.length()];
		for (int i = 0; jsargs != null && i < jsargs.length(); i++)
		{
			args[i] = jsargs.get(i);
		}

		return webComponent.executeEvent(eventType, args);
	}

	protected JSONStringer requestData(String formName) throws JSONException
	{
		Container form = getWebsocketSession().getForm(formName);
		if (form == null)
		{
			log.warn("Data requested from unknown form '" + formName + "'");
			return null;
		}

		JSONStringer initialFormData = null;
		TypedData<Map<String, Map<String, Object>>> allFormPropertiesTyped = form.getAllComponentsProperties();
		if (allFormPropertiesTyped != null && allFormPropertiesTyped.content != null)
		{
			// we will not return form properties typed data directly - instead we send both data and conversions inside this "ifd"
			// so that on client side the returned value doesn't get converted automatically and then deferr resolve handler that does applyBeanData
			// is not aware of conversion info; instead we give this entire thing as JSON to the deferr resolve handler in .js and it can do conversions/apply
			// bean data separately, having all the info that it needs - so also conversion info
			initialFormData = new EmbeddableJSONWriter();
			initialFormData.array().object();
			JSONUtils.writeDataWithConversions(getInitialRequestDataConverter(), initialFormData, allFormPropertiesTyped.content,
				allFormPropertiesTyped.contentType);
			initialFormData.endObject().endArray();
		}

		return initialFormData;
	}

	protected IToJSONConverter getInitialRequestDataConverter()
	{
		return FullValueToJSONConverter.INSTANCE;
	}

	protected void dataPush(JSONObject obj) throws JSONException
	{
		JSONObject changes = obj.getJSONObject("changes");
		if (changes.length() > 0)
		{
			String formName = obj.getString("formname");

			Container form = getWebsocketSession().getForm(formName);
			if (form == null)
			{
				log.warn("dataPush for unknown form '" + formName + "'");
				return;
			}

			String beanName = obj.optString("beanname");

			WebComponent webComponent = beanName.length() > 0 ? form.getComponent(beanName) : (WebComponent)form;
			Iterator<String> keys = changes.keys();
			while (keys.hasNext())
			{
				String key = keys.next();
				webComponent.putBrowserProperty(key, changes.get(key));
			}
		}
	}
}
