/*
 * Copyright (C) 2016 Servoy BV
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sablo.specification.Package.IPackageReader;

/**
 * This class represents an immutable state of the web components or services provider state.
 *
 * @author rgansevles
 *
 */
public class SpecProviderState
{
	private final Map<String, PackageSpecification<WebObjectSpecification>> cachedDescriptions;
	private final Map<String, PackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions;
	private final Map<String, WebObjectSpecification> allWebObjectSpecifications;
	private final List<IPackageReader> packageReaders;

	public SpecProviderState(Map<String, PackageSpecification<WebObjectSpecification>> cachedDescriptions,
		Map<String, PackageSpecification<WebLayoutSpecification>> cachedLayoutDescriptions, Map<String, WebObjectSpecification> allWebObjectSpecifications,
		List<IPackageReader> packageReaders)
	{
		this.cachedDescriptions = Collections.unmodifiableMap(new HashMap<>(cachedDescriptions));
		this.cachedLayoutDescriptions = Collections.unmodifiableMap(new HashMap<>(cachedLayoutDescriptions));
		this.allWebObjectSpecifications = Collections.unmodifiableMap(new HashMap<>(allWebObjectSpecifications));
		this.packageReaders = Collections.unmodifiableList(new ArrayList<>(packageReaders));
	}

	public synchronized WebObjectSpecification getWebComponentSpecification(String componentTypeName)
	{
		return allWebObjectSpecifications.get(componentTypeName);
	}

	public synchronized Map<String, PackageSpecification<WebObjectSpecification>> getWebObjectSpecifications()
	{
		return cachedDescriptions;
	}

	public synchronized WebObjectSpecification[] getAllWebComponentSpecifications()
	{
		return allWebObjectSpecifications.values().toArray(new WebObjectSpecification[allWebObjectSpecifications.size()]);
	}

	public Map<String, PackageSpecification<WebLayoutSpecification>> getLayoutSpecifications()
	{
		return cachedLayoutDescriptions;
	}

	/**
	 * Get the map of packages and package URLs.
	 */
	public Map<String, URL> getPackagesToURLs()
	{
		Map<String, URL> result = new HashMap<String, URL>();
		for (IPackageReader reader : packageReaders)
		{
			result.put(reader.getPackageName(), reader.getPackageURL());
		}
		return result;
	}

	/**
	 * Get the map of packages and package display names.
	 */
	public Map<String, String> getPackagesToDisplayNames()
	{
		Map<String, String> result = new HashMap<String, String>();
		for (IPackageReader reader : packageReaders)
		{
			result.put(reader.getPackageName(), reader.getPackageDisplayname());
		}
		return result;
	}

	public IPackageReader getPackageReader(String packageName)
	{
		for (IPackageReader reader : packageReaders)
		{
			if (reader.getPackageName().equals(packageName)) return reader;
		}
		return null;
	}

	public String getPackageType(String packageName)
	{
		IPackageReader packageReader = getPackageReader(packageName);
		return packageReader != null ? packageReader.getPackageType() : null;
	}

	public String getPackageDisplayName(String packageName)
	{
		return getPackagesToDisplayNames().get(packageName);
	}

	public Collection<String> getPackageNames()
	{
		return getWebObjectSpecifications().keySet();
	}

	/**
	 * Get a list of all components contained by provided package name
	 */
	public Collection<String> getComponentsInPackage(String packageName)
	{
		PackageSpecification<WebObjectSpecification> pkg = getWebObjectSpecifications().get(packageName);
		return pkg == null ? Collections.<String> emptyList() : pkg.getSpecifications().keySet();

	}

	/**
	 * Get a list of all layouts contained by provided package name
	 */
	public Collection<String> getLayoutsInPackage(String packageName)
	{
		PackageSpecification<WebLayoutSpecification> pkg = getLayoutSpecifications().get(packageName);
		return pkg == null ? Collections.<String> emptyList() : pkg.getSpecifications().keySet();
	}

	public IPackageReader[] getAllPackageReaders()
	{
		Set<String> packageNames = getWebObjectSpecifications().keySet();
		IPackageReader[] readers = new IPackageReader[packageNames.size()];
		int i = 0;
		for (String name : packageNames)
		{
			readers[i++] = getPackageReader(name);
		}
		return readers;
	}

}