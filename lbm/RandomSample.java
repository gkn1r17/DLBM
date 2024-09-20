package lbm;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
import lbm.GridCell.DistributedMovement;
import lbm.GridCell.ExternalMovement;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import util.FileIO;

public class RandomSample {
//
//	private static List<RandomSample> runners;
//	private int nodeNum = 0;
//	private static int numNodes = 7;
//
//	
//	public RandomSample(int i) {
//		nodeNum = i;
//	}
//
//	public static void main(String[] args) {
//		
//		if(args.length > 0 && args[0].toLowerCase().equals("numnodes")) {
//			numNodes = Integer.parseInt(args[1]);
//			args = Arrays.asList(args).subList(2, args.length).toArray(new String[args.length - 2]);
//		}
//		try {
//			
//			args = MPI.Init(args);
//
//			runners = Arrays.asList (new RandomSample[] {new RandomSample(MPI.COMM_WORLD.Rank())});
//			runAll(
//					args
//					);
//
//		}catch(NoClassDefFoundError ncdf) {
//			System.out.println("WARNING: Couldn't setup distributed FastMPJ network - are you testing locally?");
//			runners = new ArrayList<RandomSample>();
//			for(int i =0; i < numNodes; i++) {
//				runners.add(new RandomSample(i));
//			}
//			runAll(
//					args
//					);
//
//		}
//	}
//
//
//	private static void runAll(String[] args) {
//		
//		try {
//			
//			Settings.loadSettings(args, runners.size() == 1 ? runners.get(0).nodeNum : 0, false);
//			
//			runners.parallelStream().forEach(r -> {
//				try {
//					r.setup();
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			});;
//	}catch(Exception e){
//		e.printStackTrace();
//		MPI.COMM_WORLD.Abort(-1);
//		System.exit(-1);
//
//	}
//	MPI.Finalize();
//	}
//	
//
//
//	private void saveSmpl(int year) throws Exception {
//		int seed = Settings.SEED[0] == -1 ?
//				new Random().nextInt() :
//					Settings.SEED[nodeNum];
//		
//		TreeMap<Integer, Float> tempLins = new TreeMap<Integer,Float>();
//
//		DRand rd = new DRand(seed);
//		
//		GridCell[] allCells;
//			allCells = FileIO.loadTM(Settings.SMPL_FILE, Settings.USE_ARRAY, rd, 
//					
//					FileIO.loadDoubleFile(Settings.VOLS_FILE, false),
//					FileIO.loadDoubleFile(Settings.TEMP_FILE, false), false);
//
//		
//		ArrayList<GridCell> cellList = new ArrayList<GridCell>(Arrays.asList(allCells));
//		
//		GridCell.maxVol = cellList.stream().mapToDouble(c -> c.getVol()).max().getAsDouble();
//
//		
//		int beginID = 0;
//		for(GridCell cell : cellList)
//			beginID = cell.initIDSizeTemp(beginID, tempLins);
//
//		
//		ArrayList<Cluster> clustList = cellList.stream()
//				.map(cell -> cell.getClust())
//				.distinct()
//				.filter(clust -> clust.node == nodeNum)
//				.collect(Collectors.toCollection(ArrayList::new));
//		
//		
//		
//		cellList = clustList.stream().flatMap(clust -> clust.cells.stream()).collect(Collectors.toCollection(ArrayList::new));
//		
//		Collections.sort(cellList);
//		
//		GridCell[] cells = new GridCell[Settings.NUM_BOXES];
//		for(GridCell cell : cellList) {
//			cells[cell.id] = cell;
//		}
//		
//		
//		FileIO.loadAndOutputSample(Settings.LOAD_FILE, cells, nodeNum, year, tempLins, cellList);
//		
//		
//		
//	}
//
//	private void setup() throws Exception {
//		
//		IntStream years = IntStream.range(1, 100);
//
//		years.parallel().forEach(e -> {
//			try {
//				this.saveSmpl(e * 100);
//			} catch (Exception e1) {
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
//		});
//		
//		
//		
//		
//	}
//
//

}
