/**Singleton object for stateful information about simulation
 * 
 */

package control;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import transportMatrix.GridBox;

public class RunState {

		/**NOT THREAD SAFE Complete list of grid boxes on this machine.*/
		final ArrayList<GridBox> activeBoxs;
		//TODO Alternative implementation:
			//1) T_opt in Lineage{}: more "logical" in OOP sense and no need for concurrent collection
				//BUT - longer MPI messages and memory cost
				//	  - existing method could be extended for arbitrary additional Lineage attributes
		/**T_opt of each lineage - <Lineage ID, T_opt>.*/
		public final ConcurrentHashMap<Long,Float> tempLins;
		/**Whether simulation has completed.*/
		boolean finished = false;
		/**Format for dates used written at start of log and in file names*/
		public static final SimpleDateFormat SDF = new SimpleDateFormat("dd-MM-yyyy HH-mm-ss");

		

		/**Prefix for all output filenames of format: 
		 		* [filename specified FILE_OUT] + "_T" + startTimeStr */
		public final String simulationName;
		/**Simulation start time from System.currentTimeMillis() and synchronised across machines.*/
		public final long startTime;
		/**startTime formatted for filenames*/
		public final String startTimeStr;
		/**Random seed (synchronised across machines)*/
		public final int seed;
		/**Current simulation hour.*/
		public long hour;
		/**Current simulation day.*/
		public long day;
		
		/**
		 * 
		 * @param activeBoxs
		 * @param tempLins
		 * @param startTime
		 * @param simulationName
		 * @param seed
		 */
		public RunState(ArrayList<GridBox> activeBoxs, ConcurrentHashMap<Long, Float> tempLins, 
												long startTime, String simulationName, int seed) {
			this.activeBoxs = activeBoxs;
			this.tempLins = tempLins;
			this.startTime = startTime;
			this.seed = seed;
			this.simulationName = simulationName;
			this.startTimeStr = SDF.format(new Date(startTime));

		}// 
		
	}