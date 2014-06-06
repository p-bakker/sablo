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

import org.sablo.BaseWebObject;
import org.sablo.specification.PropertyDescription;

/**
 * Context for data converters
 * @author gboros
 */
public class DataConverterContext implements IDataConverterContext {

	private PropertyDescription propertyDescription;
	private BaseWebObject webObject;
	
	public DataConverterContext(PropertyDescription propertyDescription, BaseWebObject webObject) {
		this.propertyDescription = propertyDescription;
		this.webObject = webObject;
	}
	
	@Override
	public PropertyDescription getPropertyDescription() {
		return propertyDescription;
	}

	@Override
	public BaseWebObject getWebObject() {
		return webObject;
	}
}
