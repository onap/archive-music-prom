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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import org.onap.music.prom.eelf.logging.EELFLoggerDelegate;
import org.onap.music.prom.main.PromDaemon.ScriptResult;
import org.onap.music.prom.musicinterface.MusicHandle;



public class PromUtil {
	private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(PromDaemon.class);

	public static String version;
	static {
		try {
			final Properties properties = new Properties();
			properties.load(PromUtil.class.getClassLoader().getResourceAsStream("project.properties"));
			version = properties.getProperty("version");
			logger.info(EELFLoggerDelegate.applicationLogger, "Prom version " + version);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}
	
	private static ArrayList<String> getMusicNodeIp(){
		return ConfigReader.getConfigListAttribute("music-location");
/*		String serverAddress;
		serverAddress = agaveMusicNode;
		while(isHostUp(serverAddress) != true)
			serverAddress = toggle(serverAddress);
		return serverAddress;
*/	}
	
/*	public static String toggle(String serverAddress){
		if(serverAddress.equals(agaveMusicNode)){
			System.out.println("Agave is down...connect to Big Site");
			serverAddress = bigSiteMusicNode;
		}else if(serverAddress.equals(bigSiteMusicNode)){
			System.out.println("Big Site is down...connect to Agave");
			serverAddress = agaveMusicNode;
		}
		return serverAddress;
	}*/
	
	public static ArrayList<String> getMusicNodeURL(){
		ArrayList<String> ips = getMusicNodeIp();
		ArrayList<String> urls = new ArrayList<String>();
		for (String ip: ips) {
			urls.add( "http://"+ip+":8080/MUSIC/rest/v" +PromUtil.getMusicVersion());
		}
		return urls;
	}
	
	public static String getMusicVersion() {
		String version = ConfigReader.getConfigAttribute("music-version", "2");
		if (version==null) {
			logger.error(EELFLoggerDelegate.errorLogger,
					"No MUSIC Version provided in your configuration file. Please "
					+ "include 'musicVersion' in your config.json file.");
			throw new Error("Required property 'music-version' is not provided");
		}
		return version;
	}
	
	public static boolean isHostUp(String serverAddress) { 
		Boolean isUp = false;
	    try {
			InetAddress inet = InetAddress.getByName(serverAddress);
			isUp = inet.isReachable(1000);	
		} catch (UnknownHostException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
		}
	    return isUp;
	}
	
	
	
	/* MUSIC authentication functions */
	public static String getAid() {
		return ConfigReader.getConfigAttribute("aid", "");
	}
	
	public static String getAppNamespace() {
		return ConfigReader.getConfigAttribute("namespace", "");
	}
	
	public static String getUserId() {
		return ConfigReader.getConfigAttribute("userid", "");
	}
	
	public static String getPassword() {
		return ConfigReader.getConfigAttribute("password", "");
	}
	/* End of MUSIC authentication functions */
	
	public static int getPromTimeout() {
		return Integer.parseInt(ConfigReader.getConfigAttribute("prom-timeout"));
	}
	
	/**
	 * Gets 'music-connection-timeout-ms' property from configuration file, returning a negative number if 
	 * it doesn't exist
	 * @return
	 */
	public static int getTimeoutToMusicMillis() {
		return Integer.parseInt(ConfigReader.getConfigAttribute("music-connection-timeout-ms", "-1"));
	}
	
	public static ScriptResult executeBashScriptWithParams(ArrayList<String> script){
		logger.info(EELFLoggerDelegate.applicationLogger, "executeBashScript: " + script);
		try {
			ProcessBuilder pb = new ProcessBuilder(script);
			final Process process = pb.start();
			int exitCode = process.waitFor();

			StringBuffer errorOutput = new StringBuffer();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line = "";                       
            while ((line = reader.readLine())!= null) {
            		if(!line.equals(""))
            			errorOutput.append(line + "\n");
            }
            System.out.print(errorOutput);
			if (exitCode == 0)
				return ScriptResult.ALREADY_RUNNING;
			else if (exitCode == 1)
				return ScriptResult.FAIL_RESTART;
			else if (exitCode == 2)
				return ScriptResult.SUCCESS_RESTART;

		} catch (IOException e) {
			logger.error("PromUtil executingBashScript: " + e.getMessage());
		} catch (InterruptedException e) {
			logger.error("PromUtil executingBashScript: " + e.getMessage());
		}
		return ScriptResult.FAIL_RESTART;
	}
	
}
