package util;



import java.io.File;
import java.io.FileNotFoundException;
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
	public static GridBox[] loadTM(String filename, DRand rd, double[][] vols, double[][] temps, boolean verbose) {
		
		//double maxStay = 0;
		
		GridBox[] cells = new GridBox[Settings.NUM_BOXES];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));

        	
            while (myFileReader.hasNextLine()){
                String[] tokens = myFileReader.nextLine().trim().split("\\s+");
                
                int from = (int) Double.parseDouble(tokens[0]) - 1;
                int dest = (int) Double.parseDouble(tokens[1]) - 1;
                
                
                if(from < Settings.NUM_BOXES && dest < Settings.NUM_BOXES) {
                    
                	
                	
	                double prob = Double.parseDouble(tokens[2]) * Settings.DISP_SCALER;// * (Settings.DISP_HOURS / 90.0); //1.0 / Settings.NUM_BOXES;
	                
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
		                cells[from].addDest(prob, cells[dest]);
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
        
            allocateClusters(cells, rd);
        
        
        }catch (FileNotFoundException e){
            e.printStackTrace();
            System.exit(-1);
        }
        //System.out.println((1 - minStay) * 100);
        return cells;
		
	}
	
	
	
	private static void allocateClusters(GridBox[] cells, DRand rd) throws FileNotFoundException {
    	Scanner clustReader = new Scanner(new File(Settings.CLUST_FILE));
		HashMap<Integer, Cluster> clusts = new HashMap<Integer, Cluster>();
    	
		int i =0;
        while (clustReader.hasNextLine()){
        	String[] lineBits = clustReader.nextLine().trim().split(",");
        	
        	
        	
            int clustNum = getIntFromString(  lineBits[0]     );
            clusts.putIfAbsent(clustNum, new Cluster(rd.nextInt(),getIntFromString(  lineBits[1]     )));
            
            clusts.get(clustNum).addGridCell(cells[i]);
            i++;
        }
        
        clustReader.close();

        
	}
	



	
	public static void loadDay(String inFile, GridBox[] cells, int myNode) throws Exception {
		String filename = inFile + "s[0-9]+_D" + Settings.LOAD_DAY + "_N" + myNode + "\\.csv";

			    System.out.println("loading file \""  + filename + "\"");
				
		        try{
		  
		        	
				    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
				    ArrayList<String> files = Stream.of(new File(Settings.LOAD_DIR).listFiles())
							      .filter(file -> !file.isDirectory())
							      .map(File::getName)
							      .filter(f -> pattern.matcher(f).find())
							      .collect(Collectors.toCollection(ArrayList::new));
						
					filename = Settings.LOAD_DIR + "/" + files.get(0);	

		        	Scanner myFileReader = new Scanner(new File(filename));
		        	
		        	int cellI = 0;
		            while (myFileReader.hasNextLine()){
		            	String[] tokens = myFileReader.nextLine().trim().split(",");
		            	
		            	GridBox cell = cells[Integer.parseInt(tokens[0])];
		            	if(cell.getClust().node == myNode) {
		            	
		            		cell.addLoadedPop(tokens); //, linMappings);
		            	}
		                cellI++;
		                
		                if(cellI == Settings.NUM_BOXES)
		                	break;
		            }
		            
		            myFileReader.close();
		        }
		        catch (FileNotFoundException e){
		            e.printStackTrace();
		            System.exit(-1);
		        }
        
	}

	
	public static void printAll(ArrayList<GridBox> cellList, String filename) {
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,true);
			
			
			for(GridBox cell : cellList) {
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
	
	private static int getIntFromString(String str) {
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
			outputfile.write("SEED:" + Arrays.toString(Settings.SEED) + "\n");
			outputfile.write("TRACER_MODE:" + Settings.TRACER_MODE + "\n");
			outputfile.write("P_0:" + Settings.P_0 + "\n");

			
			/** TRANSPORT MATRIX */
			outputfile.write("NUM_BOXES:" + Settings.NUM_BOXES + "\n");		
			outputfile.write("TM_FILE:" + Settings.TM_FILE + "\n");		


			/** GROWTH */			
			outputfile.write("MORTALITY_DAY:" + Settings.MORTALITY_DAY + "\n");
			outputfile.write("GROWTH_RATE_DAY:" + Settings.GROWTH_RATE_DAY + "\n");
			outputfile.write("GROWTH_HOURS:" + Settings.GROWTH_HOURS + "\n");		
			
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





}

