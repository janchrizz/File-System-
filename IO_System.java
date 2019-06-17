import java.util.*;
import java.io.*;

public class IO_System {

	private char[][] ldisk;
	private static int k = 7;
	private static int LENGTH = 64;
	private static int BLOCK = 64;
	
	IO_System() {
		this.ldisk = new char[LENGTH][BLOCK];
	} 
	
	IO_System(int l, int b) {
		this.ldisk = new char[l][b];
	} 

	public int unpac(int loc, char[] an_array) {

    	final int MASK = 0xff;
    	int v = (int)an_array[loc] & MASK;

    	for (int i=1; i < 4; i++) {
        	v = v << 8; 
        	v = v | ((int)an_array[loc+i] & MASK);
    	}//for
    	return v;
	}//unpack

	public void pac(int val, int loc, char[] an_array) {
    	final int MASK = 0xff;
    	for (int i=3; i >= 0; i--) {
        	an_array[loc+i] = (char)(val & MASK);
        	val = val >> 8;
        	
    	}//for
	}//pack
	
	public void read_block(int i, char[] p) {
		for (int j = 0; j < BLOCK; j++)  {
			p[j] = this.ldisk[i][j];
		}
	}

	public void write_block(int i, char[] p) {
		for (int j = 0; j < BLOCK; j++)  {
			this.ldisk[i][j] = p[j];
		}
	}

	public void print_ldisk(String filename) {

		PrintWriter fout = null;
		try {
			fout = new PrintWriter(new FileWriter(filename));
			
			for (int i=0; i < BLOCK; i++) {
				for(int j =0 ;j<64;j++) {
					fout.print((int) ldisk[i][j] + " ");
				}
				fout.println();
				
			}//for
			fout.close();
		}//try
		catch(IOException e) {
			System.out.print(e);
		}

	}//print

	public void restore_ldisk(String filename) {
		Scanner fin = null;
		
		try {
			fin = new Scanner(new File(filename));
			int looping = 0;
			
			while(fin.hasNextLine() && looping < LENGTH) {
				for(int i = 0; i < 64; i++) {
					ldisk[looping][i] = (char)fin.nextInt();
				}

				fin.nextLine();
				looping++;
			}//while
			fin.close();
		}//try
		catch(IOException e) {
			System.out.print(e);
		}//catch

	}//restore

	public void print_content() {
		int i = 0;
		for (char[] element : this.ldisk) {
			for(char el: element) {
				if (el == '\u0000') {
					System.out.print("0" + " ");
    				
				}
				else {
					System.out.print(el + " ");
				}
			}//el
			System.out.println("-" +i);
			i++;
		}//element
	}//print

	public void testing() {
		System.out.println("Dir block: " + unpac(4, ldisk[1]));
	}
}










