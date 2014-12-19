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

package org.sablo;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.websocket.TypedData;
import org.sablo.websocket.WebsocketEndpoint;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;
import org.sablo.websocket.utils.JSONUtils.IToJSONConverter;

/**
 * Container object is a component that can contain other components.
 * @author jblok
 */
public abstract class Container extends WebComponent
{
	protected final Map<String, WebComponent> components = new HashMap<>();
	protected boolean changed;

	public Container(String name, WebComponentSpecification spec)
	{
		super(name, spec);
	}

	/**
	 * Called when it changes or any of it's children change.
	 */
	protected void markAsChanged()
	{
		changed = true;
	}

	public boolean isChanged()
	{
		return changed;
	}

	public void add(WebComponent component)
	{
		WebComponent old = components.put(component.getName(), component);
		if (old != null)
		{ // should never happen I think
			old.parent = null;
		}
		component.parent = this;
	}

	public void remove(WebComponent component)
	{
		components.remove(component.getName());
		component.parent = null;
	}

	public WebComponent getComponent(String name)
	{
		return components.get(name);
	}

	public Collection<WebComponent> getComponents()
	{
		return Collections.unmodifiableCollection(components.values());
	}

	@Override
	public void dispose()
	{
		super.dispose();
		for (WebComponent component : components.values().toArray(new WebComponent[0]))
		{
			component.dispose();
		}
		components.clear();
	}

	public boolean writeAllComponentsChanges(JSONWriter w, String keyInParent, IToJSONConverter converter, DataConversion clientDataConversions)
		throws JSONException
	{
		boolean contentHasBeenWritten = writeComponentChanges(w, keyInParent, this, "", converter, clientDataConversions);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = writeComponentChanges(w, contentHasBeenWritten ? null : keyInParent, wc, wc.getName(), converter, clientDataConversions) ||
				contentHasBeenWritten;
		}
		if (contentHasBeenWritten) w.endObject();
		changed = false;
		return contentHasBeenWritten;
	}

	protected boolean writeComponentChanges(JSONWriter w, String keyInParent, WebComponent wc, String nodeName, IToJSONConverter converter,
		DataConversion clientDataConversions) throws JSONException
	{
		TypedData<Map<String, Object>> changes = wc.getAndClearChanges();
		if (changes.content.isEmpty())
		{
			return false;
		}

		if (keyInParent != null)
		{
			JSONUtils.addKeyIfPresent(w, keyInParent);
			w.object();
		}

		w.key(nodeName).object();
		clientDataConversions.pushNode(nodeName);
		JSONUtils.writeData(converter, w, changes.content, changes.contentType, clientDataConversions, wc);
		clientDataConversions.popNode();
		w.endObject();

		return true;
	}

	public boolean writeAllComponentsProperties(JSONWriter w, IToJSONConverter converter) throws JSONException
	{
		WebsocketEndpoint.get().registerContainer(this);

		DataConversion clientDataConversions = new DataConversion();
		boolean contentHasBeenWritten = writeComponentProperties(w, converter, this, "", clientDataConversions);
		for (WebComponent wc : getComponents())
		{
			contentHasBeenWritten = writeComponentProperties(w, converter, wc, wc.getName(), clientDataConversions) || contentHasBeenWritten;
		}

		JSONUtils.writeClientConversions(w, clientDataConversions);
		changed = false;
		return contentHasBeenWritten;
	}

	protected boolean writeComponentProperties(JSONWriter w, IToJSONConverter converter, WebComponent wc, String nodeName, DataConversion clientDataConversions)
		throws JSONException
	{
		TypedData<Map<String, Object>> wcProperties = wc.getProperties();
		if (wcProperties.content.isEmpty())
		{
			return false;
		}

		w.key(nodeName).object();
		clientDataConversions.pushNode(nodeName);

		Map<String, Object> data;
		if (wc.isVisible())
		{
			// write all properties
			data = wcProperties.content;
			wc.clearChanges();
		}
		else
		{
			// only write visibility properties
			data = new HashMap<>();
			for (Entry<String, Object> entry : wcProperties.content.entrySet())
			{
				if (wc.isVisibilityProperty(entry.getKey()))
				{
					data.put(entry.getKey(), entry.getValue());
					wc.flagPropertyAsDirty(entry.getKey(), false);
				}
				else
				{
					// will be sent as changed when component becomes visible
					wc.flagPropertyAsDirty(entry.getKey(), true);
				}
			}
		}

		JSONUtils.writeData(converter, w, data, wcProperties.contentType, clientDataConversions, wc);
		clientDataConversions.popNode();
		w.endObject();

		return true;
	}
}
