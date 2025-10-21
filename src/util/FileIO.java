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
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	 */
	public static GridBox[] loadTM(String filename, double[][] vols, double[][] temps, boolean verbose) {
		
		//double maxStay = 0;
		
		GridBox[] cells = new GridBox[Settings.NUM_BOXES];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));

        	
            while (myFileReader.hasNextLine()){
                String[] tokens = myFileReader.nextLine().trim().split("\\s+");
                
                int from = (int) Double.parseDouble(tokens[0]) - 1;
                int dest = (int) Double.parseDouble(tokens[1]) - 1;
                
                
                if(from < Settings.NUM_BOXES && dest < Settings.NUM_BOXES) {
                    
                	
                	
	                double prob = Double.parseDouble(tokens[2]) * Settings.DISP_SCALER; // * (Settings.DISP_HOURS / 24.0);
	                
	                if(cells[from] == null) {
	                	cells[from] = new GridBox(from, 
	                			vols == null ? 1.0 : vols[from][0],
	                			temps == null ? new double[] {-999.0} : temps[from]);
	                }
	
	                if(prob != 0 && from != dest) {
	            		GridBox destCell = cells[dest];
	            		if(destCell == null) { 
	            			cells[dest] = new GridBox(dest, 
	            					vols == null ? 1 : vols[dest][0], 
	            					temps == null ? new double[] {-999.0} : temps[dest]);
	            			destCell = cells[dest];
	            		}
		                cells[from].addDest(prob, cells[dest], null);
	                }
                }



            }
            
            myFileReader.close();
            
            for(int i =0 ; i < Settings.NUM_BOXES; i++) {
            	try {
					cells[i].sortMovers(cells);
				} catch (Exception e) {
					e.printStackTrace();
				}
            }
        
        
        
        }catch (FileNotFoundException e){
            e.printStackTrace();
            System.exit(-1);
        }
        //System.out.println((1 - minStay) * 100);
        return cells;
		
	}
	
	
	



	
	public static void loadDay(String inFile, GridBox[] cells) throws Exception {
		String filename = inFile + "s[0-9]+_D" + Settings.LOAD_DAY + "(hr" + (Settings.LOAD_HOUR - (Settings.LOAD_DAY * 24) ) + ")?"+  "_N[0-9]+\\.csv";

			    System.out.println("loading file \""  + filename + "\"");
				
		        try{
		  
		        	
				    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
				    ArrayList<String> files = Stream.of(new File(Settings.LOAD_DIR).listFiles())
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
				    
						filename = Settings.LOAD_DIR + "/" + f;	
	
			        	Scanner myFileReader = new Scanner(new File(filename));
			        	
			        	int cellI = 0;
			            while (myFileReader.hasNextLine()){
			            	String[] tokens = myFileReader.nextLine().trim().split(",");
			            	
			            	GridBox cell = cells[Integer.parseInt(tokens[0])];
			            	if(cell == null)
			            		continue;
			            		
			            	cell.addLoadedPop(tokens);
			                cellI++;
			                
			                if(cellI == Settings.NUM_BOXES)
			                	break;
			            }
			            myFileReader.close();
				    }
		        }
		        catch (FileNotFoundException e){
		            e.printStackTrace();
		            System.exit(-1);
		        }
        
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

	
	


	public static double[][] loadDoubleFile(String filename) throws Exception {
		if(filename == null) {
			return null;
		}
		double[][] vols = new double[Settings.NUM_BOXES][];
        Scanner myFileReader = new Scanner(new File(filename));
        int cellI = 0;
        while(myFileReader.hasNext()) {
        	//String[] nextLineBits = .split(",");
        	vols[cellI] = getDoubleArrayFromString(myFileReader.nextLine());
		    cellI++;
        }
		if(cellI < Settings.NUM_BOXES)
		    throw new Exception("Number of lines in file " + filename + " < number of boxes");
        myFileReader.close();
        return vols;
	}

	private static double[] getDoubleArrayFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9|\\.)]", ""); //get rid of special characters
		
		//warn in output if special characters replaced
		if(!str.equals(str2))
			System.out.println("PLEASE CHECK: replacing " + str + " with " + str2);

		return  Arrays.asList(str2.split(",")).stream().mapToDouble(e -> Double.parseDouble(e)).toArray();
	}
	
	public static int getIntFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9)]", ""); //get rid of special characters
		
		//warn in output if special characters replaced
		if(!str.equals(str2))
			System.out.println("PLEASE CHECK: replacing " + str + " with " + str2);

		return Integer.parseInt(str2);
	}

	public static HashSet<Integer> loadIntSet(String filename) {
		if(filename == null) {
			return null;
		}
		
		HashSet<Integer> intSet = new HashSet<Integer>();

		
		Scanner myFileReader;
		try {
			myFileReader = new Scanner(new File(filename));
	        while(myFileReader.hasNext())
	        	intSet.add (getIntFromString(myFileReader.nextLine()));
	        myFileReader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        return intSet;	
     }

	
	/**record settings in file */
	public static void makeSettingsFile(String filename) {
		
		//create directory
		try {
			Files.createDirectories(Paths.get(Settings.DIR_OUT));
		} catch (IOException e) {
			e.printStackTrace();
		}		

		
		//save
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,false);
			
			outputfile.write("DURATION:" + Settings.DURATION + "\n");
			outputfile.write("INIT_LIN_SIZE:" + Settings.INIT_LIN_SIZE + "\n");
			outputfile.write("SEED:" + Settings.SEED + "\n");
			outputfile.write("TRACER_MODE:" + Settings.TRACER_MODE + "\n");
			outputfile.write("P_0:" + Settings.INITIAL_P + "\n");

			
			/** TRANSPORT MATRIX */
			outputfile.write("NUM_BOXES:" + Settings.NUM_BOXES + "\n");		
			outputfile.write("TM_FILE:" + Settings.TM_FILE + "\n");	
			outputfile.write("BUILD_TM:" + Settings.BUILD_TM + "\n");	
			if(Settings.BUILD_TM) {
				outputfile.write("BUILD_TM_NUM_ROWS:" + MakeArtificialTM.NUM_ROWS + "\n");	
				outputfile.write("BUILD_TM_NUM_COLS:" + MakeArtificialTM.NUM_COLS + "\n");
				outputfile.write("BUILD_TM_DISP:" + MakeArtificialTM.DISP + "\n");
				outputfile.write("BUILD_TM_DISP_OUT:" + MakeArtificialTM.DISP_OUT + "\n");
				outputfile.write("BUILD_TM_DISP_IN:" + MakeArtificialTM.DISP_IN + "\n");
				outputfile.write("BUILD_TM_SPLIT_COL:" + MakeArtificialTM.SPLIT_COL + "\n");

			}

			
			
			
			
			


			/** GROWTH */			
			outputfile.write("MORTALITY_DAY:" + Settings.MORTALITY_DAY + "\n");
			outputfile.write("GROWTH_RATE_DAY:" + Settings.GROWTH_RATE_DAY + "\n");
			outputfile.write("GROWTH_HOURS:" + Settings.GROWTH_HOURS + "\n");
			outputfile.write("TOP_DOWN:" + Settings.TOP_DOWN + "\n");

			
			/** DISPERSAL */
			outputfile.write("DISP_HOURS:" + Settings.DISP_HOURS + "\n");
			outputfile.write("K:" + Settings.K + "\n");
			outputfile.write("VOLS_FILE:" + Settings.VOL_FILE + "\n");
			outputfile.write("DISP_SCALER:" + Settings.DISP_SCALER + "\n");		
		
					
			/** SELECTION */
			if(Settings.TEMP_FILE != null) {
				outputfile.write("TEMP_FILE:" + Settings.TEMP_FILE + "\n");
				outputfile.write("TEMP_START_RANGE:" + Settings.TEMP_FILE + "\n");
				outputfile.write("W:" + Settings.W + "\n");
			}
			else {
				outputfile.write("TEMP_FILE:0\n");
				outputfile.write("TEMP_START_RANGE:0\n");
				outputfile.write("W:Inf\n");

			}
			
			/** DORMANCY */	
			outputfile.write("SIZE_REFUGE:" + Settings.SIZE_REFUGE + "\n");		

			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
	}



	public static int loadSeed() {
		String hourDayString = "" + Settings.LOAD_DAY + "hr0"; 
	    Scanner seedFile;
		try {
			seedFile = new Scanner(new File(Settings.DIR_OUT + "/seeds/Settings.FILE_OUT_seed_D" + hourDayString));
			int seed = Integer.parseInt(seedFile.nextLine());
			seedFile.close();
			return seed;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return -1;
	}







}

