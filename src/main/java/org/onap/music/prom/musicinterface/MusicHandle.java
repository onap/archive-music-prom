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
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.onap.music.prom.eelf.logging.EELFLoggerDelegate;
import org.onap.music.prom.main.ConfigReader;
import org.onap.music.prom.main.PromUtil;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

public class MusicHandle {
	private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(MusicHandle.class);


	/**
	 * Adds MUSIC's authentication headers into the webresource
	 * @param webResource
	 */
	private static Builder addMusicHeaders(WebResource webResource) {
		String aid = PromUtil.getAid();
		String namespace = PromUtil.getAppNamespace();
		String userId = PromUtil.getUserId();
		String password = PromUtil.getPassword();
		Builder builder = webResource.accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON);
		if (!aid.equals("")) {
			builder.header("aid", aid);
		}
		if (!namespace.equals("")) {
			builder.header("ns", namespace);
		}
		if (!userId.equals("")) {
			builder.header("userId", userId);
		}
		if (!password.equals("")) {
			builder.header("password", password);
		}
		
		return builder;
	}
	
	private static WebResource createMusicWebResource(String musicAPIurl) {
		ClientConfig clientConfig = new DefaultClientConfig();

		clientConfig.getFeatures().put(
				JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
		int timeout = getMaxConnectionTimeout();
		clientConfig.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, timeout);
		clientConfig.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, timeout);

		Client client = Client.create(clientConfig);
		return client.resource(musicAPIurl);
	}
	
	public static void createKeyspaceEventual(String keyspaceName){
		logger.info(EELFLoggerDelegate.applicationLogger, "createKeyspaceEventual "+keyspaceName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				createKeyspaceEventual(musicUrl, keyspaceName);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to create keyspace."
							+ "Could not successfully reach any music instance to create keyspace.");
		return;
	}
	
	private static void createKeyspaceEventual(String musicUrl, String keyspaceName){
		Map<String,Object> replicationInfo = new HashMap<String, Object>();
		replicationInfo.put("class", "SimpleStrategy");
		replicationInfo.put("replication_factor", 3);
		String durabilityOfWrites="true";
		Map<String,String> consistencyInfo= new HashMap<String, String>();
		consistencyInfo.put("type", "eventual");
		JsonKeySpace jsonKp = new JsonKeySpace();
		jsonKp.setConsistencyInfo(consistencyInfo);
		jsonKp.setDurabilityOfWrites(durabilityOfWrites);
		jsonKp.setReplicationInfo(replicationInfo);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName);

		ClientResponse response = addMusicHeaders(webResource)
									.post(ClientResponse.class, jsonKp);
		
		Map<String,Object> output = response.getEntity(Map.class);
		if (!output.containsKey("status") || !output.get("status").equals("SUCCESS")) {
			if (output.containsKey("error")) {
				String errorMsg = String.valueOf(output.get("error"));
				if (errorMsg.equals("err:Keyspace prom_sdnc already exists")) {
					logger.warn(EELFLoggerDelegate.applicationLogger, 
							"Not creating keyspace " + keyspaceName + " because it already exists. Continuing.");
					//assume keyspace is already created and continue
				}
				else { //unhandled/unknown exception
					logger.error(EELFLoggerDelegate.errorLogger,
							"Failed to createKeySpaceEventual : Status Code "+ output.toString());
					throw new RuntimeException("Failed: MUSIC Response " + output.toString());
				}
			} else { //no exception message
				logger.error(EELFLoggerDelegate.errorLogger,
						"Failed to createKeySpaceEventual : Status Code "+ output.toString());
				throw new RuntimeException("Failed: MUSIC Response " + output.toString());
			}
		}
	}

	public static void createTableEventual(String keyspaceName, String tableName, Map<String,String> fields) {
		logger.info(EELFLoggerDelegate.applicationLogger,
				"createKeyspaceEventual "+keyspaceName+" tableName "+tableName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				createTableEventual(musicUrl, keyspaceName, tableName, fields);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");

		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to create table. "
						+ "Could not successfully reach any music instance.");
		return;
	}
	
	private static void createTableEventual(String musicUrl, String keyspaceName,
										String tableName, Map<String,String> fields) {
		Map<String,String> consistencyInfo= new HashMap<String, String>();
		consistencyInfo.put("type", "eventual");

		JsonTable jtab = new JsonTable();
		jtab.setFields(fields);
		jtab.setConsistencyInfo(consistencyInfo);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName+"/tables/"+tableName);

		ClientResponse response = addMusicHeaders(webResource).post(ClientResponse.class, jtab);

		Map<String,Object> output = response.getEntity(Map.class);
		if (!output.containsKey("status") || !output.get("status").equals("SUCCESS")) {
			if (output.containsKey("error")) {
				String error = String.valueOf(output.get("error"));
				if (error.equalsIgnoreCase("Table " + keyspaceName + "." + tableName + " already exists")) {
					logger.warn(EELFLoggerDelegate.applicationLogger, 
							"Not creating table " + tableName + " because it already exists. Continuing.");
				} else { //unhandled exception message
					logger.error(EELFLoggerDelegate.errorLogger,
							"Failed to createTableEventual : Status Code "+ output.toString());
					throw new RuntimeException("Failed: MUSIC Response " + output.toString());
				}
			} else { //no exception message, MUSIC should give more info if failure
				logger.error(EELFLoggerDelegate.errorLogger, 
						"Failed to createTableEventual : Status Code "+ output.toString());
				throw new RuntimeException("Failed: MUSIC Response " + output.toString());
			}
		}
	}

	public static void createIndexInTable(String keyspaceName, String tableName, String colName) {
		logger.info(EELFLoggerDelegate.applicationLogger,
				"createIndexInTable "+keyspaceName+" tableName "+tableName + " colName" + colName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				createIndexInTable(musicUrl, keyspaceName, tableName, colName);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to create index in table. "
									+ "Could not successfully reach any music instance.");
		return;
	}
	
	private static void createIndexInTable(String musicUrl, String keyspaceName, String tableName, String colName) {
		WebResource webResource = createMusicWebResource(musicUrl +
							"/keyspaces/"+keyspaceName+"/tables/"+tableName+"/index/"+colName);

		ClientResponse response = addMusicHeaders(webResource).post(ClientResponse.class);

		if (response.getStatus() != 200 && response.getStatus() != 204) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"Failed to createIndexInTable : Status Code " + response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
		}

	}

	public static void insertIntoTableEventual(String keyspaceName, String tableName, Map<String,Object> values) {
		logger.info(EELFLoggerDelegate.applicationLogger,
				"insertIntoTableEventual "+keyspaceName+" tableName "+tableName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				insertIntoTableEventual(musicUrl, keyspaceName, tableName, values);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to insert into table."
								+ " Could not successfully reach any music instance.");
		return;
	}
	
	private static void insertIntoTableEventual(String musicUrl, String keyspaceName,
											String tableName, Map<String,Object> values) {
		Map<String,String> consistencyInfo= new HashMap<String, String>();
		consistencyInfo.put("type", "eventual");

		JsonInsert jIns = new JsonInsert();
		jIns.setValues(values);
		jIns.setConsistencyInfo(consistencyInfo);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName+"/tables/"+tableName+"/rows");

		ClientResponse response = addMusicHeaders(webResource).post(ClientResponse.class, jIns);

		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"Failed to insertIntoTableEventual : Status Code " + response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
		}

		Map<String,Object> output = response.getEntity(Map.class);
		if (!output.containsKey("status") || !output.get("status").equals("SUCCESS")) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to createKeySpaceEventual : Status Code "+ output.toString());
			throw new RuntimeException("Failed: MUSIC Response " + output.toString());
		}
	}

	public static void updateTableEventual(String keyspaceName, String tableName, String keyName,
													String keyValue, Map<String,Object> values) {
		logger.info(EELFLoggerDelegate.applicationLogger, "updateTableEventual "+keyspaceName+" tableName "+tableName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				updateTableEventual(musicUrl, keyspaceName, tableName, keyName, keyValue, values);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to update the table. "
									+ "Could not successfully reach any music instance.");
	}

	
	private static void updateTableEventual(String musicUrl, String keyspaceName, String tableName,
									String keyName, String keyValue, Map<String,Object> values) {		
		Map<String,String> consistencyInfo= new HashMap<String, String>();
		consistencyInfo.put("type", "eventual");

		JsonInsert jIns = new JsonInsert();
		jIns.setValues(values);
		jIns.setConsistencyInfo(consistencyInfo);
		
		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName
								+"/tables/"+tableName+"/rows?"+keyName+"="+keyValue);

		ClientResponse response = addMusicHeaders(webResource).put(ClientResponse.class, jIns);

		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"Failed to updateTableEventual : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
		}

		Map<String,Object> output = response.getEntity(Map.class);
		if (!output.containsKey("status") || !output.get("status").equals("SUCCESS")) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to createKeySpaceEventual : Status Code "+ output.toString());
			throw new RuntimeException("Failed: MUSIC Response " + output.toString());
		}
	}

	public static Map<String,Object> readSpecificRow(String keyspaceName, String tableName,
			String keyName, String keyValue) {
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		Map<String, Object> result;
		for (String musicUrl: musicUrls) {
			try {
				result = readSpecificRow(musicUrl, keyspaceName, tableName, keyName, keyValue);
				return result;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to read row. "
							+ "Could not successfully reach any music instance.");
		result = null;
		return result;
	}
	
	private static Map<String,Object> readSpecificRow(String musicUrl, String keyspaceName, String tableName,
												String keyName, String keyValue) {
		logger.info(EELFLoggerDelegate.applicationLogger,
				"readSpecificRow "+keyspaceName+" tableName "+tableName + " key" +keyName + " value" + keyValue);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName
											+"/tables/"+tableName+"/rows?"+keyName+"="+keyValue);

		ClientResponse response = addMusicHeaders(webResource).get(ClientResponse.class);

		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"Failed to insertIntoTableEventual : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
		}

		Map<String,Object> output = response.getEntity(Map.class);

		Map<String, Object> rowMap = (Map<String, Object>) output.getOrDefault("result", null);

		return rowMap;	
	}

	public static Map<String,Object> readAllRows(String keyspaceName, String tableName) {
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		Map<String, Object> result;
		for (String musicUrl: musicUrls) {
			try {
				result = readAllRows(musicUrl, keyspaceName, tableName);
				return result;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to read all rows. "
								+ "Could not successfully reach any music instance.");
		result = null;
		return result;
	}
	
	private static Map<String,Object> readAllRows(String musicUrl, String keyspaceName, String tableName) {
		logger.info(EELFLoggerDelegate.applicationLogger, "readAllRows "+keyspaceName+" tableName "+tableName);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName+"/tables/"+tableName+"/rows");

		ClientResponse response = addMusicHeaders(webResource).get(ClientResponse.class);

		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to readAllRows : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
		}

		Map<String,Object> output = response.getEntity(Map.class);
		return output;	
	}

	public static void dropTable(String keyspaceName, String tableName) {
		logger.info(EELFLoggerDelegate.applicationLogger, "dropTable "+keyspaceName+" tableName "+tableName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				dropTable(musicUrl, keyspaceName, tableName);
				return;
			}  catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to drop table."
							+ " Could not successfully reach any music instance.");
		return;
	}
	
	private static void dropTable(String musicUrl, String keyspaceName, String tableName) {
		Map<String,String> consistencyInfo= new HashMap<String, String>();
		consistencyInfo.put("type", "eventual");

		JsonTable jsonTb = new JsonTable();
		jsonTb.setConsistencyInfo(consistencyInfo);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName+"/tables/"+tableName);
	
		ClientResponse response = addMusicHeaders(webResource).delete(ClientResponse.class, jsonTb);

		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to dropTable : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
		}
	}

	public static void dropKeySpace(String keyspaceName) {
		logger.info(EELFLoggerDelegate.applicationLogger, "dropKeySpace "+keyspaceName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				dropKeySpace(musicUrl, keyspaceName);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to drop keyspace."
							+ " Could not successfully reach any music instance.");
		return;
	}
	
	private static void dropKeySpace(String musicUrl, String keyspaceName) {		
		Map<String,String> consistencyInfo= new HashMap<String, String>();
		consistencyInfo.put("type", "eventual");

		JsonKeySpace jsonKp = new JsonKeySpace();
		jsonKp.setConsistencyInfo(consistencyInfo);

		WebResource webResource = createMusicWebResource(musicUrl+"/keyspaces/"+keyspaceName);

		ClientResponse response = addMusicHeaders(webResource).delete(ClientResponse.class, jsonKp);

		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to dropTable : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : "+ response.getStatus());
		}
	}

	public static String createLockRef(String lockName) {
		logger.info(EELFLoggerDelegate.applicationLogger, "createLockRef "+lockName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		String result;
		for (String musicUrl: musicUrls) {
			try {
				result = createLockRef(musicUrl, lockName);
				return result;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to create lock reference. "
											+ "Could not successfully reach any music instance.");
		result = "";
		return result;
	}
	
	private static String createLockRef(String musicUrl, String lockName) {
		WebResource webResource = createMusicWebResource(musicUrl+"/locks/create/"+lockName);
		
		ClientResponse response = addMusicHeaders(webResource).post(ClientResponse.class);

		if (response.getStatus() != 200 ) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"Failed to createLockRef : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
		}

		Map<String,Object> responseMap = response.getEntity(Map.class);
		if (!responseMap.containsKey("status") || !responseMap.get("status").equals("SUCCESS") ||
				!responseMap.containsKey("lock")) {
			logger.error(EELFLoggerDelegate.errorLogger, 
					"Failed to createLockRef : Status Code "+ responseMap.toString());
			return "";
		}
		String lockRef = ((Map<String, String>) responseMap.get("lock")).get("lock");
		logger.info(EELFLoggerDelegate.applicationLogger, "This site's lockReference is "+lockRef);
		return lockRef;
	}

	/**
	 * Try to acquire the lock lockid.
	 * If cannot reach any music instance, return status:FAILURE
	 * @param lockId
	 * @return
	 */
	public static Map<String,Object> acquireLock(String lockId) {
		logger.info(EELFLoggerDelegate.applicationLogger, "acquireLock "+lockId);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		Map<String, Object> result;

		for (String musicUrl: musicUrls) {
			try {
				result = acquireLock(musicUrl, lockId);
				return result;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to acquireLock. Could not successfully reach any music instance.");
		result = new HashMap<String, Object>();
		result.put("status", "FAILURE");
		return result;
	}
	
	private static Map<String,Object> acquireLock(String musicUrl, String lockId){
		//should be fixed in MUSIC, but putting patch here too
		if (lockId==null) {
			Map<String,Object> fail = new HashMap<String, Object>();
			fail.put("status", "FAILURE");
			return fail;
		}

		WebResource webResource = createMusicWebResource(musicUrl+"/locks/acquire/"+lockId);

		ClientResponse response = addMusicHeaders(webResource).get(ClientResponse.class);

		if (response.getStatus() != 200) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to acquireLock : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
		}

		Map<String,Object> output = response.getEntity(Map.class);

		return output;
	}

	public static String whoIsLockHolder(String lockName) {
		logger.info(EELFLoggerDelegate.applicationLogger, "whoIsLockHolder "+lockName);
		
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		String result;
		for (String musicUrl: musicUrls) {
			try {
				result = whoIsLockHolder(musicUrl, lockName);
				return result;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to check who the lock holder is. "
											+ "Could not successfully reach any music instance.");
		result = null;
		return result;
	}
	
	private static String whoIsLockHolder(String musicUrl, String lockName) {	
		WebResource webResource = createMusicWebResource(musicUrl+"/locks/enquire/"+lockName);

		ClientResponse response = addMusicHeaders(webResource).get(ClientResponse.class);

		if (response.getStatus() != 200) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"Failed to determine whoIsLockHolder : Status Code "+response.getStatus());
			throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
		}

		Map<String,String> lockoutput = (Map<String, String>) response.getEntity(Map.class).get("lock");
		if (lockoutput.get("lock-holder").equals("No lock holder!")) {
			logger.info(EELFLoggerDelegate.applicationLogger, "No lock holder");
			return null;
		}
		return (String) lockoutput.get("lock-holder");
	}

	public static void unlock(String lockId) {
		ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
		for (String musicUrl: musicUrls) {
			try {
				unlock(musicUrl, lockId);
				return;
			} catch (ClientHandlerException che) {
				logger.warn(EELFLoggerDelegate.applicationLogger, "Timed out connection to MUSIC. Consider setting"
						+ " 'music-connection-timeout-ms' in the configuration");
			} catch (RuntimeException e) {
				logger.warn(EELFLoggerDelegate.applicationLogger, e.getMessage());
			}
			logger.warn(EELFLoggerDelegate.applicationLogger, "Could not reach music at '" + musicUrl +"'");
		}
		logger.error(EELFLoggerDelegate.errorLogger, "Unable to unlock the lock. "
								+ "Could not successfully reach any music instance.");
		return;
	}
	
	private static void unlock(String musicUrl, String lockId) {
		logger.info(EELFLoggerDelegate.applicationLogger, "unlock "+lockId);

		WebResource webResource = createMusicWebResource(musicUrl+"/locks/release/"+lockId);

		ClientResponse response = addMusicHeaders(webResource).delete(ClientResponse.class);

		Map<String,Object> responseMap = response.getEntity(Map.class);
		
		if (response.getStatus() < 200 || response.getStatus() > 299) {
			logger.error(EELFLoggerDelegate.errorLogger, "Failed to unlock : Status Code "+response.getStatus());
			if (responseMap.containsKey("error")) {
				logger.error(EELFLoggerDelegate.errorLogger, "Failed to unlock : "+responseMap.get("error"));
			}
			throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
		}
	}


	
	/**
	 * Gets a connection timeout to music. This function will return the 
	 * configured parameter given in the prom json config, if available.
	 * Otherwise, it will calculate a timeout such that it the connection will be able
	 * to cycle through the different music locations prior to other nodes assuming this
	 * replica is dead.
	 * @return
	 */
	private static int getMaxConnectionTimeout() {
		int timeout = PromUtil.getTimeoutToMusicMillis();
		if (timeout<=0) { // user hasn't defined a valid timeout
			ArrayList<String> musicUrls = PromUtil.getMusicNodeURL();
			int promTimeout = PromUtil.getPromTimeout();
			timeout = promTimeout/musicUrls.size();
		}
		return timeout;
	}
	
	
	public static void main(String[] args){
		Map<String,Object> results = MusicHandle.readAllRows("votingappbharath", "replicas");
		for (Map.Entry<String, Object> entry : results.entrySet()){
			Map<String, Object> valueMap = (Map<String, Object>)entry.getValue();
			for (Map.Entry<String, Object> rowentry : valueMap.entrySet()){
				if(rowentry.getKey().equals("timeoflastupdate")){
					System.out.println(rowentry.getValue());
				}
				break;
			}
			break;
		}
	}

}



