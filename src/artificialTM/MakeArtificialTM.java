package transportMatrix.artificialTM;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import config.SciConfig;
import control.Runner;
import parallelization.Cluster;
import transportMatrix.GridBox;

public class MakeArtificialTM {

	
	public static GridBox[] makeUniformTM() {
		
		//Runner.settings.ART_TM_CONFIG.TM_DISP_ is per day is assumed per day - adjust if different interval
		double disp = Runner.settings.TM.TM_DISP / (24/ Runner.settings.SCI.DISP_HOURS) ;
		double dispOut = Runner.settings.TM.TM_DISP_OUT / (24/ Runner.settings.SCI.DISP_HOURS) ;
		double dispIn = Runner.settings.TM.TM_DISP_IN / (24/ Runner.settings.SCI.DISP_HOURS) ;
		
		StringBuilder tmWriter = new StringBuilder("From,To,Weight");
		
		GridBox[] boxes = new GridBox[Runner.settings.NUM_BOXES];
		
		int splitRow = (int) ((Runner.settings.TM.TM_SPLIT_COL % 1.0) * Runner.settings.TM.TM_NUM_ROWS);
		if(splitRow == 0)
			splitRow = Runner.settings.TM.TM_NUM_ROWS;
		
		//columns then rows
		for(int i =0; i < Runner.settings.NUM_BOXES; i++) {
			int from = i;
			int[] tos = Runner.settings.TM.TM_GYRE ?
					new int[] {i + 1} :
					new int[] {i + 1, i + Runner.settings.TM.TM_NUM_COLS, i - 1, i - Runner.settings.TM.TM_NUM_COLS};
			
			
			
			if(boxes[from] == null)
                boxes[from] = new GridBox(from, 1.0, new double[] {-999.0});
			for(int to : tos) {
				
				if(Runner.settings.TM.TM_GYRE) {
					if(to == Runner.settings.NUM_BOXES)
						to = 0;
				}
				if(to < Runner.settings.NUM_BOXES && to >= 0) {
					if(boxes[to] == null)
						boxes[to] = new GridBox(to, 1.0, new double[] {-999.0});
					if(Runner.settings.TM.TM_GYRE &&  (to == 0 || to == Runner.settings.NUM_BOXES / 2) && dispOut != disp) {
						if(dispOut > 0)
							boxes[from].addDest(dispOut, boxes[to], tmWriter);
						if(to == Runner.settings.NUM_BOXES / 2)
							boxes[from].addDest(disp - dispOut, boxes[0], tmWriter);
						else
							boxes[from].addDest(disp - dispOut, boxes[Runner.settings.NUM_BOXES / 2], tmWriter);
						
					}
					else{
						
						
						if(sameClust(from,to,splitRow))
							boxes[from].addDest(disp, boxes[to], tmWriter);
						
						
					}
					
				}
			}
		}
		
		//add channels in/out of isolated region if there is one
		if(Runner.settings.TM.TM_SPLIT_COL != 0 ) {
			
			
			
			//top left -> top right
			int boxA = (int) ((Math.ceil(Runner.settings.TM.TM_SPLIT_COL) - 1) + (Runner.settings.TM.TM_NUM_COLS * Math.floor(splitRow / 2)));
			int boxB = boxA + 1;
			boxes[boxA].addDest(dispOut, boxes[boxB], tmWriter);			
			if(dispIn > 0)
				boxes[boxB].addDest(dispIn, boxes[boxA], tmWriter);
		}
		
		//allocate clusters
		int clustRows = 1;
		int clustCols = 1;
		if(Runner.settings.NUM_BOXES > Runner.settings.TM.TM_NUM_CORES) {
		
			int clustSize = (int) Math.round((double)Runner.settings.NUM_BOXES / (double)Runner.settings.TM.TM_NUM_CORES);
			clustRows = Math.min(Runner.settings.TM.TM_NUM_ROWS,(int) Math.round(Math.sqrt(clustSize)));
			clustCols = clustSize / clustRows;
		}
		
        for(int i =0 ; i < Runner.settings.NUM_BOXES; i++) {
        	try {
				boxes[i].sortMovers(boxes);
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
		while(startY < (Runner.settings.TM.TM_NUM_COLS * Runner.settings.TM.TM_NUM_ROWS) ) {
			for(int x = 0; x < nCols; x++) {
				for(int y = 0; y < nRows; y++) {
		            
		            
		            int boxI = startY + (Runner.settings.TM.TM_NUM_COLS * y) + startX + x;
//		            if(boxI < Runner.settings.NUM_BOXES) {
//		            		clusts.putIfAbsent(clustI, new Cluster(rd.nextInt(),0));
//		            		clusts.get(clustI).addGridBox(boxs[boxI]);
//		 
//		            }

				}
			}
			clustI++;
			startX = startX + clustCols;
			if(startX >= Runner.settings.TM.TM_NUM_COLS) {
				startX = 0;
				startY = startY + (Runner.settings.TM.TM_NUM_COLS * clustRows);
			}
			
			nCols = Math.min(Runner.settings.TM.TM_NUM_COLS - startX, clustCols);

		}
		
		
		try {
			FileWriter outputfile = new FileWriter(Runner.settings.SCI.TM_FILE,false);
			outputfile.write(tmWriter.toString());
			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
        return boxes;

	}

	public static boolean sameClust(int boxA, int boxB, int splitRow) {
		int colA = (boxA % Runner.settings.TM.TM_NUM_COLS);
		int colB = (boxB % Runner.settings.TM.TM_NUM_COLS);
		
		if(Math.abs(colA - colB) > 1) // so doesn't loop around sides
			return false;
		
		if(Runner.settings.TM.TM_SPLIT_COL == 0) //if not using isolated region at all
			return true;
		
		int rowA = Math.floorDiv(boxA, Runner.settings.TM.TM_NUM_COLS);
		int rowB = Math.floorDiv(boxB, Runner.settings.TM.TM_NUM_COLS);

		//row split
		if(colA == colB && colA <= Math.ceil(Runner.settings.TM.TM_SPLIT_COL) - 1) {
			return rowA >= splitRow == rowB >= splitRow;
		}
		
		if(rowA >= splitRow && rowB >= splitRow)
			return true;
		
		return(colA > (Math.ceil(Runner.settings.TM.TM_SPLIT_COL) - 1) == colB > (Math.ceil(Runner.settings.TM.TM_SPLIT_COL) - 1) );
		
		
			
	}
	
}
