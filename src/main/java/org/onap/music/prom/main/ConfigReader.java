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


import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ConfigReader {
	private static String configLocation = "config.json";
	
	public static void setConfigLocation(String pathToFile){
		configLocation = pathToFile+"/config.json";
	}
	
	private static JSONObject getJsonHandle(){
		JSONParser parser = new JSONParser();
		Object obj =null;
		try {
			obj = parser.parse(new FileReader(configLocation));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		JSONObject jsonObject = (JSONObject) obj;	
		return jsonObject;
	}

	public static ArrayList<String> getConfigListAttribute(String key){
		ArrayList<String> value = (ArrayList<String>) getJsonHandle().get(key);
		return value; 
	}

	public static String getConfigAttribute(String key){
		Object value = getJsonHandle().get(key);
		return (value!=null) ? String.valueOf(value) : null;
	}
	
	public static String getConfigAttribute(String key, String defaultValue){
		String toReturn = getConfigAttribute(key);
		return (toReturn!=null) ? toReturn : defaultValue;
	}

	public static ArrayList<String> getExeCommandWithParams(String key){
		String script = (String)getJsonHandle().getOrDefault(key, "");
		String[] scriptParts = script.split(" ");
		ArrayList<String> scriptWithPrams = new ArrayList<String>();
		for(int i=0; i < scriptParts.length;i++)
			scriptWithPrams.add(scriptParts[i]);
		return scriptWithPrams;
	}

}