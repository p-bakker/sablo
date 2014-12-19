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

import org.json.JSONObject;
import org.sablo.eventthread.IEventDispatcher;

/**
 * A service that will need to execute some of it's methods using an "eventLevel" different then {@link IEventDispatcher#EVENT_LEVEL_DEFAULT}.
 *
 * @author acostescu
 */
public interface IEventDispatchAwareServerService extends IServerService
{

	/**
	 * Most implementations will simply return here {@link IEventDispatcher#EVENT_LEVEL_DEFAULT}.
	 * This is the "eventLevel" used to schedule the event for executiong on the event dispatcher thread. For more info see {@link IEventDispatcher#suspend(Object, int)}.
	 *
	 * @param methodName the method that will be scheduled for execution.
	 * @param arguments the arguments it will receive.
	 *
	 * @return the "eventLevel" used to schedule the event for executiong on the event dispatcher thread.
	 */
	public int getMethodEventThreadLevel(String methodName, JSONObject arguments);

}