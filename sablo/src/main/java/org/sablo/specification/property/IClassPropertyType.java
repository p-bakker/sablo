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

package org.sablo.specification.property;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.websocket.IForJsonConverter;
import org.sablo.websocket.utils.DataConversion;

/**
 * @author jcompagner
 */
public interface IClassPropertyType<C,C1> extends IPropertyType<C> {
	
	/**
	 * @return
	 */
	Class<C> getTypeClass();
	
	public C1 fromJSON(Object newValue, C previousValue);
	
	public void toJSON(JSONWriter writer, C object, DataConversion clientConversion, IForJsonConverter forJsonConverter) throws JSONException;
}
