/**Config parameters used in transportMatrix.artificialTM.MakeArtificialTM 
 * to build synthetic/artificial (simple neighbour to neighbour square) Transport Matrix
 * 
 */

package config;

public class ArtificialTMConfig {

	/**Whether build artificial TM at all*/
	public final boolean buildTM;

	/**number of distributed cores*/
	public final int tmNumCores;
	public final int tmNumRows;
	public final int tmNumCols;
	/**uniform dispersal between all neighbouring locations*/
	public final double tmDisp;
	/**if will connect as ring, not neighbour to neighbour*/
	public final boolean tmGyre;
	/**If using isolated region with only one input/output channel : dispersal out of region*/
	public final double tmDispOut;
	/**If using isolated region with only one input/output channel : dispersal in to region*/
	public final double tmDispIn;
	/**determines if using isolated region with only one input/output channel*/
	public final double tmSplitCol;

	
	public ArtificialTMConfig(IniFileReader iniFR) throws Exception{

		buildTM = iniFR.getParamValue( "BUILD_TM", "TransportMatrix", false).trim().toLowerCase().equals("true");
		tmNumCores = Integer.parseInt(iniFR.getParamValue( "TM_NUM_CORES", "TransportMatrix", false));
		tmNumRows = Integer.parseInt(iniFR.getParamValue( "TM_NUM_ROWS", "TransportMatrix", false));
		tmNumCols = Integer.parseInt(iniFR.getParamValue( "TM_NUM_COLS", "TransportMatrix", false));
		tmDisp = Double.parseDouble(iniFR.getParamValue( "TM_DISP", "TransportMatrix", false));
		tmGyre = iniFR.getParamValue( "TM_GYRE", "TransportMatrix", false).trim().toLowerCase().equals("true");
		tmDispOut = Double.parseDouble(iniFR.getParamValue( "TM_DISP_OUT", "TransportMatrix", false));
		tmDispIn  = Double.parseDouble(iniFR.getParamValue( "TM_DISP_IN", "TransportMatrix", false));
		tmSplitCol  = Double.parseDouble(iniFR.getParamValue( "TM_SPLIT_COL", "TransportMatrix", false));
	}
}
