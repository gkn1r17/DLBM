package util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import cern.jet.random.engine.DRand;
import lbm.GridBox;
import lbm.Settings;
import parallelization.Cluster;

public class MakeArtificialTM {
//	public static int NUM_CORES = 40;
//	public static int NUM_ROWS = 1;
//	public static int NUM_COLS = 100;
//	public static double DISP = 0.0001;
//	public static boolean GYRE = false;
//	public static double DISP_OUT = 0.0001;
//	public static double DISP_IN = 0;
//	
//	public static double SPLIT_COL = 5;

	
	public static GridBox[] makeUniformTM(Settings settings) {
		
		//adjust for time intervals per generation
		DISP = DISP / (24/ settings.DISP_HOURS) ;
		DISP_OUT = DISP_OUT / (24/ settings.DISP_HOURS) ;

		
		StringBuilder tmWriter = new StringBuilder("From,To,Weight");
		
		GridBox[] cells = new GridBox[settings.NUM_BOXES];
		
		int splitRow = (int) ((SPLIT_COL % 1.0) * NUM_ROWS);
		if(splitRow == 0)
			splitRow = NUM_ROWS;
		
		//columns then rows
		for(int i =0; i < settings.NUM_BOXES; i++) {
			int from = i;
			int[] tos = GYRE ?
					new int[] {i + 1} :
					new int[] {i + 1, i + NUM_COLS, i - 1, i - NUM_COLS};
			
			
			
			if(cells[from] == null)
                cells[from] = new GridBox(from, 1.0, new double[] {-999.0}, settings );
			for(int to : tos) {
				
				if(GYRE) {
					if(to == settings.NUM_BOXES)
						to = 0;
				}
				if(to < settings.NUM_BOXES && to >= 0) {
					if(cells[to] == null)
						cells[to] = new GridBox(to, 1.0, new double[] {-999.0}, settings );
					if(GYRE &&  (to == 0 || to == settings.NUM_BOXES / 2) && DISP_OUT != DISP) {
						if(DISP_OUT > 0)
							cells[from].addDest(DISP_OUT, cells[to], tmWriter);
						if(to == settings.NUM_BOXES / 2)
							cells[from].addDest(DISP - DISP_OUT, cells[0], tmWriter);
						else
							cells[from].addDest(DISP - DISP_OUT, cells[settings.NUM_BOXES / 2], tmWriter);
						
					}
					else{
						
						
						if(sameClust(from,to,splitRow))
							cells[from].addDest(DISP, cells[to], tmWriter);
						
						
					}
					
				}
			}
		}
		
		
		if(SPLIT_COL != 0 ) {
			
			
			
			//top left -> top right
			int cellA = (int) ((Math.ceil(SPLIT_COL) - 1) + (NUM_COLS * Math.floor(splitRow / 2)));
			int cellB = cellA + 1;
			cells[cellA].addDest(DISP_OUT, cells[cellB], tmWriter);			
			if(DISP_IN > 0)
				cells[cellB].addDest(DISP_IN, cells[cellA], tmWriter);
		}
		
		//allocate clusters
		int clustRows = 1;
		int clustCols = 1;
		if(settings.NUM_BOXES > NUM_CORES) {
		
			int clustSize = (int) Math.round((double)settings.NUM_BOXES / (double)NUM_CORES);
			clustRows = Math.min(NUM_ROWS,(int) Math.round(Math.sqrt(clustSize)));
			clustCols = clustSize / clustRows;
		}
		
        for(int i =0 ; i < settings.NUM_BOXES; i++) {
        	try {
				cells[i].sortMovers(cells);
			} catch (Exception e) {
				e.printStackTrace();
			}
        }

		
		
		
		/////////////////// allocate clusters
		HashMap<Integer, Cluster> clusts = new HashMap<Integer, Cluster>();

		int startX = 0;
		int startY = 0;
		int clustI = 0;
		
		int nCols = clustCols;
		int nRows = clustRows;
		while(startY < (NUM_COLS * NUM_ROWS) ) {
			for(int x = 0; x < nCols; x++) {
				for(int y = 0; y < nRows; y++) {
		            
		            
		            int cellI = startY + (NUM_COLS * y) + startX + x;
//		            if(cellI < settings.NUM_BOXES) {
//		            		clusts.putIfAbsent(clustI, new Cluster(rd.nextInt(),0));
//		            		clusts.get(clustI).addGridCell(cells[cellI]);
//		 
//		            }

				}
			}
			clustI++;
			startX = startX + clustCols;
			if(startX >= NUM_COLS) {
				startX = 0;
				startY = startY + (NUM_COLS * clustRows);
			}
			
			nCols = Math.min(NUM_COLS - startX, clustCols);

		}
		
		
		try {
			FileWriter outputfile = new FileWriter(settings.TM_FILE,false);
			outputfile.write(tmWriter.toString());
			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
        return cells;

	}

	public static boolean sameClust(int cellA, int cellB, int splitRow) {
		int colA = (cellA % NUM_COLS);
		int colB = (cellB % NUM_COLS);
		
		if(Math.abs(colA - colB) > 1) // so doesn't loop around sides
			return false;
		
		if(SPLIT_COL == 0) //if all one cluster
			return true;
		
		int rowA = Math.floorDiv(cellA, NUM_COLS);
		int rowB = Math.floorDiv(cellB, NUM_COLS);

		//row split
		if(colA == colB && colA <= Math.ceil(SPLIT_COL) - 1) {
			return rowA >= splitRow == rowB >= splitRow;
		}
		
		if(rowA >= splitRow && rowB >= splitRow)
			return true;
		
		return(colA > (Math.ceil(SPLIT_COL) - 1) == colB > (Math.ceil(SPLIT_COL) - 1) );
		
		
			
	}
	
}
