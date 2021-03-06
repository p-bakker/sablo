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

/**
 * Context for wrapping/unwrapping.
 *
 * @author acostescu
 */
public class WrappingContext implements IWrappingContext
{

	protected final BaseWebObject webObject;
	private final String propertyName;

	public WrappingContext(BaseWebObject webObject, String propertyName)
	{
		this.webObject = webObject;
		this.propertyName = propertyName;
	}

	public BaseWebObject getWebObject()
	{
		return webObject;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sablo.specification.property.IWrappingContext#getPropertyName()
	 */
	@Override
	public String getPropertyName()
	{
		return propertyName;
	}

}
