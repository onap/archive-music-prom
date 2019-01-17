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

package org.onap.music.prom.main;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.core.IsAnything;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.runners.MockitoJUnitRunner;
import org.onap.music.prom.main.ConfigReader;
import org.onap.music.prom.main.PromDaemon;
import org.onap.music.prom.main.PromUtil;
import org.onap.music.prom.main.PromDaemon.CoreState;
import org.onap.music.prom.musicinterface.MusicHandle;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;


@RunWith(PowerMockRunner.class)
@PrepareForTest({MusicHandle.class, ConfigReader.class, PromUtil.class})
public class PromDaemonTest {
	static PromDaemon promDaemon;

	@BeforeClass
	public static void beforeClass() {
		promDaemon = Mockito.spy(PromDaemon.class);
	}
	
	@Before
	public void before() {
		promDaemon.lockName = "lockName";
		promDaemon.keyspaceName = "keyspaceName";
		promDaemon.id = "anIdToTestFor";
		
		PowerMockito.mockStatic(ConfigReader.class);
		PowerMockito.when(ConfigReader.getConfigAttribute("prom-timeout")).thenReturn("1000");
		PowerMockito.when(ConfigReader.getConfigAttribute("core-monitor-sleep-time", "1000")).thenReturn("1");
	}
	
	
	@Test
	public void bootstrapTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		PowerMockito.mockStatic(ConfigReader.class);
		
		PowerMockito.when(ConfigReader.getConfigAttribute("app-name")).thenReturn("testing");
		
		Whitebox.invokeMethod(promDaemon,  "bootStrap");
		
		assertEquals("prom_testing", promDaemon.keyspaceName);
		assertEquals("prom_testing.Replicas.PROM_ADMIN", promDaemon.lockName);
	}
	
	@Test
	public void acquireLockTrue() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		
		HashMap<String, Object> falseMap = new HashMap<String, Object>();
		promDaemon.lockRef = "testLock";
		falseMap.put("status", "SUCCESS");
		falseMap.put("message", "You already have the lock");
		PowerMockito.when(MusicHandle.acquireLock("testLock")).thenReturn(falseMap);	
		
		Boolean acquireLock = Whitebox.invokeMethod(promDaemon,  "acquireLock");
		assertTrue(acquireLock);
	}
	
	@Test
	public void acquireLockFalse() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		
		HashMap<String, Object> falseMap = new HashMap<String, Object>();
		promDaemon.lockRef = "testLock";
		falseMap.put("status", "FAILURE");
		falseMap.put("message", "you don't own the lock");
		PowerMockito.when(MusicHandle.acquireLock("testLock")).thenReturn(falseMap);	
		
		Boolean acquireLock = Whitebox.invokeMethod(promDaemon,  "acquireLock");
		assertFalse(acquireLock);
	}
	
	@Test
	public void acquireNullLock() throws Exception {
		promDaemon.lockRef = null;
		
		Boolean acquireLock = Whitebox.invokeMethod(promDaemon,  "acquireLock");
		assertFalse(acquireLock);
	}

	@Test
	public void activeLockHolderTestTrue() throws Exception{
		PowerMockito.mockStatic(MusicHandle.class);
		
		HashMap<String, Object> falseMap = new HashMap<String, Object>();
		promDaemon.lockRef = "testLock";
		falseMap.put("status", "SUCCESS");
		falseMap.put("message", "You already have the lock");
		PowerMockito.when(MusicHandle.acquireLock("testLock")).thenReturn(falseMap);	
		
		Boolean isActiveLockHolder = Whitebox.invokeMethod(promDaemon,  "isActiveLockHolder");
		assertTrue(isActiveLockHolder);
	}
	
	@Test
	public void activeLockHolderTestFalse() throws Exception{
		PowerMockito.mockStatic(MusicHandle.class);
		
		HashMap<String, Object> falseMap = new HashMap<String, Object>();
		falseMap.put("status", "FAILURE");
		falseMap.put("message", "You do not own the lock");
		PowerMockito.when(MusicHandle.acquireLock("testLock")).thenReturn(falseMap);	
		
		Boolean isActiveLockHolder = Whitebox.invokeMethod(promDaemon,  "isActiveLockHolder");
		assertFalse(isActiveLockHolder);
	}
	
	@Test
	public void activeLockHolderTestStaleLock() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		
		HashMap<String, Object> staleLockMap = new HashMap<String, Object>();
		promDaemon.lockRef = "testLock";
		staleLockMap.put("status", "FAILURE");
		staleLockMap.put("message", "Lockid doesn't exist");
		PowerMockito.when(MusicHandle.acquireLock("testLock")).thenReturn(staleLockMap);
		
		PowerMockito.when(MusicHandle.createLockRef("lockName")).thenReturn("testLock2");
		
		HashMap<String, Object> falseMap = new HashMap<String, Object>();
		falseMap.put("status", "FAILURE");
		falseMap.put("message", "You do not own the lock");
		PowerMockito.when(MusicHandle.acquireLock("testLock2")).thenReturn(falseMap);
		
		Boolean isActiveLockHolder = Whitebox.invokeMethod(promDaemon,  "isActiveLockHolder");
		assertFalse(isActiveLockHolder);
		assertEquals("testLock2", promDaemon.lockRef);
	}
	
	
	@Test
	public void releaseLockTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		
		Whitebox.invokeMethod(promDaemon, "releaseLock", null);
		Whitebox.invokeMethod(promDaemon, "releaseLock", "");
		
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
			.thenReturn(null);
		Whitebox.invokeMethod(promDaemon, "releaseLock", "lock1");
		
		//should actually release now
		ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
		HashMap<String,Object> map = new HashMap<String,Object>();
		HashMap<String,Object> repDetails = new HashMap<String,Object>();
		repDetails.put("id", promDaemon.id);
		map.put("row 0", repDetails);
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
		.thenReturn(map);
		Whitebox.invokeMethod(promDaemon, "releaseLock", "lock1");
		
		PowerMockito.verifyStatic();
		MusicHandle.updateTableEventual(Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString(), mapCaptor.capture());
		assertEquals(false, mapCaptor.getValue().get("isactive"));
	}

	@Test
	public void activeHealthTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);

		ArgumentCaptor<String> keyspaceCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
		Whitebox.invokeMethod(promDaemon, "updateHealth", CoreState.ACTIVE);

		PowerMockito.verifyStatic();
		MusicHandle.insertIntoTableEventual(keyspaceCaptor.capture(), tableCaptor.capture(), mapCaptor.capture());
		Map<String, Object> returnedMap = mapCaptor.getValue();

		assertTrue((Boolean) returnedMap.get("isactive"));
		assertTrue(System.currentTimeMillis()-500< (long) returnedMap.get("timeoflastupdate"));
	}
	
	@Test
	public void passiveHealthTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);

		ArgumentCaptor<String> keyspaceCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
		Whitebox.invokeMethod(promDaemon, "updateHealth", CoreState.PASSIVE);

		PowerMockito.verifyStatic();
		MusicHandle.insertIntoTableEventual(keyspaceCaptor.capture(), tableCaptor.capture(), mapCaptor.capture());
		Map<String, Object> returnedMap = mapCaptor.getValue();

		assertFalse((Boolean) returnedMap.get("isactive"));
		//make sure call was somewhat recent, as synched with current system clock
		//may need to make this more strict or less strict depending
		assertTrue(System.currentTimeMillis()-500< (long) returnedMap.get("timeoflastupdate"));
	}
	

	@Test
	public void replicaIsAliveTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		
		
		//no test replica
		Boolean isReplicaAlive = Whitebox.invokeMethod(promDaemon,  "isReplicaAlive", "testReplica");
		assertFalse(isReplicaAlive);
		
		//return null pointer
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(null);
		isReplicaAlive = Whitebox.invokeMethod(promDaemon,  "isReplicaAlive", "testReplica");
		assertFalse(isReplicaAlive);

		//is active is dead
		Map<String,Object> deadReplica = new HashMap<String,Object>();
		Map<String,Object> replicaInfo = new HashMap<String,Object>();
		replicaInfo.put("id", "testReplica");
		replicaInfo.put("isactive", "false");
		deadReplica.put("row 0", replicaInfo);
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(deadReplica);
		isReplicaAlive = Whitebox.invokeMethod(promDaemon,  "isReplicaAlive", "testReplica");
		assertFalse(isReplicaAlive);
		
		//timed out
		replicaInfo.put("timeoflastupdate", System.currentTimeMillis()-1000);
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(deadReplica);

		isReplicaAlive = Whitebox.invokeMethod(promDaemon,  "isReplicaAlive", "testReplica");
		assertFalse(isReplicaAlive);
		
		//alive
		replicaInfo.put("timeoflastupdate", System.currentTimeMillis());
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(deadReplica);
		PowerMockito.when(ConfigReader.getConfigAttribute("prom-timeout")).thenReturn("1000");
		isReplicaAlive = Whitebox.invokeMethod(promDaemon,  "isReplicaAlive", "testReplica");
		assertTrue(isReplicaAlive);
	}
	
	
	/**
	 * try to start as passive. First iteration will fail because the replica is stale.
	 * Second iteration should exit the method. In failure cases, this might throw an 
	 * exception to prevent an infinite loop.
	 * 
	 * @throws Exception
	 */
	@Test
	public void startAsPassiveReplicaTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		String activeLock = "actLock";
		Map<String, Object> staleActiveMap = new HashMap<String, Object>();
		Map<String, Object> staleInfo = new HashMap<String, Object>();
		staleInfo.put("id", "activeReplica");
		staleInfo.put("isactive", true);
		staleInfo.put("timeoflastupdate", System.currentTimeMillis()-1001);
		staleActiveMap.put("row 0", staleInfo);
		Map<String, Object> activeActiveMap = new HashMap<String, Object>();
		Map<String, Object> activeInfo = new HashMap<String, Object>();
		activeInfo.put("id", "activeReplica");
		activeInfo.put("isactive", true);
		activeInfo.put("timeoflastupdate", System.currentTimeMillis());
		activeActiveMap.put("row 0", activeInfo);
		
		PowerMockito.when(MusicHandle.whoIsLockHolder(promDaemon.lockName)).thenReturn(activeLock);
		PowerMockito.when(MusicHandle.readSpecificRow(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
			.thenReturn(staleActiveMap).thenReturn(staleActiveMap)
			.thenReturn(activeActiveMap).thenReturn(activeActiveMap)
			.thenThrow(new RuntimeException("Should exit before reaching here"));
		
		Whitebox.invokeMethod(promDaemon,  "startAsPassiveReplica");
		
		//make sure we went through 2 iterations. Each iteration makes 2 calls to readSpecific row so 2x2 is 4
		PowerMockito.verifyStatic(VerificationModeFactory.times(4));
		MusicHandle.readSpecificRow(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
	}
	
	
	@Test
	public void getLockRefOrOldLockRefIfExistsTest() throws Exception {
		PowerMockito.mockStatic(MusicHandle.class);
		
		//no entry in music
		PowerMockito.when(MusicHandle.createLockRef(promDaemon.lockName)).thenReturn("aNewLockRef1");
		String lockref = Whitebox.invokeMethod(promDaemon, "getLockRefOrOldLockRefIfExists");
		assertEquals("aNewLockRef1", lockref);
		
		//entry in music doesn't have lockref column
		Map<String, Object> entriesInMusic = new HashMap<String, Object>();
		Map<String, Object> entry = new HashMap<String, Object>();
		entry.put("id", promDaemon.id);
		entriesInMusic.put("row 0", entry);
		PowerMockito.when(MusicHandle.readSpecificRow(promDaemon.keyspaceName, promDaemon.tableName, "id", promDaemon.id))
				.thenReturn(entriesInMusic);
		PowerMockito.when(MusicHandle.createLockRef(promDaemon.lockName)).thenReturn("aNewLockRef2");
		lockref = Whitebox.invokeMethod(promDaemon, "getLockRefOrOldLockRefIfExists");
		assertEquals("aNewLockRef2", lockref);

		//entry in music didn't previously have a lockref
		entry.put("lockref", null);
		PowerMockito.when(MusicHandle.readSpecificRow(promDaemon.keyspaceName, promDaemon.tableName, "id", promDaemon.id))
		.thenReturn(entriesInMusic);
		PowerMockito.when(MusicHandle.createLockRef(promDaemon.lockName)).thenReturn("aNewLockRef3");
		lockref = Whitebox.invokeMethod(promDaemon, "getLockRefOrOldLockRefIfExists");
		assertEquals("aNewLockRef3", lockref);
		
		//entry in music didn't previously have a lockref
		entry.put("lockref", "");
		PowerMockito.when(MusicHandle.readSpecificRow(promDaemon.keyspaceName, promDaemon.tableName, "id", promDaemon.id))
				.thenReturn(entriesInMusic);
		PowerMockito.when(MusicHandle.createLockRef(promDaemon.lockName)).thenReturn("aNewLockRef4");
		lockref = Whitebox.invokeMethod(promDaemon, "getLockRefOrOldLockRefIfExists");
		assertEquals("aNewLockRef4", lockref);
		
		//entry had a previous lock entry
		entry.put("lockref", "previousLockRef");
		PowerMockito.when(MusicHandle.readSpecificRow(promDaemon.keyspaceName, promDaemon.tableName, "id", promDaemon.id))
				.thenReturn(entriesInMusic);
		lockref = Whitebox.invokeMethod(promDaemon, "getLockRefOrOldLockRefIfExists");
		assertEquals("previousLockRef", lockref);
	}
}
