/** Reads configuration from ini / command line.
 * Order of precedances (lowest to highest)
 * 
 * - default.ini
 * - additional ini file specified in command line as "SETTINGS [ini file].ini"
 * - additional command line parameters specified "[NAME OF PARAMETER
 */

package config;

import java.io.File;
import java.io.IOException;

import org.ini4j.Ini;
import org.ini4j.Profile;
import org.ini4j.Profile.Section;

public class IniFileReader {
	
	/**String to be saved as new file containing all settings as record in run folder*/
	final StringBuilder settingsIniOut;
	/**Object combing all settings*/
	final Ini settingsIni;

	/**
	 * 
	 * @param args Command line args (may contains more settings and these have precedence over ini file)
	 * @param isController When running distributed if we're on node responsible for printing log stuff
	 * @param verbose
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public IniFileReader(String[] args, boolean isController, boolean verbose) 
														throws NumberFormatException, IOException {
		settingsIni = readSettingsFile(args, isController, verbose);
		settingsIniOut = new StringBuilder();

	}

	/**
	 * 
	 * @param paramName
	 * @param sectName
	 * @param canBeMissing
	 * @return
	 * @throws Exception
	 */
	String getParamValue(String paramName, String sectName, boolean canBeMissing) throws Exception {
		
		//look in ini file first as command line takes precedence
		Section namedSect = settingsIni.get(sectName);
		if(namedSect == null)
			throw new Exception("Section " + sectName + " missing. "
					+ "Is parameter " + paramName + " in the wrong section?");
		String resStr = settingsIni.get(sectName).remove(paramName);
		
		//look in command line
		Section defSect = settingsIni.get("commandLine");
		if(defSect != null && defSect.containsKey(paramName))
			resStr = defSect.remove(paramName);
		
		
		if(!canBeMissing && resStr == null)
			throw new Exception("Parameter: " + paramName + 
					" not found in ini file (expected section: [" + sectName + "]) or command line" );
		
		settingsIniOut.append("\n\n\n[" + sectName + "]\n" + paramName + "=" + resStr);
		
		return resStr;
	}





	Ini readSettingsFile(String[] args, boolean isController, boolean verbose) throws NumberFormatException, IOException {
		
		Ini defIni = null;
		Ini otherIni = null;
		
		try {
			
			System.out.println("Reading default settings file \"default.ini\"");
			defIni = new Ini( new File("default.ini"));
		
		} catch (NumberFormatException | IOException e) {
			System.err.println("Exception reading default Settings file (default.ini)");
			throw e;
		}
			

		//read any additional settings files
		//filename follows "SETTINGS" in command line args
		Section cmdSect = null;
		for(int i =0; i < args.length; i+= 2) {
			
			
			if(args[i].toLowerCase().trim().equals("settings")) { //read settings file
				String settingsFileName = args[i + 1];
				
				if(!settingsFileName.endsWith(".ini"))
					settingsFileName = settingsFileName + ".ini";
				
				if(isController && verbose)
					System.out.println("*** using settings file ***: \"" + settingsFileName + "\"");
				
				
				try {
					
					//read additional settings file
					try {
						otherIni = new Ini(new File(settingsFileName));
					} catch (NumberFormatException | IOException e) {
						System.err.println("Exception reading default Settings file (default.ini)");
						throw e;
					}	
						
					for (String sectionName : otherIni.keySet()) {
					    Profile.Section othSect = otherIni.get(sectionName);
					    Profile.Section defSect = defIni.get(sectionName);
			
					    if (defSect == null) {
					        defIni.add(sectionName).putAll(othSect);
					    } else {
					        for (String key : othSect.keySet())
					        	defSect.put(key, othSect.get(key));
					    }
					}

					
				} catch (Exception e) {
					System.err.println("Exception reading Settings file \"" + settingsFileName + "\"");
					throw e;
				}
				
				
			}
			else {
			    
				//TODO properly integrate command line into sections
				//for now will check section "commandLine" first (as has precedence over settings files)
			    if (cmdSect == null) {
			        cmdSect = defIni.add("commandLine");
					if(isController && verbose)
						System.out.println("*** Reading command line settings *** ");

			    }
		        cmdSect.put(args[i].toUpperCase().trim(), args[i + 1]);

			}
				
			

		
		}
		return defIni;


	}

	public void appendToOutput(String string) {
    	settingsIniOut.append("\n\n\nDET_TRANSPORT=false");
		
	}

	public void checkForUnknownSettings() {
		// TODO Auto-generated method stub
		
	}

	
}
