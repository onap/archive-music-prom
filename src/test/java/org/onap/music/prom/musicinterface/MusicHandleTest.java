/*
 * ============LICENSE_START==========================================
 * org.onap.music.prom
 * ===================================================================
 *  Copyright (c) 2018 AT&T Intellectual Property
 * ===================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 * ============LICENSE_END=============================================
 * ====================================================================
 */

package org.onap.music.prom.musicinterface;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.onap.music.prom.main.ConfigReader;
import org.onap.music.prom.main.PromUtil;
import org.onap.music.prom.musicinterface.MusicHandle;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ConfigReader.class, Client.class, ClientResponse.class,
		WebResource.class, WebResource.Builder.class, PromUtil.class})
public class MusicHandleTest {
	
	ClientResponse response;
	Client client;
	WebResource webresource;
	WebResource.Builder webresourceBuilder;
	
	
	@Before
	public void before() throws Exception {
		PowerMockito.mockStatic(ConfigReader.class);
		ArrayList<String> urls = new ArrayList<String>();
		Collections.addAll(urls, "10.1.2.3", "10.4.5.6");
		PowerMockito.when(ConfigReader.getConfigAttribute(Mockito.anyString(), Mockito.anyString()))
			.thenCallRealMethod();
		
		PowerMockito.mockStatic(PromUtil.class);
		//PowerMockito.spy(PromUtil.class);
		PowerMockito.when(PromUtil.getPromTimeout()).thenReturn(Integer.parseInt("1000"));
		PowerMockito.when(PromUtil.getMusicNodeURL()).thenReturn(urls);
		PowerMockito.when(PromUtil.getAid()).thenReturn("");
		PowerMockito.when(PromUtil.getAppNamespace()).thenReturn("");
		PowerMockito.when(PromUtil.getUserId()).thenReturn("");
		PowerMockito.when(PromUtil.getPassword()).thenReturn("");
		
		
		response = PowerMockito.mock(ClientResponse.class);
		PowerMockito.mockStatic(Client.class);
		client = PowerMockito.mock(Client.class);
		webresource = PowerMockito.mock(WebResource.class);
		webresourceBuilder = PowerMockito.mock(WebResource.Builder.class);
		
		//PowerMockito.when(Client.create()).thenReturn(client);
		PowerMockito.when(Client.create((ClientConfig) Mockito.anyObject())).thenReturn(client);
		PowerMockito.when(client.resource(Mockito.anyString())).thenReturn(webresource);
		PowerMockito.when(webresource.accept(MediaType.APPLICATION_JSON)).thenReturn(webresourceBuilder);
		PowerMockito.when(webresourceBuilder.type(MediaType.APPLICATION_JSON)).thenReturn(webresourceBuilder);
		PowerMockito.when(webresourceBuilder.get(ClientResponse.class)).thenReturn(response);
		PowerMockito.when(webresourceBuilder.post(ClientResponse.class)).thenReturn(response);

	}

	@Test
	public void acquireLockTestFailure() {
		Map<String, Object> acquireLockResponse = new HashMap<String, Object>();
		acquireLockResponse.put("status", "FAILURE");
		PowerMockito.when(response.getStatus()).thenReturn(200);
		PowerMockito.when(response.getEntity(Map.class)).thenReturn(acquireLockResponse);
		Map<String, Object> result = MusicHandle.acquireLock("testLock");
		assertEquals("FAILURE", result.get("status"));
	}
	
	@Test
	public void acquireLockTestFailureCannotReachFirstMusic() {
		PowerMockito.when(response.getStatus()).thenReturn(404, 404, 404, 200);
		Map<String, Object> acquireLockResponse = new HashMap<String, Object>();
		acquireLockResponse.put("status", "SUCCESS");
		PowerMockito.when(response.getEntity(Map.class)).thenReturn(acquireLockResponse);
		Map<String, Object> result = MusicHandle.acquireLock("testLock");
		assertEquals("SUCCESS", result.get("status"));
	}
	
	@Test
	public void acuireLockTestCannotReachAnyMusic() {
		PowerMockito.when(response.getStatus()).thenReturn(404);
		Map<String, Object> result = MusicHandle.acquireLock("testLock");
		assertEquals("FAILURE", result.get("status"));
	}


	@Test
	public void createLockRefSuccess() {
		Map<String, Object> acquireLockResponse = new HashMap<String, Object>();
		acquireLockResponse.put("status", "SUCCESS");
		Map<String, Object> lockMap = new HashMap<String, Object>();
		lockMap.put("lock", "abc_lockref");
		acquireLockResponse.put("lock", lockMap);
		PowerMockito.when(response.getStatus()).thenReturn(200);
		PowerMockito.when(response.getEntity(Map.class)).thenReturn(acquireLockResponse);
		
		String result = MusicHandle.createLockRef("testLock");
		assertEquals("abc_lockref", result);
	}
	
	@Test
	public void createLockRefFailure() {
		//Fail all music instances
		PowerMockito.when(response.getStatus()).thenReturn(404);
		String result = MusicHandle.createLockRef("testLock");
		assertEquals("", result);
	}
	
	@Test
	public void createLockRefFailFirstMusic() {
		PowerMockito.when(response.getStatus()).thenReturn(404, 404, 200);
		
		Map<String, Object> acquireLockResponse = new HashMap<String, Object>();
		acquireLockResponse.put("status", "SUCCESS");
		Map<String, Object> lockMap = new HashMap<String, Object>();
		lockMap.put("lock", "abc_lockref");
		acquireLockResponse.put("lock", lockMap);
		PowerMockito.when(response.getEntity(Map.class)).thenReturn(acquireLockResponse);
		
		String result = MusicHandle.createLockRef("testLock");
		assertEquals("abc_lockref", result);
	}
}
