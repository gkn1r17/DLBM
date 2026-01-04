package util;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;
import lbm.Settings;
import parallelization.Cluster;
import lbm.GridBox;
import lbm.Runner;


public class FileIO {


	
	/** Loads the output of the following MatLab code (assuming TM is sparse matrix)
	 * into Java sparse matrix:
	 	[i,j,val] = find(TM);
	    data_dump = [i,j,val];
	    save -ascii TM.txt data_dump;
	 * @param filename
	 * @param rd 
	 * @param vols 
	 * @param temps 
	 * @param smpls 
	 * @param ds 
	 * @param verbose 
	 * @param nodeNum 
	 * @param settings 
	 * @param tm 
	 * @return
	 * @throws FileNotFoundException 
	 */
	public static GridBox[] loadTM(String filename, double[][] vols, double[][] temps, boolean verbose, Settings settings) throws FileNotFoundException {
		
		//double maxStay = 0;
		
		GridBox[] cells = new GridBox[settings.NUM_BOXES];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));

        	
            while (myFileReader.hasNextLine()){
                String[] tokens = myFileReader.nextLine().trim().split("\\s+");
                
                int from = (int) Double.parseDouble(tokens[0]) - 1;
                int dest = (int) Double.parseDouble(tokens[1]) - 1;
                
                
                if(from < settings.NUM_BOXES && dest < settings.NUM_BOXES) {
                    
                	
                	
	                double prob = Double.parseDouble(tokens[2]) * settings.DISP_SCALER; // * (settings.DISP_HOURS / 24.0);
	                
	                if(cells[from] == null) {
	                	cells[from] = new GridBox(from, 
	                			vols == null ? 1.0 : vols[from][0],
	                			temps == null ? new double[] {-999.0} : temps[from],
	                					settings);
	                }
	
	                if(prob != 0 && from != dest) {
	            		GridBox destCell = cells[dest];
	            		if(destCell == null) { 
	            			cells[dest] = new GridBox(dest, 
	            					vols == null ? 1 : vols[dest][0], 
	            					temps == null ? new double[] {-999.0} : temps[dest],
	            							settings);
	            			destCell = cells[dest];
	            		}
		                cells[from].addDest(prob, cells[dest], null);
	                }
                }



            }
            
            myFileReader.close();
            
            for(int i =0 ; i < settings.NUM_BOXES; i++) {
            	try {
					cells[i].sortMovers(cells);
				} catch (Exception e) {
					e.printStackTrace();
				}
            }
        
        
        
        }catch (FileNotFoundException e){
            throw e;
        }
        //System.out.println((1 - minStay) * 100);
        return cells;
		
	}
	
	
	



	
	public static ArrayList<GridBox> loadDay(String inFile, GridBox[] cells, Settings settings) throws Exception {
		String filename = new File(inFile).getName();
		String loadDir = inFile.replaceAll(filename, "");
		long day = (long) Math.floor(settings.LOAD_HOUR / 24);
		long hourOfDay = settings.LOAD_HOUR - (day * 24);
		ArrayList<GridBox> activeCells = new ArrayList<GridBox>();
		filename = filename + "s[0-9]+_D" + day + "(hr" + hourOfDay + ")?"+  "_N[0-9]+\\.csv";

			    System.out.println("loading file \""  + filename + "\"");
				
		        try{
		  
		        	
				    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
				    ArrayList<String> files = Stream.of(new File(loadDir).listFiles())
							      .filter(file -> !file.isDirectory())
							      .map(File::getName)
							      .filter(f -> pattern.matcher(f).find())
							      .collect(Collectors.toCollection(ArrayList::new));
					
				    
				    HashSet<Integer> nodesHandled = new HashSet<Integer>();
				    
				    
				    for(String f: files) {
				    	int nodeNum = Integer.parseInt(f.split("_N")[1].split("\\.csv")[0]);
				    	if(nodesHandled.contains(nodeNum))
				    		throw new IOException("More than one matching file found filename = \"" + filename + "\"");
				    	System.out.println("Loading File: \"" + filename + "\"" );
				    	
				    	nodesHandled.add(nodeNum);
				    
						filename = loadDir + "/" + f;	
	
			        	Scanner myFileReader = new Scanner(new File(filename));
			        	
			        	int cellI = 0;
			            while (myFileReader.hasNextLine()){
			            	String[] tokens = myFileReader.nextLine().trim().split(",");
			            	
			            	GridBox cell = cells[Integer.parseInt(tokens[0])];
			            	if(cell == null)
			            		continue;
			            		
			            	cell.addLoadedPop(tokens);
			                cellI++;
			                activeCells.add(cell);
			                
			                if(cellI == settings.NUM_BOXES)
			                	break;
			            }
			            myFileReader.close();
				    }
		        }
		        catch (FileNotFoundException e){
		        	System.err.println("Fatal Exception: LOAD_FILE \"" + filename + "\" not found.");
		        	throw e;
		        }
		        return activeCells;
        
	}

	
	public static void printAll(List<GridBox> activeCells, String filename) {
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,true);
			
			
			for(GridBox cell : activeCells) {
				//add total
				outputfile.write(
						cell.id + "," + cell.writeTree() + "\n"
					);
			}
			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	
	


	public static double[][] loadDoubleFile(String filename, Settings settings) throws Exception {
		if(filename == null) {
			return null;
		}
		double[][] vols = new double[settings.NUM_BOXES][];
        Scanner myFileReader = new Scanner(new File(filename));
        int cellI = 0;
        while(myFileReader.hasNext()) {
        	//String[] nextLineBits = .split(",");
        	vols[cellI] = getDoubleArrayFromString(myFileReader.nextLine());
		    cellI++;
        }
        myFileReader.close();
		if(cellI < settings.NUM_BOXES)
		    throw new Exception("Number of lines in file " + filename + " < number of boxes");
        return vols;
	}

	private static double[] getDoubleArrayFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9|\\.)]", ""); //get rid of special characters
		
		//warn in output if special characters replaced
		if(!str.equals(str2))
			System.err.println("WARNING: possible character error in input file. Replacing " + str + " with " + str2);

		return  Arrays.asList(str2.split(",")).stream().mapToDouble(e -> Double.parseDouble(e)).toArray();
	}
	
	public static long getLongFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9)]", ""); //get rid of special characters
		
		//warn in output if special characters replaced
		if(!str.equals(str2))
			System.err.println("WARNING: possible character encoding error in input file. "
					+ "Check that " + str + " = " + str2);

		return Long.parseLong(str2);
	}

	public static HashSet<Long> loadLongSet(String filename) {
		if(filename == null) {
			return null;
		}
		
		HashSet<Long> longSet = new HashSet<Long>();

		
		Scanner myFileReader;
		try {
			myFileReader = new Scanner(new File(filename));
	        while(myFileReader.hasNext())
	        	longSet.add (getLongFromString(myFileReader.nextLine()));
	        myFileReader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        return longSet;	
     }



	public static int loadSeed(Settings settings) {
		long day = (long) Math.floor(settings.LOAD_HOUR / 24);
		long hourOfDay = settings.LOAD_HOUR - (day * 24);
		
		String hourDayString = "" + day + "hr" + hourOfDay; 
	    Scanner seedFile;
		try {
			seedFile = new Scanner(new File(settings.FILE_OUT + "/seeds/settings.FILE_OUT_seed_D" + hourDayString));
			int seed = Integer.parseInt(seedFile.nextLine());
			seedFile.close();
			return seed;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return -1;
	}


	/**record settings in file 
	 * @throws IOException */
	public static void makeSettingsFile(String filename, String settingsString) throws IOException {
		
		//create directory
		new File(filename).mkdir();
	
		File outFile = new File(filename +  "_Settings.ini");
		
		//save
		try {
			FileWriter outFileWriter = new FileWriter(outFile,false);
			outFileWriter.write(settingsString);
			outFileWriter.close();
		} catch (IOException e) {
			throw e;
		}				
	}





}

