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

package org.sablo.specification;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.WebComponentPackage.IPackageReader;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomPropertyTypeResolver;
import org.sablo.specification.property.ICustomType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.specification.property.types.TypesRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse .spec files for components.
 * @author rgansevles
 */
public class WebComponentSpecification extends PropertyDescription
{
	private static final Logger log = LoggerFactory.getLogger(WebComponentSpecification.class.getCanonicalName());

	public static final String TYPES_KEY = "types";
	private final Map<String, PropertyDescription> handlers = new HashMap<>(); // second String is always a "function" for now, but in the future it will probably contain more (to specify sent args/types...)
	private final Map<String, WebComponentApiDefinition> apis = new HashMap<>();
	private final String definition;
	private final JSONArray libraries;
	private final String displayName;
	private final String categoryName;
	private final String icon;
	private final String packageName;

	private final Map<String, IPropertyType< ? >> foundTypes;

	private URL serverScript;

	private URL specURL;

	private URL definitionURL;

	public WebComponentSpecification(String name, String packageName, String displayName, String categoryName, String icon, String definition, JSONArray libs)
	{
		super(name, null);
		this.packageName = packageName;
		this.displayName = displayName;
		this.categoryName = categoryName;
		this.icon = icon;
		this.definition = definition;
		this.libraries = libs != null ? libs : new JSONArray();
		this.foundTypes = new HashMap<>();
	}

	public WebComponentSpecification(String name, String packageName, String displayName, String categoryName, String icon, String definition, JSONArray libs,
		Object configObject)
	{
		super(name, null, configObject);
		this.packageName = packageName;
		this.displayName = displayName;
		this.categoryName = categoryName;
		this.icon = icon;
		this.definition = definition;
		this.libraries = libs != null ? libs : new JSONArray();
		this.foundTypes = new HashMap<>();
	}


	/**
	 * @param serverScript the serverScript to set
	 */
	public void setServerScript(URL serverScript)
	{
		this.serverScript = serverScript;
	}

	/**
	 * @return
	 */
	public URL getServerScript()
	{
		return serverScript;
	}


	protected final void addApiFunction(WebComponentApiDefinition apiFunction)
	{
		apis.put(apiFunction.getName(), apiFunction);
	}

	protected final void addHandler(PropertyDescription propertyDescription)
	{
		handlers.put(propertyDescription.getName(), propertyDescription);
	}

	/**
	 * @param hndlrs
	 */
	protected final void putAllHandlers(Map<String, PropertyDescription> hndlrs)
	{
		handlers.putAll(hndlrs);
	}

	/**
	 * You are not allowed to modify this map!
	 */
	public Map<String, PropertyDescription> getHandlers()
	{
		return Collections.unmodifiableMap(handlers);
	}

	public PropertyDescription getHandler(String handlerName)
	{
		return handlers.get(handlerName);
	}

	public WebComponentApiDefinition getApiFunction(String apiFunctionName)
	{
		return apis.get(apiFunctionName);
	}

	public Map<String, WebComponentApiDefinition> getApiFunctions()
	{
		return Collections.unmodifiableMap(apis);
	}

	public String getDisplayName()
	{
		return displayName == null ? getName() : displayName;
	}

	public String getCategoryName()
	{
		return categoryName;
	}

	public String getIcon()
	{
		return icon;
	}

	public String getPackageName()
	{
		return packageName;
	}

	public String getDefinition()
	{
		return definition;
	}

	@Override
	public Set<String> getAllPropertiesNames()
	{
		Set<String> names = new HashSet<>(super.getAllPropertiesNames());
		names.addAll(handlers.keySet());
		return names;
	}

	public JSONArray getLibraries()
	{
		return libraries;
	}

	private ParsedProperty parsePropertyString(final String propertyString)
	{
		String property = propertyString.replaceAll("\\s", "");
		boolean isArray = false;
		if (property.endsWith("[]"))
		{
			isArray = true;
			property = property.substring(0, property.length() - 2);
		}
		// first check the local ones.
		IPropertyType< ? > t = foundTypes.get(property);
		if (t == null) t = TypesRegistry.getType(property);
		return new ParsedProperty(t, isArray);
	}

	/**
	 *
	 * @return the types parsed from the "types" attribute.
	 */
	public Map<String, IPropertyType< ? >> getFoundTypes()
	{
		return foundTypes;
	}

	public static Map<String, IPropertyType< ? >> getTypes(JSONObject typesContainer) throws JSONException
	{
		WebComponentSpecification spec = new WebComponentSpecification("", "", "", null, null, "", null);
		spec.parseTypes(typesContainer);
		return spec.foundTypes;
	}

	@SuppressWarnings("unchecked")
	public static WebComponentSpecification parseSpec(String specfileContent, String packageName, IPackageReader reader) throws JSONException
	{
		JSONObject json = new JSONObject(specfileContent);

		WebComponentSpecification spec = new WebComponentSpecification(json.getString("name"), packageName, json.optString("displayName", null),
			json.optString("categoryName", null), json.optString("icon", null), json.getString("definition"), json.optJSONArray("libraries"));

		if (json.has("serverscript"))
		{
			spec.setServerScript(reader.getUrlForPath(json.getString("serverscript").substring(packageName.length())));
		}
		// first types, can be used in properties
		spec.parseTypes(json);

		// properties
		spec.putAll(spec.parseProperties("model", json));
		spec.putAllHandlers(spec.parseProperties("handlers", json));

		// api
		if (json.has("api"))
		{
			JSONObject api = json.getJSONObject("api");
			Iterator<String> itk = api.keys();
			while (itk.hasNext())
			{
				String func = itk.next();
				JSONObject jsonDef = api.getJSONObject(func);
				WebComponentApiDefinition def = new WebComponentApiDefinition(func);

				Iterator<String> it = jsonDef.keys();
				JSONObject customConfiguration = null;
				while (it.hasNext())
				{
					String key = it.next();
					if ("parameters".equals(key))
					{
						JSONArray params = jsonDef.getJSONArray("parameters");
						for (int p = 0; p < params.length(); p++)
						{
							JSONObject param = params.getJSONObject(p);

							ParsedProperty pp = spec.parsePropertyString(param.getString("type"));
							// hmm why not set the array field instead of configObject here?
							def.addParameter(new PropertyDescription((String)param.get("name"), resolveArrayType(pp), param, null, null, null,
								param.has("optional")));
						}
					}
					else if ("returns".equals(key))
					{
						ParsedProperty pp = spec.parsePropertyString(jsonDef.getString("returns"));
						PropertyDescription desc = new PropertyDescription("return", resolveArrayType(pp));
						def.setReturnType(desc);
					}
					else
					{
						if (customConfiguration == null) customConfiguration = new JSONObject();
						customConfiguration.put(key, jsonDef.get(key));
					}
				}
				if (customConfiguration != null) def.setCustomConfigOptions(customConfiguration);

				spec.addApiFunction(def);
			}
		}
		return spec;
	}

	private static IPropertyType< ? > resolveArrayType(ParsedProperty pp)
	{
		if (pp.array)
		{
			return TypesRegistry.createNewType(CustomJSONArrayType.TYPE_NAME, new PropertyDescription("", pp.type));
		}
		return pp.type;
	}

	/**
	 * Parses json spec object for declared custom types; custom type will be stored prefixed by spec name (if available)
	 *
	 * @param json JSON to parse for custom types;
	 *
	 * @throws JSONException
	 */
	void parseTypes(JSONObject json) throws JSONException
	{
		String specName = json.optString("name", null);
		if (json.has("types"))
		{
			JSONObject jsonObject = json.getJSONObject("types");
			// first create all types
			Iterator<String> types = jsonObject.keys();
			while (types.hasNext())
			{
				String name = types.next();
				ICustomType< ? > wct = CustomPropertyTypeResolver.getInstance().resolveCustomPropertyType(specName != null ? (specName + "." + name) : name);
				foundTypes.put(name, wct);
			}

			// then parse all the types (so that they can find each other)
			types = jsonObject.keys();
			while (types.hasNext())
			{
				String typeName = types.next();
				ICustomType< ? > type = (ICustomType< ? >)foundTypes.get(typeName);
				JSONObject typeJSON = jsonObject.getJSONObject(typeName);
				if (typeJSON.has("model"))
				{
					// TODO will we really use anything else but model (like api/handlers)? Cause if not, we can just drop the need for "model"
					type.getCustomJSONTypeDefinition().putAll(parseProperties("model", typeJSON));
				}
				else
				{
					// allow custom types to be defined even without the "model" clutter
					type.getCustomJSONTypeDefinition().putAll(parseProperties(typeName, jsonObject));
				}
				// TODO this is currently never true? See 5 lines above this, types are always just PropertyDescription?
				// is this really supported? or should we add it just to the properties? But how are these handlers then added and used
				if (type instanceof WebComponentSpecification)
				{
					((WebComponentSpecification)type).putAllHandlers(parseProperties("handlers", jsonObject.getJSONObject(typeName)));
				}
			}
		}
	}

	protected Map<String, PropertyDescription> parseProperties(String propKey, JSONObject json) throws JSONException
	{
		Map<String, PropertyDescription> pds = new HashMap<>();
		if (json.has(propKey))
		{
			JSONObject jsonProps = json.getJSONObject(propKey);
			Iterator<String> itk = jsonProps.keys();
			while (itk.hasNext())
			{
				String key = itk.next();
				Object value = jsonProps.get(key);
				IPropertyType< ? > type = null;
				boolean isArray = false;
				JSONObject configObject = null;
				Object defaultValue = null;
				JSONObject tags = null;
				List<Object> values = null;
				if (value instanceof String)
				{
					ParsedProperty pp = parsePropertyString((String)value);
					isArray = pp.array;
					type = pp.type;
				}
				else if (value instanceof JSONObject && ((JSONObject)value).has("type"))
				{
					ParsedProperty pp = parsePropertyString(((JSONObject)value).getString("type"));
					type = pp.type;
					isArray = pp.array;
					configObject = ((JSONObject)value);
					defaultValue = configObject.opt("default");
					tags = configObject.optJSONObject("tags");

					JSONArray valuesArray = configObject.optJSONArray("values");
					if (valuesArray != null)
					{
						values = new ArrayList<Object>(valuesArray.length());
						for (int i = 0; i < valuesArray.length(); i++)
						{
							values.add(valuesArray.get(i));
						}
					}
				}
				if (type != null)
				{
					if (isArray)
					{
						// here we could have something like { type: 'myprop[]', a: ..., b: ... } so with a config object;
						// the config object will be used by the 'CustomJSONArray' type;
						// a config for the element type can be specified like this: { type: 'myprop[]', a: ..., b: ..., elementConfig: {...} } and we could give that to the elementDescription instead
						JSONObject elementConfig = configObject != null ? configObject.optJSONObject(CustomJSONArrayType.ELEMENT_CONFIG_KEY) : null;
						PropertyDescription elementDescription = new PropertyDescription("", type, elementConfig != null ? type.parseConfig(elementConfig)
							: null, defaultValue, values, tags, false);
						type = TypesRegistry.createNewType(CustomJSONArrayType.TYPE_NAME, elementDescription);
					}

					pds.put(key, new PropertyDescription(key, type, type.parseConfig(configObject), defaultValue, values, tags, false));
				}
			}
		}
		return pds;
	}

	@Override
	public String toString()
	{
		return getName();
	}

	private static class ParsedProperty
	{
		private final IPropertyType type;
		private final boolean array;

		ParsedProperty(IPropertyType type, boolean array)
		{
			this.type = type;
			this.array = array;
		}
	}

	/**
	 * @param dropTargetFieldName
	 */
	public boolean isArrayReturnType(String dropTargetFieldName)
	{
		if (getProperty(dropTargetFieldName) != null && getProperty(dropTargetFieldName).getType() instanceof CustomJSONArrayType< ? , ? >) return true;
		return false;
	}


	/**
	 * Get the location of the specFile inside.
	 * @return
	 */
	public URL getSpecURL()
	{
		return specURL;
	}

	/**
	 * @param url
	 */
	public void setSpecURL(URL url)
	{
		specURL = url;
	}

	/**
	 * @param url
	 */
	public void setDefinitionFileURL(URL url)
	{
		definitionURL = url;
	}

	public URL getDefinitionURL()
	{
		return definitionURL;
	}
}
