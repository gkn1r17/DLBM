package util;

import java.util.HashMap;

import cern.jet.random.engine.DRand;
import lbm.GridBox;
import lbm.Settings;
import parallelization.Cluster;

public class MakeArtificialTM {
	public static int NUM_CORES = 40;
	public static int NUM_ROWS = 1;
	public static int NUM_COLS = 1;
	public static double UNIFORM_DISP = 0.0001;

	
	public static GridBox[] makeUniformTM(DRand rd) {
		
		//adjust for time intervals per generation
		UNIFORM_DISP = UNIFORM_DISP / (Settings.GROWTH_HOURS / (24 * 5)) ;
		Settings.VOL_FILE = null;
		Settings.TEMP_FILE = null;
		Settings.NUM_BOXES = NUM_ROWS * NUM_COLS;

		
		
		GridBox[] cells = new GridBox[Settings.NUM_BOXES];
		
		//columns then rows
		for(int i =0; i < Settings.NUM_BOXES; i++) {
			int from = i;
			int[] tos = new int[] {i + 1, i + NUM_COLS, i - 1, i - NUM_COLS};
			if(cells[from] == null)
                cells[from] = new GridBox(from, 1.0, new double[] {-999.0} );
			for(int to : tos) {
				if(to < Settings.NUM_BOXES && to >= 0) {
					if(cells[to] == null)
						cells[to] = new GridBox(to, 1.0, new double[] {-999.0} );
					cells[from].addDest(UNIFORM_DISP, cells[to]);
					
				}
			}
		}
		
		
		//allocate clusters
		int clustRows = 1;
		int clustCols = 1;
		if(Settings.NUM_BOXES > NUM_CORES) {
		
			int clustSize = (int) Math.round((double)Settings.NUM_BOXES / (double)NUM_CORES);
			clustRows = Math.min(NUM_ROWS,(int) Math.round(Math.sqrt(clustSize)));
			clustCols = clustSize / clustRows;
		}
		
        for(int i =0 ; i < Settings.NUM_BOXES; i++) {
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
		            if(cellI < Settings.NUM_BOXES) {
		            		clusts.putIfAbsent(clustI, new Cluster(rd.nextInt(),0));
		            		clusts.get(clustI).addGridCell(cells[cellI]);
		 
		            }

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
		
        return cells;

	}

	
	
}
