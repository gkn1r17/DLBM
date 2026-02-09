/**Config parameters used in transportMatrix.artificialTM.MakeArtificialTM 
 * to build synthetic/artificial (simple neighbour to neighbour square) Transport Matrix
 * 
 */

package config;

public class ArtificialTMConfig {

	/**Whether build artificial TM at all*/
	public final boolean BUILD_TM;

	/**number of distributed cores*/
	public final int TM_NUM_CORES;
	public final int TM_NUM_ROWS;
	public final int TM_NUM_COLS;
	/**uniform dispersal between all neighbouring locations*/
	public final double TM_DISP;
	/**if will connect as ring, not neighbour to neighbour*/
	public final boolean TM_GYRE;
	/**If using isolated region with only one input/output channel : dispersal out of region*/
	public final double TM_DISP_OUT;
	/**If using isolated region with only one input/output channel : dispersal in to region*/
	public final double TM_DISP_IN;
	/**determines if using isolated region with only one input/output channel*/
	public final double TM_SPLIT_COL;

	
	public ArtificialTMConfig(IniFileReader iniFR) throws Exception{

		BUILD_TM = iniFR.getParamValue( "BUILD_TM", "TransportMatrix", false).trim().toLowerCase().equals("true");
		TM_NUM_CORES = Integer.parseInt(iniFR.getParamValue( "TM_NUM_CORES", "TransportMatrix", false));
		TM_NUM_ROWS = Integer.parseInt(iniFR.getParamValue( "TM_NUM_ROWS", "TransportMatrix", false));
		TM_NUM_COLS = Integer.parseInt(iniFR.getParamValue( "TM_NUM_COLS", "TransportMatrix", false));
		TM_DISP = Double.parseDouble(iniFR.getParamValue( "TM_DISP", "TransportMatrix", false));
		TM_GYRE = iniFR.getParamValue( "TM_GYRE", "TransportMatrix", false).trim().toLowerCase().equals("true");
		TM_DISP_OUT = Double.parseDouble(iniFR.getParamValue( "TM_DISP_OUT", "TransportMatrix", false));
		TM_DISP_IN  = Double.parseDouble(iniFR.getParamValue( "TM_DISP_IN", "TransportMatrix", false));
		TM_SPLIT_COL  = Double.parseDouble(iniFR.getParamValue( "TM_SPLIT_COL", "TransportMatrix", false));
	}
}
