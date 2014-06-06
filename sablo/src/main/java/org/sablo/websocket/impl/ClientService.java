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
package org.sablo.websocket.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.sablo.BaseWebObject;
import org.sablo.specification.WebComponentApiDefinition;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.specification.property.IClassPropertyType;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.WebsocketEndpoint;

/**
 * implementation of {@link IClientService}
 * 
 * @author jcompagner
 */
public class ClientService extends BaseWebObject implements IClientService {


	public ClientService(String serviceName) {
		super(serviceName,  WebServiceSpecProvider.getInstance().getWebServiceSpecification(serviceName));
	}
	
	@Override
	public Object executeServiceCall(String functionName, Object[] arguments) throws IOException
	{
		Map<String, Object> serviceChanges = getChanges();
		Object retValue = WebsocketEndpoint.get().executeServiceCall(name, functionName, arguments, serviceChanges.isEmpty()?null:Collections.singletonMap("services", Collections.singletonMap(getName(),serviceChanges)));
		if (retValue != null) {
			WebComponentSpecification spec = WebServiceSpecProvider.getInstance().getWebServiceSpecification(name);
			if (spec != null) {
				WebComponentApiDefinition apiFunction = spec.getApiFunction(functionName);
				if (apiFunction != null && apiFunction.getReturnType() != null && apiFunction.getReturnType().getType() instanceof IClassPropertyType) {
					// TODO wrapper types return now directly the wrapper class in toJava() this should not be returned.
//					if (apiFunction.getReturnType().getType() instanceof IWrapperType) {
//						return 
//					}
					return ((IClassPropertyType)apiFunction.getReturnType().getType()).fromJSON(retValue, null);
				}
			}
		}
		return retValue;
	}

	@Override
	public void executeAsyncServiceCall(String functionName, Object[] arguments)
	{
		WebsocketEndpoint.get().executeAsyncServiceCall(name, functionName, arguments);
	}
}
