package test;
import java.util.Arrays;

import mpi.*;


public class TestMPI {
	public static void main(String[] args) {
		System.out.println(Arrays.toString(args));
		
		int me,size;

		args = MPI.Init(args);
		me = MPI.COMM_WORLD.Rank();
		size = MPI.COMM_WORLD.Size();
	
		System.out.println(MPI.Get_processor_name()+": Hello World from "+me+" of "+size);
	
		MPI.Finalize();

	}
}
