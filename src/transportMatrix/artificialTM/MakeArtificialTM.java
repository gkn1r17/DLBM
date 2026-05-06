/**Produce Transport Matrix in form of Grid with every box connected to its neighbours
 * 
 */

package transportMatrix.artificialTM;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import config.SciConfig;
import control.Runner;
import parallelization.Cluster;
import transportMatrix.GridBox;

public class MakeArtificialTM {

	/**
	 * 
	 * @return array containing every GridBox
	 */
	public static GridBox[] makeUniformTM() {
		
		//Runner.settings.ART_TM_CONFIG.TM_DISP_ is per day is assumed per day - adjust if different interval
		double disp = Runner.settings.tm.tmDisp / (24/ Runner.settings.sci.dispHours) ;
		double dispOut = Runner.settings.tm.tmDispOut / (24/ Runner.settings.sci.dispHours) ;
		double dispIn = Runner.settings.tm.tmDispIn / (24/ Runner.settings.sci.dispHours) ;
		
		StringBuilder tmWriter = new StringBuilder("From,To,Weight");
		
		GridBox[] boxes = new GridBox[Runner.settings.numBoxes];
		
		int splitRow = (int) ((Runner.settings.tm.tmSplitCol % 1.0) * Runner.settings.tm.tmNumRows);
		if(splitRow == 0)
			splitRow = Runner.settings.tm.tmNumRows;
		
		//columns then rows
		for(int i =0; i < Runner.settings.numBoxes; i++) {
			int from = i;
			int[] tos = Runner.settings.tm.tmGyre ?
					new int[] {i + 1} :
					new int[] {i + 1, i + Runner.settings.tm.tmNumCols, i - 1, i - Runner.settings.tm.tmNumCols};
			
			
			
			if(boxes[from] == null)
                boxes[from] = new GridBox(from, 1.0, new double[] {-999.0});
			for(int to : tos) {
				
				if(Runner.settings.tm.tmGyre) {
					if(to == Runner.settings.numBoxes)
						to = 0;
				}
				if(to < Runner.settings.numBoxes && to >= 0) {
					if(boxes[to] == null)
						boxes[to] = new GridBox(to, 1.0, new double[] {-999.0});
					if(Runner.settings.tm.tmGyre &&  (to == 0 || to == Runner.settings.numBoxes / 2) && dispOut != disp) {
						if(dispOut > 0)
							boxes[from].addDest(dispOut, boxes[to], tmWriter);
						if(to == Runner.settings.numBoxes / 2)
							boxes[from].addDest(disp - dispOut, boxes[0], tmWriter);
						else
							boxes[from].addDest(disp - dispOut, boxes[Runner.settings.numBoxes / 2], tmWriter);
						
					}
					else{
						
						
						if(sameClust(from,to,splitRow))
							boxes[from].addDest(disp, boxes[to], tmWriter);
						
						
					}
					
				}
			}
		}
		
		//add channels in/out of isolated region if there is one
		if(Runner.settings.tm.tmSplitCol != 0 ) {
			
			
			
			//top left -> top right
			int boxA = (int) ((Math.ceil(Runner.settings.tm.tmSplitCol) - 1) + (Runner.settings.tm.tmNumCols * Math.floor(splitRow / 2)));
			int boxB = boxA + 1;
			boxes[boxA].addDest(dispOut, boxes[boxB], tmWriter);			
			if(dispIn > 0)
				boxes[boxB].addDest(dispIn, boxes[boxA], tmWriter);
		}
		
		//allocate clusters
		int clustRows = 1;
		int clustCols = 1;
		if(Runner.settings.numBoxes > Runner.settings.tm.tmNumCores) {
		
			int clustSize = (int) Math.round((double)Runner.settings.numBoxes / (double)Runner.settings.tm.tmNumCores);
			clustRows = Math.min(Runner.settings.tm.tmNumRows,(int) Math.round(Math.sqrt(clustSize)));
			clustCols = clustSize / clustRows;
		}
		
        for(int i =0 ; i < Runner.settings.numBoxes; i++) {
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
		while(startY < (Runner.settings.tm.tmNumCols * Runner.settings.tm.tmNumRows) ) {
			for(int x = 0; x < nCols; x++) {
				for(int y = 0; y < nRows; y++) {
		            
		            
		            int boxI = startY + (Runner.settings.tm.tmNumCols * y) + startX + x;
//		            if(boxI < Runner.settings.NUM_BOXES) {
//		            		clusts.putIfAbsent(clustI, new Cluster(rd.nextInt(),0));
//		            		clusts.get(clustI).addGridBox(boxs[boxI]);
//		 
//		            }

				}
			}
			clustI++;
			startX = startX + clustCols;
			if(startX >= Runner.settings.tm.tmNumCols) {
				startX = 0;
				startY = startY + (Runner.settings.tm.tmNumCols * clustRows);
			}
			
			nCols = Math.min(Runner.settings.tm.tmNumCols - startX, clustCols);

		}
		
		
		try {
			FileWriter outputfile = new FileWriter(Runner.settings.sci.tmFile,false);
			outputfile.write(tmWriter.toString());
			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
        return boxes;

	}

	/**
	 * 
	 * @param boxA
	 * @param boxB
	 * @param splitRow
	 * @return
	 */
	public static boolean sameClust(int boxA, int boxB, int splitRow) {
		int colA = (boxA % Runner.settings.tm.tmNumCols);
		int colB = (boxB % Runner.settings.tm.tmNumCols);
		
		if(Math.abs(colA - colB) > 1) // so doesn't loop around sides
			return false;
		
		if(Runner.settings.tm.tmSplitCol == 0) //if not using isolated region at all
			return true;
		
		int rowA = Math.floorDiv(boxA, Runner.settings.tm.tmNumCols);
		int rowB = Math.floorDiv(boxB, Runner.settings.tm.tmNumCols);

		//row split
		if(colA == colB && colA <= Math.ceil(Runner.settings.tm.tmSplitCol) - 1) {
			return rowA >= splitRow == rowB >= splitRow;
		}
		
		if(rowA >= splitRow && rowB >= splitRow)
			return true;
		
		return(colA > (Math.ceil(Runner.settings.tm.tmSplitCol) - 1) == colB > (Math.ceil(Runner.settings.tm.tmSplitCol) - 1) );
		
		
			
	}
	
}
