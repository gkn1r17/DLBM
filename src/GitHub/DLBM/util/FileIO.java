package util;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
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
import lbm.GridCell;
import lbm.Cluster;
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
	 * @param verbose 
	 * @param nodeNum 
	 * @param settings 
	 * @param tm 
	 * @return
	 */
	public static GridCell[] loadTM(String filename, boolean gcArray, DRand rd, double[][] vols, double[][] temps, boolean verbose) {
		
		//double maxStay = 0;
		
		boolean swapped = false;
        if(filename.contains("6386")) {
        	if(verbose)
        		System.out.println("TM file format swapped (from -> to)");
            swapped = true;
        }
		
		GridCell[] cells = new GridCell[Settings.NUM_BOXES];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));

        	
            while (myFileReader.hasNextLine()){
                String[] tokens = myFileReader.nextLine().trim().split("\\s+");
                
                int from = (int) Double.parseDouble(tokens[1]) - 1;
                int dest = (int) Double.parseDouble(tokens[0]) - 1;
                if(swapped) {
                    from = (int) Double.parseDouble(tokens[0]) - 1;
                    dest = (int) Double.parseDouble(tokens[1]) - 1;
                }
                
                
                if(from < Settings.NUM_BOXES && dest < Settings.NUM_BOXES) {
                    
                	
                	
	                double prob = Double.parseDouble(tokens[2]) * Settings.LOW_DISP;// * (Settings.DISP_HOURS / 90.0); //1.0 / Settings.NUM_BOXES;
	                
	                if(Settings.UNIFORM_PROB > 0)
	                	prob = Settings.UNIFORM_PROB;
	                
	                if(cells[from] == null) {
	                	cells[from] = new GridCell(from, 
	                			vols == null ? 1.0 : vols[from][0],
	                			temps == null ? new double[] {-999.0} : temps[from]);
	                }
	
	                if(prob != 0 && from != dest) {
	            		GridCell destCell = cells[dest];
	            		if(destCell == null) { 
	            			cells[dest] = new GridCell(dest, 
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
	
	private static void allocateClusters(GridCell[] cells, DRand rd) throws FileNotFoundException {
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
	public static double[] loadPopSize(String filename) {
		double[] popSizes = new double[Settings.NUM_BOXES];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));
        	int i =0;
        	while(myFileReader.hasNextLine()) {
        		popSizes[i] = Double.parseDouble(myFileReader.nextLine().trim());
        		i++;
        	}
        	
        	myFileReader.close();
        	
        }
        catch (FileNotFoundException e){
            e.printStackTrace();
            System.exit(-1);
        }
        return(popSizes);
	}
	
	/** Loads the output of the following MatLab code (assuming TM is sparse matrix)
	 * into Java sparse matrix:
	 	[i,j,val] = find(TM);
	    data_dump = [i,j,val];
	    save -ascii TM.txt data_dump;
	 * @param filename
	 * @param cells 
	 * @param myNode 
	 * @param tempLins 
	 * @param settings 
	 * @param tm 
	 * @return
	 * @throws Exception 
	 */
	public static void loadOldRun(String inFile, GridCell[] cells, int myNode) throws Exception {
		String filename = inFile;
		
		for(int year = Settings.START_LOAD_YEAR; year >= Settings.LOAD_YEAR; year = year - 50) {
		
				//String indFile = filename + "-lins.csv";
				
				if(Settings.LOAD_DIST)
					filename = inFile + "_Y" + year + "_N" + myNode + "\\.csv";
				else
					filename = filename + "\\.csv";
				
			    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
				
			    int size = 0;
			    ArrayList<String> files = null;
			    do {
			    	if(myNode == 0)
			    		System.out.println("Trying to load year " + year + "...");
					files = Stream.of(new File(Settings.LOAD_DIR).listFiles())
						      .filter(file -> !file.isDirectory())
						      .map(File::getName)
						      .filter(f -> pattern.matcher(f).find())
						      .collect(Collectors.toCollection(ArrayList::new));
					
					size = files.size();
					if(size > 1)
						throw new Exception("Too many file matches!");
					else if(size == 0 && year == Settings.LOAD_YEAR)
						Thread.sleep(10000);
			    }while(size == 0 && year == Settings.LOAD_YEAR);
			    
			    if(size == 0)
			    	continue;
			    
			    filename = Settings.LOAD_DIR + "/" + files.get(0);
			    loadOldRunYear(filename, year, cells, myNode);
			    break;
        
		}
		//return -1;
	}
	
	public static void loadAndOutputSample(String filename, GridCell[] cells, int myNode, int year, ArrayList<GridCell> cellList) {
	    
		filename = filename + "_Y" + year + "_N" + myNode + "\\.csv";
		Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);

		
		ArrayList<String> files = Stream.of(new File(Settings.LOAD_DIR).listFiles())
			      .filter(file -> !file.isDirectory())
			      .map(File::getName)
			      .filter(f -> pattern.matcher(f).find())
			      .collect(Collectors.toCollection(ArrayList::new));

		if(files.size() == 1) {
			loadOldRunYear(Settings.LOAD_DIR + "/" + files.get(0),  year,  cells, myNode);
			cellList.parallelStream().forEach(cell -> cell.getSampleset(Settings.SMPL_SIZE, year));
			
			int lineLength = (cellList.stream().mapToInt(GridCell::getNumLins).max().getAsInt() * 2) + 1;
			filename = Settings.FILE_OUT + "SETs" + lineLength + "_Y" + year + "_N" + myNode + ".csv";
			printAll(cellList, filename);
			
		}
	}
	
	private static void loadOldRunYear(String filename, int year, GridCell[] cells, int myNode) {
	    System.out.println("loading file \""  + filename + "\"");
		
        try{
  
        	Scanner myFileReader = new Scanner(new File(filename));
        	Settings.LOAD_YEAR = year;
        	
        	int cellI = 0;
            while (myFileReader.hasNextLine()){
            	String[] tokens = myFileReader.nextLine().trim().split(",");
            	
            	GridCell cell = 
            			Settings.LOAD_DIST ?
            					cells[Integer.parseInt(tokens[0])] :
            					cells[cellI];
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

	/** Loads the output of the following MatLab code (assuming TM is sparse matrix)
	 * into Java sparse matrix:
	 	[i,j,val] = find(TM);
	    data_dump = [i,j,val];
	    save -ascii TM.txt data_dump;
	 * @param filename
	 * @param cells 
	 * @param myNode 
	 * @param tempLins 
	 * @param settings 
	 * @param tm 
	 * @return
	 * @throws Exception 
	 */
	public static void loadFinalRun(String inFile, GridCell[] cells, int myNode) throws Exception {
		String filename = Settings.LOAD_DIR + "/" + inFile + "z" + "_Y" + Settings.LOAD_HOUR + "_N" + myNode + ".csv";

			    System.out.println("loading file \""  + filename + "\"");
				
		        try{
		  
		        	Scanner myFileReader = new Scanner(new File(filename));
		        	
		        	int cellI = 0;
		            while (myFileReader.hasNextLine()){
		            	String[] tokens = myFileReader.nextLine().trim().split(",");
		            	
		            	GridCell cell = 
		            			Settings.LOAD_DIST ?
		            					cells[Integer.parseInt(tokens[0])] :
		            					cells[cellI];
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
	
	public static void printAll(ArrayList<GridCell> cellList, String filename) {
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,true);
			
			
			for(GridCell cell : cellList) {
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
	
	public static void printAllFinal(ArrayList<GridCell> cellList, String filename, int hour, int lineLength) {
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,false);
			outputfile.write(hour + "," + lineLength +  "\n");
			
			for(GridCell cell : cellList) {
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
	
	


	public static double[][] loadDoubleFile(String filename, boolean verbose) throws Exception {
		if(filename == null) {
			return null;
		}
		double[][] vols = new double[Settings.NUM_BOXES][];
        Scanner myFileReader = new Scanner(new File(filename));
        int cellI = 0;
        while(myFileReader.hasNext()) {
        	//String[] nextLineBits = .split(",");
        	vols[cellI] = getValFromString(myFileReader.nextLine(), verbose);
		    cellI++;
        }
		if(cellI < Settings.NUM_BOXES)
		    throw new Exception("Number of lines in file " + filename + " < number of boxes");
        myFileReader.close();
        return vols;
	}

	private static double[] getValFromString(String str, boolean verbose) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9|\\.)]", ""); //get rid of special characters
		if(!str.equals(str2) && verbose)
			System.out.println("PLEASE CHECK: replacing " + str + " with " + str2);

		return  Arrays.asList(
				str2.split(",")
				).stream().mapToDouble(e -> Double.parseDouble(e)).toArray();
	}
	
	private static int getIntFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9)]", ""); //get rid of special characters
		if(!str.equals(str2))
			System.out.println("PLEASE CHECK: replacing " + str + " with " + str2);

		return Integer.parseInt(str2);
	}

	public static void saveSample(int year, Collection<GridCell> smpls, int totalLinNum, String filename) {

		
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,false);
			outputfile.write(totalLinNum +  "\n");
			
			for(GridCell cell : smpls) {
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

	public static void saveModSample(int year, HashSet<Integer> modList, Collection<GridCell> cells, int totalLinNum, String filename) {
		StringBuilder outStr = new StringBuilder();
		
		int maxLen = 0;
		for(GridCell cell : cells) {
			
			String newStr = cell.writeModules(modList);
			maxLen = Math.max(maxLen, newStr.split(",").length + 1);
			
			//add total
			outStr.append(
					newStr
				);
		}
		
		filename = filename.replace("linelength", "" + maxLen);
		
		FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,false);
			outputfile.write(totalLinNum +  "\n" + outStr.toString());
			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		
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



}

