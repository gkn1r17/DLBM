/**Stores record of descent for each Lineage
 * either produced as a mutant or present at time=0
 * for a given grid box
 * 
 * Can be regularly serialized to file for saved record.
 * 
 */

package transportMatrix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
import config.SciConfig;
import control.Runner;
import lineages.Lineage;
import lineages.NeutralLineage;
import lineages.SelLineage;

public class Phylogeny implements Serializable{
	private static final long serialVersionUID = -2776211665306472795L;

	
	private final ArrayList<PhyloEntry> parentNumChildren = new ArrayList<PhyloEntry>();
	private final int gridBoxID;
	private long curID;

	
	/**Record of parent ID of each new mutant lineage
	 * (Lineages present at start are represented with parent = -1)
	 * To save memory parent ID and number of children lineages 
	 * are stored, not each child separate
	 * 
	 */
	private class PhyloEntry implements Serializable, Comparable<PhyloEntry>{
		private static final long serialVersionUID = -6648630815909640434L;
		private final long parentID;
		private final int numChildren;
		//private final long startHour;

		
		public PhyloEntry(long parentID, int numChildren) { //, long startHour) {
			this.parentID = parentID;
			this.numChildren = numChildren;
			//this.startHour = startHour;
		}

		@Override
		public int compareTo(PhyloEntry oth) {
			if(this == oth)
				return 0;
			
			return this.parentID == oth.parentID 
						? 0  
						: (
								this.parentID > oth.parentID 
									?  1 
									: -1
								);
		}
		
	}
	
	
	public Phylogeny(int gridBoxID, long curID) {
		this.gridBoxID = gridBoxID;
		this.curID = curID;
	}

	
	/**At start of simulation add initial populations as "mutants"
	 * with parent = -1
	 * 
	 * @param numLins size of starting population 
	 */
	public void addInitialPopulation(int numLins) {
		curID += numLins - 1;
		if(Runner.settings.mutantTimestepsArr != null)
			parentNumChildren.add(new PhyloEntry(-1, numLins)); //, 0 ));
	}
	
	/**Combine two phylogenies
	 * 
	 * @param phyl
	 */
	public void merge(Phylogeny phyl) {
		parentNumChildren.addAll(phyl.parentNumChildren);
		curID = Math.max(curID, phyl.curID);
	}

	/**Clear phylogeny
	 * 
	 */
	public void clear() {
		parentNumChildren.clear();
		
	}



	public LongStream streamAllChildren() {
		
		ArrayList<Long> childIDs = new ArrayList<Long>();
		long id = curID;
		for(PhyloEntry parNumCh : parentNumChildren.reversed()) {
			for(int i = 0; i < parNumCh.numChildren; i++) { 
				childIDs.add(id);
				if(id % Runner.settings.mutantOffset == 0)
					id = id - Runner.settings.maxMutantOffset + Runner.settings.mutantOffset;				
				id--;
				
			}

		}
		
		return childIDs.stream().mapToLong(i -> i);
	}
	
	
	
	/////////////////// SAVE /LOAD ////////////////////////////
	
	
	public static void saveMutants(String inFile, String timeStr, List<GridBox> activeBoxes) {
		
		long saveStart =  System.currentTimeMillis();
		
		//ensure add in special phylogeny folder as tend to produce lots of files
		String fileSep = "/";
		//fileSep = File.separator;
		
		if(inFile.contains(fileSep))
			inFile = inFile.replace(fileSep, fileSep + "phylogeny" + fileSep);
		else
			inFile = "phylogeny" + fileSep + inFile;
		
		String[] phyloPathBits = inFile.split("/");
		String phyloPath = inFile.replace("/" + phyloPathBits[phyloPathBits.length - 1], "");
		if(!new File(phyloPath).exists())
			new File(phyloPath).mkdir();
		
		String filename = inFile + "_Phy_D" + timeStr + "_N" + Runner.runParallel.getRank() + ".ser";
        try {
            FileOutputStream fileOut = new FileOutputStream(filename);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            
            HashMap<Integer, Phylogeny> combinedMutants = new HashMap<Integer, Phylogeny>();
            long phyloSize = 0;
            for(GridBox box : activeBoxes) {
            	combinedMutants.put(box.id, box.getPhylogeny());
            	phyloSize += box.getPhyloSize();
            }
            out.writeObject(combinedMutants);
            
            out.close();
            fileOut.close();
            for(GridBox box : activeBoxes)
            	box.clearPhylogeny();
            System.out.println("saved mutants in " + ((System.currentTimeMillis() - saveStart ) / 1000.0) + " seconds" );
            System.out.println("phylo size = " + phyloSize );

            
            
        } catch (IOException i) {
            i.printStackTrace();
        }

	
	}
	
	/**JUST FOR TESTING FOR NOW
	 * 
	 * @param inDir
	 * @param inFile
	 * @param boxes
	 * @param loadAll
	 * @param loadHour
	 * @return
	 * @throws Exception
	 */
	public static long[] loadMutants(String inDir, String inFile, ArrayList<GridBox> boxes, boolean loadAll, long loadHour) throws Exception {
		int day = (int) Math.floor(loadHour / 24.0);
		int hourOfDay = (int) ((loadHour /24.0) - day);
		String hourDayStr = day + "hr" + hourOfDay;
		String fileName = inFile.replace(inDir + "/", "");
		String maxFile = inDir + "/phylogeny/" + fileName + "_Phy_D" + hourDayStr + "_N" + Runner.runParallel.getRank() + ".ser";
		
		if(loadAll) 
			loadAllMutants(inDir + "/phylogeny/" + fileName, boxes, loadHour);

		

		
		if(!new File(maxFile).exists()    ) {
			throw new Exception("No phylogeny files found");
		}
		
		
		long[] maxInts = new long[Runner.settings.numBoxes];
        FileInputStream fileIn = new FileInputStream(maxFile);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        HashMap<Integer, Phylogeny> combinedMutants = (HashMap<Integer, Phylogeny>) in.readObject();
        
        in.close();
        fileIn.close();
        
        //get ID for creating next mutant in each box
        for(Entry<Integer, Phylogeny> mutEntry : combinedMutants.entrySet()) {
        	Phylogeny mut = mutEntry.getValue();
        	maxInts[mut.gridBoxID] = mut.curID;
        }

		return maxInts;

	}

	private static boolean loadAllMutants(String fileName, ArrayList<GridBox> boxes, long loadHour) throws IOException, ClassNotFoundException {
		
		String inFile = new File(fileName).getName();
		String suffix = "_N" + Runner.runParallel.getRank() + ".ser";
		String dirName = fileName.replace(inFile, "");
		
		
		String filePattern = inFile + "_Phy_D[0-9]+(hr[0-9]+)?" + suffix;
		Pattern pattern = Pattern.compile(filePattern, Pattern.CASE_INSENSITIVE);
		
		ArrayList<String> files = Stream.of(new File(dirName).listFiles())
					      .filter(file -> !file.isDirectory())
					      .map(File::getName)
					      .filter(f -> pattern.matcher(f).find())
					      .collect(Collectors.toCollection(ArrayList::new));
		
		
		
		if(files.size() == 0) {
			System.err.println("WARNING: no phylogeny to load found");
			return false;
		}
		
		
		for(String file : files) {
			String time = file.replace(inFile + "_Phy_D", "").replace(suffix, "");
			String[] hours = time.split("hr");
			long hour = Long.parseLong(hours[0]) * 24 + 
					(hours.length == 1 ?
					0 :
					Integer.parseInt(hours[1]));
			
			//load whole file if want a complete phylogeny (currently just used for debugging)
			if(hour <= loadHour) {
	            FileInputStream fileIn = new FileInputStream(dirName + "/" + file);
	            ObjectInputStream in = new ObjectInputStream(fileIn);
	            HashMap<Integer, Phylogeny> combinedMutants = (HashMap<Integer, Phylogeny>) in.readObject();
	            
	            in.close();
	            fileIn.close();
	            
	            for(Entry<Integer, Phylogeny> mutEntry : combinedMutants.entrySet()) {
	            	boxes.get(mutEntry.getKey()).mergePhylogeny(mutEntry.getValue());
	            	
	            }
	            System.out.println("loaded");
			}
		}
		
		return true;
	}

	public void addMutants(long id, int numMuts, long hour) {
		parentNumChildren.add(new PhyloEntry(id, numMuts));//, hour));		
	}

	/**Increments and returns curID and handles increasing curID to ensure no duplicate IDs
	 * 
	 * @return curID
	 */
	public long getNextMutantCounter() {
		//adjust counter for next mutant
		curID++;
		
		
		//if curID becomes too high skip all indices that could've been created in another box
		if(curID % Runner.settings.mutantOffset == 0) {
			curID = curID + Runner.settings.maxMutantOffset - Runner.settings.mutantOffset;
			if(gridBoxID == 0) //for now only print for grid box 0 else too many messages
				System.out.println("Re-offsetting lineage ID's for gridBox= " + gridBoxID + "(new ID= " + curID + ")" );
		}
		
		return curID;
		
	}

	public long getSize() {
		// TODO Auto-generated method stub
		return parentNumChildren.size();
	}

	/**
	 * 
	 * @return curID
	 */
	long getCurID() {
		return curID;
	}


	void setCurID(long curID) {
		this.curID = curID;
		
	}


	/**
	 * 
	 * @param lin
	 * @return
	 */
	public long findLin(long lin) {
		if(lin > curID)
			return -1; //can't find. this phylogeny too early (ID too high)
		
		long id = curID;
		
		
		//iterate in reverse order (from most recent to oldest)
		for(int p =0; p < parentNumChildren.size(); p++  ) {
			
			PhyloEntry phylo = parentNumChildren.get(parentNumChildren.size() - p - 1);
			for(int i =0 ; i < phylo.numChildren; i++) {
				if(lin == id)
					return phylo.parentID;
				if(id % Runner.settings.mutantOffset == 0)
					id = id - Runner.settings.maxMutantOffset + Runner.settings.mutantOffset;				
				id--;
			}
		}	
		return -2; //can't find. this phylogeny too late (ID too low)
	}



}
