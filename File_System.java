import java.util.*;
import java.io.*;
import java.lang.*;

public class File_System {

	private IO_System io = new IO_System();
	private static int LENGTH = 64;
	private static int BLOCK = 64;
	private static int k =7;
	private int[] container_descriptor = new int[24];
	private int[] container_directory = new int[24];
	private int[] container_oft = new int[4];
	
	//oft has 64 bytes buffer, current pos, file desc index
	private char[][] oft_table = new char[4][BLOCK+8];

	File_System() {

		IO_System io = new IO_System();

		//set first k bits to be reserved
		for (int i =0; i < k; i++) {
			set_bit(i, 1);
		}

		container_descriptor[0] = 1; //descriptor 0 is occupied
		container_directory[23] = 1;//impossible 

	}//constructor

	public void reset() {
		char[] an_array = new char[BLOCK];
		for ( int i = 0; i< 64;i++) {
			this.io.write_block(i, an_array);
		}
		Arrays.fill(container_directory,0);
		Arrays.fill(container_descriptor,0);
		Arrays.fill(container_oft,0);

		for(int i = 0; i < 4; i++) {
			Arrays.fill(oft_table[i], '\u0000');
		}

		//set first k bits to be reserved
		for (int i =0; i < k; i++) {
			set_bit(i, 1);
		}

		container_descriptor[0] = 1; //descriptor 0 is occupied
		container_directory[23] = 1;//impossible
		System.out.println("Disk initialized");
	}//reset everything

	public boolean is_dir_open() {
		if(container_oft[0] == 0) {
			return false;
		}
		int descriptor_index = unpack(68, oft_table[0]);
		if(descriptor_index == 0) {
			return true;
		}
		return false;
	}
	public boolean is_open(char[] symbolic_file_name) {
		int directory_index = find_file_position(symbolic_file_name);
		int descriptor_index = get_descriptor_index(directory_index);
		for ( int i = 1; i < 4; i++) {
			if(container_oft[i] == 1) { 
				int compare_index = unpack(68,oft_table[i]);
				if(compare_index == descriptor_index) {
					return true;
				}//if desc index is equal
			}//if the oft index is filled
		}//for
		return false;
	}

	public int open(char[] symbolic_file_name, int flag) {

		if(symbolic_file_name.length > 4) {
			System.out.println("Invalid name");
			return -1;
		}

		int descriptor_index;
		int oft_index;

		if(flag == 0) {
			int directory_index = find_file_position(symbolic_file_name);

			if(directory_index == -1) {
			System.out.println("File not found");
				return -1;
			}//if

			if(is_open(symbolic_file_name)) {
				System.out.println("File already open");
				return -1;
			}//if

			descriptor_index = get_descriptor_index(directory_index);
			oft_index = find_free_oft();
		}//if flag =0, normal open file operation

		else {
			descriptor_index = 0;
			oft_index = 0;
		}

		if(oft_index == -1) {
			System.out.println("OFT full");
			return -1;
		}

		//for directory purpose, 5 is a random number
		int which_block = 5;

		//set descriptor index to the designated OFT
		pack(descriptor_index, 68, oft_table[oft_index]);

		//Begin copying first block to buffer
		//if the file has no content/assigned block, buffer is empty
		char[] new_array = new char[BLOCK];
		for(int i = 0; i < 3; i++) {
			int block_no = get_assigned_block(descriptor_index,i);
			if(block_no != 0 ) {
				this.io.read_block(block_no, new_array);
				which_block = i;
				break;
			}//if
		}//for
		
		for(int i = 0; i< BLOCK; i++) {
			oft_table[oft_index][i] = new_array[i];
		}

		/** For other files, position start at 0 because never shrinks
		For directory, position doesn't always start at 0, because 
		directory can lose a block, so we pack position 
		depending on which_block (0/1/2), which is the first block of
		directory that still has an allocated block
		*/
		if (flag == 1) { //special open for directory
			if (which_block != 5) {
				int the_position = which_block*64;
				pack(the_position, 64, oft_table[oft_index]);
			}//if
		}//if

		container_oft[oft_index] = 1;
		return oft_index;

	}//open

	public void close(int oft_index, int flag) {
		
		if(container_oft[oft_index]==0) {
			System.out.println("No file open at that index");
			return;
		}
		//write current buffer to disk if not directory
		if(flag == 0) {
			return_buffer(oft_index,0);
		}

		//free oft
		container_oft[oft_index] = 0;
		for(int i = 0; i < BLOCK + 8; i++) {
			oft_table[oft_index][i] = '\u0000';
		}
	}//close

	public void read(int oft_index, char[] some_array, int count) {

		if(container_oft[oft_index] == 0) {
			System.out.println("No open file in that index");
			return;
		}

		int current_position = unpack(64, oft_table[oft_index]);
		int original_current_position = current_position;
		int descriptor_index = unpack(68, oft_table[oft_index]);
		int file_size = get_file_size(descriptor_index);
		int end_position = (current_position + count > file_size)? file_size: current_position+count;
		int local_position = current_position%64;
		int some_array_counter = 0;

		while(current_position < end_position) {

			if(current_position != 0 && current_position%64 == 0) {
				//if it is not in the same buffer as previous
				if(original_current_position/64 != current_position/64) {
					int which_block = current_position/64;

					//write back buffer and update file length
					return_buffer(oft_index,1);
					//bring new buffer
					bring_to_buffer(oft_index, which_block );
					local_position = 0;

					original_current_position = current_position;
				}
			}
			some_array[some_array_counter] = oft_table[oft_index][local_position];

			//Update current position
			current_position++;
			pack(current_position, 64, oft_table[oft_index]);

			local_position++;
			some_array_counter++;
		}//while
		System.out.println(current_position);
		String read_word = new String(some_array);
		System.out.println(read_word);
	}//read

	public void write(int oft_index, char some_char, int count) {

		if(container_oft[oft_index] == 0) {
			System.out.println("No open file in that index");
			return;
		}

		int descriptor_index = unpack(68, oft_table[oft_index]);

		if (descriptor_index == 0) {
			System.out.println("Cannot write to directory");
			return;
		}

		//Compute current position and end position
		int current_position = unpack(64, oft_table[oft_index]);
		int original_current_position = current_position;
		//set limit of ending position to max file size 192 bytes
		int end_position = (current_position + count > 192)? 192: current_position+count;
		
		int local_position = current_position%64;

		while(current_position < end_position) {
			if(current_position == 0 || current_position%64 == 0) {

				int which_block = current_position/64;
				return_buffer(oft_index,1);

				//if assigned block is 0, means not yet assigned
				if(get_assigned_block(descriptor_index, which_block)==0) {

					if(find_free_block() != -1) {
						int new_block = find_free_block();
						allocate_block(descriptor_index, new_block);
					}
					else {
						System.out.println("No more empty block");
						break;
					}
				}
				bring_to_buffer(oft_index, which_block);
				local_position = 0;

			}
			oft_table[oft_index][local_position] = some_char;

			//Update current position
			current_position++;
			pack(current_position, 64, oft_table[oft_index]);

			local_position++;
		}//while
		
		return_buffer(oft_index,0);
		int written_byte = current_position - original_current_position;
		System.out.println(written_byte + " bytes written");

	}

	public void return_buffer(int oft_index, int flag) {

		int current_position = unpack(64, oft_table[oft_index]);
		if(flag==1) {
			current_position--;
		}
		int descriptor_index = unpack(68, oft_table[oft_index]);

		//find which block the buffer is holding (0/1/2)
		int which_block = current_position/64;
		
		//find the absolute block number
		int block_number = get_assigned_block(descriptor_index, which_block);
		
		if(block_number == 0) {
			return;
		}

		//copy buffer and compute length of current buffer
		char[] new_array = Arrays.copyOfRange(oft_table[oft_index], 0, 64);

		int new_length = 0;
		for(int i = 0; i < BLOCK; i++) {
			if (new_array[i] != '\u0000') {
				new_length++;
			}//if
		}//for

		//copy original block and compute length
		char[] old_array = new char[BLOCK];
		this.io.read_block(block_number, old_array);
		int old_length = 0;
		for(int i = 0; i < BLOCK; i++) {
			if (old_array[i] != '\u0000') {
				old_length++;
			}//if
		}//for
		
		//If buffer has grown, update file size in descriptor
		if(new_length != old_length) {
			int increment = new_length - old_length;
			add_file_size(descriptor_index, increment);
		}//if

		//write the buffer
		this.io.write_block(block_number, new_array);

	}

	public void bring_to_buffer(int oft_index, int which_block) {
		int descriptor_index = unpack(68, oft_table[oft_index]);

		//careful get_assigned may return 0
		int new_block = get_assigned_block(descriptor_index, which_block);
		if(new_block == 0) {
			return;
		}

		//This function does not rewrite buffer to previous block
		char[] new_array = new char[BLOCK];
		this.io.read_block(new_block, new_array);
		for(int i = 0; i< BLOCK; i++) {
			oft_table[oft_index][i] = new_array[i];
		}//for

	}

	public void lseek(int oft_index, int new_position)
	{
		if(container_oft[oft_index] == 0) {
			System.out.println("No open file at this OFT index");
			return;
		}

		int descriptor_index = unpack(68, oft_table[oft_index]);
		int file_size = get_file_size(descriptor_index);
		if(new_position >= file_size) {
			System.out.println("Position out of bounds");
			return;
		}

		int current_position = unpack(64, oft_table[oft_index]);
		//Find which block is the buffer currently holding, 0/1/2
		//If different from new_position's block, need to get to buffer
		int which_block = current_position/64;
		int new_which_block = new_position/64;

		//If new and old position is in different block, need to get new to buffer
		if(which_block!=new_which_block) {
			
			return_buffer(oft_index,0);

			//Read the new block to buffer
			bring_to_buffer(oft_index, new_which_block);
		}//if

		//Set new position
		pack(new_position, 64, oft_table[oft_index]);
		System.out.println("position updated to " + new_position);
	}//lseek

	public int find_free_oft() {
		for(int i = 1; i < 4; i++) {
			if(container_oft[i]==0) {
				return i;
			}//if
		}//for
		return -1;
	}//return index of free OFT

	public int unpack(int loc, char[] an_array) {

    	final int MASK = 0xff;
    	int v = (int)an_array[loc] & MASK;

    	for (int i=1; i < 4; i++) {
        	v = v << 8; 
        	v = v | ((int)an_array[loc+i] & MASK);
    	}//for
    	return v;
	}//unpack

	public void pack(int val, int loc, char[] an_array) {
    	final int MASK = 0xff;
    	for (int i=3; i >= 0; i--) {
        	an_array[loc+i] = (char)(val & MASK);
        	val = val >> 8;
        	
    	}//for
	}//pack

	public int get_directory_block(int which_block) { //return block#, which_block=0/1/2
		char[] directory_descriptor = get_descriptor(0); //get directory desc 16 bytes
		
		int directory_block = unpack((1+which_block)*4,directory_descriptor);
		
		return directory_block;
	}//careful of 0 from this return


	//helper functions under the model that char ldisk[64][64]
	public int find_free_block() {
		char[] bit_map = new char[BLOCK];
		this.io.read_block (0, bit_map);

		for (int i = 0; i < 8; i++) {
			for(int j = 7; j >= 0; j--) {
				int bit = (bit_map[i] >> j) & 1;
				
				if (bit == 0) {
					return (i*8)+(8-j-1); //return first free block index start from 0
				}//if
			}//for
		}//for
		return -1;
	}//find first free block in bitmap

	public void set_bit(int i, int bit_value) {
		char[] bit_map = new char[BLOCK];
		this.io.read_block (0, bit_map); //read ldisk[0] to bit_map[]
		int bit_map_index = (i==0) ? 0: i/8; //find which char has the bit responsible for block i
		char copy_byte = bit_map[bit_map_index]; //this char holds 8 bits/block

		int new_array_index = i%8; //find which bit is responsible for i

		if (bit_value == 1) {
			copy_byte = (char)(copy_byte | (1 << (8-new_array_index-1))); 
		}
		else {
			copy_byte = (char)(copy_byte & ~(1 << (8-new_array_index-1)));
		}
		
		bit_map[bit_map_index] = copy_byte; //put new char in bit_map
		
		this.io.write_block (0, bit_map); //write to ldsik

	}//free block in bitmap upon deleting file

	public int find_free_descriptor() {
		
		for (int i = 1; i < 24; i++) {
			if(container_descriptor[i] == 0) {
				return i;
			}
		}//for
		return 0;
	}//find free descriptor

	//return array of 16 chars that contain descriptor
	public char[] get_descriptor(int descriptor_index) {
		// 24 descriptor, index 0 - 23
		int index = (descriptor_index == 0)? 0: descriptor_index/4;
		int offset = (descriptor_index%4 == 0)? 0: (descriptor_index%4)*16;
		char[] temp = new char[BLOCK];
		this.io.read_block(index+1, temp);
		char[] an_array = Arrays.copyOfRange(temp,offset,offset+16);
		return an_array;

	}

	public int get_descriptor_index(int directory_index) {
		
		int directory_block_number = directory_index/8;
		int offset = directory_index%8;
		int directory_block_index = get_directory_block(directory_block_number);
		char[] new_array = new char[BLOCK];
		this.io.read_block(directory_block_index, new_array);
		
		int descriptor_index = unpack(offset*8 +4, new_array);
		return descriptor_index;

	}

	public int get_directory_length() {
		char[] directory_descriptor = get_descriptor(0);
		int dir_length = unpack(0,directory_descriptor);
		return dir_length;
	}

	public int get_file_size(int descriptor_index) {
		char[] descriptor_array = get_descriptor(descriptor_index);
		
		char[] size_array = new char[4];
		for ( int i = 0; i < 4; i++) {
			size_array[i] = descriptor_array[i];
		}
		int size = unpack(0, size_array);
		return size;
	}

	public void add_file_size(int descriptor_index, int increment) {
		int descriptor_block = 1 + (descriptor_index/4);
		int offset_index = (descriptor_index%4) *16;

		char[] new_array = new char[64];
		this.io.read_block(descriptor_block, new_array);

		int old_size = unpack(offset_index, new_array);
		int new_size = old_size + increment;

		pack(new_size, offset_index, new_array);

		this.io.write_block(descriptor_block, new_array);
	}

	public char[] get_symbolic_file_name(int directory_index) {
		
		char[] directory_descriptor = get_descriptor(0); //get directory descriptor
		char[] temp_array = new char[BLOCK];
		//find blocks that hold directory
		int i = (directory_index==0)? 0: directory_index/8;
		int offset_index = (directory_index%8)*8;

		int directory_block = unpack(i*4+4, directory_descriptor);

		if(directory_block==0) {
			return temp_array;
		}
		
		
		this.io.read_block(directory_block, temp_array);

		char[] file_name = new char[4];
		
		file_name = Arrays.copyOfRange(temp_array,offset_index,offset_index+4);

		return file_name;
	}

	public int find_file_position(char[] symbolic_file_name) {

		char[] formatted_file_name = new char[4];
		int len = symbolic_file_name.length;
		//move file_name to a fixed 4 char array for easy comparison 
		System.arraycopy(symbolic_file_name, 0, formatted_file_name, 4 - len, len);
		//get symbolic name of every file in directory and compare
		for (int i = 0; i < 24; i++ ) {
			char[] temp_file_name = new char[4];
			temp_file_name = get_symbolic_file_name(i); //get file name of dir entry i
			
			if(Arrays.equals(formatted_file_name, temp_file_name)) {
				return i;//return directory index of the file
			}
		}
		return -1;
	}

	public int find_free_directory() {
		char[] new_array = new char[64];
		this.io.read_block(1, new_array);
		int directory_allocated_block = 0;
		for (int i = 1; i < 4; i++) {
			int block_no = unpack(i*4,new_array);
			int start = (i-1)*8;
			int end = start+8;
			if (block_no != 0) {
				for(int j=start; j < end;j++) {
					if(container_directory[j]==0) {
						return j;
					}//if
				}//for
			}//if the block is a valid number
		}
		
		return -1;
			
	}//return index of free directory

	public void write_descriptor(char[] descriptor_array, int descriptor_index) {

		int descriptor_block = descriptor_index/4;
		int offset = (descriptor_index%4)*16;
		char[] temp_array = new char[64];
		this.io.read_block(descriptor_block+1, temp_array );
		int temp = 0;
		while(temp < 16) {
			temp_array[offset] = descriptor_array[temp];
			offset++;
			temp++;
		}
		
		this.io.write_block(descriptor_block+1, temp_array);
	}

	public void allocate_block(int descriptor_index, int block_number) {
		char[] descriptor_array = get_descriptor(descriptor_index);
		
		for (int i = 1; i < 4; i++) {
			int block_no = unpack(i*4, descriptor_array);

			//if 0 is read, allocate the block to desriptor
			if (block_no == 0) {

				pack(block_number, i*4, descriptor_array);
				write_descriptor(descriptor_array, descriptor_index);
				//update bitmap
				set_bit( block_number, 1);
				return;
			}//if
		}//for
	}//alloc

	public void insert_to_directory(char[] symbolic_file_name, int directory_index, int descriptor_index)
	{
		int directory_block_index = get_directory_block(directory_index/8);
		int offset = (directory_index%8); 

		//read directory block to temp_array
		char[] temp_array = new char[BLOCK]; 
		this.io.read_block(directory_block_index, temp_array);
		
		//copy file name to temp_array
		char[] formatted_file_name = new char[4];
		int len = symbolic_file_name.length;
		System.arraycopy(symbolic_file_name, 0, formatted_file_name, 4 - len, len);
		System.arraycopy(formatted_file_name, 0, temp_array, offset*8, 4);

		//copy descriptor index to temp_array
		pack(descriptor_index, offset*8+4, temp_array);

		//write back temp array to ldisk
		this.io.write_block(directory_block_index, temp_array);
	}//insert

	public void create(char[] symbolic_file_name) {

		if(symbolic_file_name.length > 4) {
			System.out.println("Name too long");
			return;
		}//max 4 char/byte name

		if (find_file_position(symbolic_file_name) != -1) {
			System.out.println("Duplicate file creation attempted");
			return;
		}//give error if duplicate

		if(get_directory_length() > 24) {
			System.out.println("Directory is full");
			return;
		}

		//find free descriptor for the file
		int free_descriptor = find_free_descriptor();

		//if free descriptor returns 0, means descriptor is full, exit
		if(free_descriptor == 0) {
			return;
		}

		//find free directory index entry for the file
		int free_directory = find_free_directory();

		//if directory length is 0 or full, need to allocate new block for directory
		if(get_directory_length()==0 || free_directory==-1) {
			int allocated = find_free_block();
			allocate_block(0, allocated); //allocate a block to directory descriptor
		}

		//get descriptor of directory, add 1 to length, write back
		char[] directory_descriptor_block = new char[BLOCK];
		this.io.read_block(1, directory_descriptor_block);
		int new_length = unpack(0, directory_descriptor_block) + 1;
		pack(new_length, 0, directory_descriptor_block);
		this.io.write_block(1, directory_descriptor_block);

		free_directory = find_free_directory();
		
		//write the data to the corresponding directory
		insert_to_directory(symbolic_file_name, free_directory, free_descriptor);
		//set descriptor to be occupied
		container_directory[free_directory] = 1;
		container_descriptor[free_descriptor] = 1;
		String name = new String(symbolic_file_name);
		System.out.println(name + " created");

		//if directory is open, bring updated directory to buffer
		if(is_dir_open()) {
			int directory_position = unpack(64, oft_table[0]);
			int directory_which_block = directory_position/64;
			bring_to_buffer(0, directory_which_block);
		}

	}//create

	public void destroy(char[] symbolic_file_name) {

		if(symbolic_file_name.length > 4) {
			System.out.println("Name too long");
			return;
		}//max 4 char/byte name

		//find directory index of the file
		int directory_index = find_file_position(symbolic_file_name);

		if(directory_index==-1) {
			System.out.println("File not found");
			return;
		}

		//find descriptor pointed by directory entry
		int descriptor_index = get_descriptor_index(directory_index);

		//close the file if its open in oft
		for(int i = 0; i < 4; i++) {
			int compare_index = unpack(68, oft_table[i]);
			if(descriptor_index != 0 && descriptor_index == compare_index) {
				close(i , 0);
			}
		}

		/**get descriptor, find all block numbers linked to the file
		   and free them in bitmap*/
		char[] descriptor_array = get_descriptor(descriptor_index);
		char[] empty_array = new char[64];
		for (int i = 1; i < 4; i++) {
			if(unpack(i*4,descriptor_array) != 0) {
				set_bit(unpack(i*4, descriptor_array), 0);//free the bits
				this.io.write_block(unpack(i*4, descriptor_array), empty_array);
			}
		}//for

		/**free the directory from that file entry
		   free container_directory of that index
		   the function also reduce directory length by 1*/
		remove_from_directory(directory_index);
		
		//free the descriptor and the container_descriptor
		remove_from_descriptor(descriptor_index);

		/** At the end, checks if any of the 3 assigned directory block length
			falls to 0. Assigned blocks are never zero or less than
			the value of k 
			Find the directory descriptor, looks like:
			[length, block1, block2, block3]
			block1 is dir_index 0-7, 2=> 8-15, 3=> 16-22
			#23 is n/a
			check container_directory, if any of the consecutive
			value (eg:0-7) is empty, need to free that block from
			directory descriptor and bitmap */
		for (int i = 0; i < 3; i++) {
			if(get_assigned_block(0, i)!=0) {
				if(item_in_dir(i)==0) {
					//this function free from both desc and bitmap
					free_a_block(0,i);
				}//free that block from directory desc
			}//if assigned block is real/not 0
		}//for each assigned block
		String name = new String(symbolic_file_name);
		System.out.println(name + " destroyed");	

		//if directory is open, bring updated directory to buffer	
		if(is_dir_open()) {

			//need to make sure directory block is still assigned
			int directory_position = unpack(64, oft_table[0]);
			int directory_which_block = directory_position/64;

			//if the current buffer is actually destroyed, need to get another
			if(get_assigned_block(0, directory_which_block) == 0) {
				for(int i = 0; i < 3; i++) {
					if(get_assigned_block(0,i) != 0) {
						//bring new block to buffer and reset position depending on block
						bring_to_buffer(0, i);
						pack(i*64, 64, oft_table[0]);

						break;
					}//if block is non zero
				}//for
			}//if

			else {
				bring_to_buffer(0, directory_which_block);
			}


			
		}

	}

	public int get_assigned_block(int descriptor_index, int which_block) {
		/** returns the assigned block number in a descriptor,
			which_block is 0/1/2 */
		int descriptor_block = 1+(descriptor_index/4);
		int offset = descriptor_index%4;
		int start = (offset*16)+(4*(which_block+1));

		char[] new_array = new char[BLOCK];
		this.io.read_block(descriptor_block, new_array);

		int block_number = unpack(start, new_array);
		return block_number;
		//this is a blind read and may return 0
	}
	public void free_a_block(int descriptor_index, int which_block) {
		/** this function frees a block from the file descriptor and the bitmap
			which_block can be 0/1/2*/
		int descriptor_block = 1+(descriptor_index/4);
		int offset = descriptor_index%4;
		int start = (offset*16)+(4*(which_block+1));

		char[] new_array = new char[BLOCK];
		this.io.read_block(descriptor_block, new_array);

		int block_number = unpack(start, new_array);
		//can't free block 0
		if(block_number==0) {
			return;
		}
		set_bit(block_number,0); //free from bitmap

		for (int i = start; i< start+4; i++) {
			new_array[i] = '\u0000';
		}//free from descriptor

		this.io.write_block(descriptor_block,new_array);

	}

	public int item_in_dir(int which_block) { //block is 0/1/2
		int start = which_block*8;
		int end = (which_block==2)? start+7: start+8;
		int[] dir_block_array = Arrays.copyOfRange(container_directory,start, end);
		int length = 0;
		for(int el: dir_block_array) {
			if(el==1) {
				length++;
			}
		}
		return length;

	}//return how many items in that directory block

	public void remove_from_directory(int directory_index) {//remove,free,reduce len of dir
		int offset = directory_index%8;
		int directory_block_index = get_directory_block(directory_index/8);
		char[] new_array = new char[BLOCK];

		this.io.read_block(directory_block_index, new_array);

		//nullify \u0000
		for ( int i = offset*8; i< offset*8+8; i++) {
			new_array[i] = '\u0000';
		}

		this.io.write_block(directory_block_index, new_array);

		container_directory[directory_index] = 0;

		//get descriptor of directory, reduce 1 to length, write back
		char[] directory_descriptor_block = new char[BLOCK];
		this.io.read_block(1, directory_descriptor_block);

		int new_length = unpack(0, directory_descriptor_block) - 1;

		pack(new_length, 0, directory_descriptor_block);
		this.io.write_block(1, directory_descriptor_block);

	}//remove from directory

	public void remove_from_descriptor(int descriptor_index) {
		//remove & free
		int offset = descriptor_index%4;
		int descriptor_block_index = 1+ descriptor_index/4;
		char[] new_array = new char[BLOCK];

		this.io.read_block(descriptor_block_index, new_array);

		//nullify \u0000
		for ( int i = offset*16; i< offset*16+16; i++) {
			new_array[i] = '\u0000';
		}

		this.io.write_block(descriptor_block_index, new_array);

		container_descriptor[descriptor_index] = 0;
	}//remove from descriptor

	public void print_directory() {
		this.io.print_content();
		char[] new_array = new char[64];
		this.io.read_block(1, new_array);
		System.out.println("Directory details:");
		System.out.println("Length: "+unpack(0,new_array));
		System.out.println("Block 1: "+unpack(4,new_array));
		System.out.println("Block 2: "+unpack(8,new_array));
		System.out.println("Block 3: "+unpack(12,new_array));

	}

	public void list_directory() {
		for (int i = 0; i < 23; i++) {
			if(container_directory[i]==1) {
				System.out.print(get_symbolic_file_name(i));
				System.out.print(", "+ get_file_size(get_descriptor_index(i)) + " ");
			}
		}
		System.out.print("\n");

		//list length of each file
	}

	public void init(String file_name) {
		reset();

		//read txt to ldisk, does not initialize containers
		this.io.restore_ldisk(file_name);

		//reset containers
		for(int i = 0; i < 24;i++) {
			container_descriptor[i] = 0;
			container_directory[i] = 0;
		}
		container_descriptor[0] = 1; //for directory
		container_directory[23] = 1; //impossible

		//get directory descriptor from restored ldsik, find all blocks
		char[] new_array = new char[BLOCK];
		this.io.read_block(1, new_array);
		int block[] = new int[3];
		block[0] = (unpack(4,new_array)); 
		block[1] = (unpack(8,new_array));
		block[2] = (unpack(12,new_array));

		//in each block, unpack the desc no(4*i +4),if not 0, set container_dir = 1
		//set the container_descriptor to 1 as well
		char[] an_array = new char[BLOCK];
		for(int i = 0; i < 3; i++) {
			System.out.println("Block dir:" +block[i]);
			if(block[i] != 0) {
				this.io.read_block(block[i], an_array);
				for(int j = 0; j < 8; j++) { //8 possible file per dir block
					int descriptor_no = unpack(8*j +4,an_array);
					if(descriptor_no != 0) { //means descriptor exist
						int z = i*8 +j;
						
						container_directory[i*8 + j] = 1;
						System.out.println("Directory "+z +" set to 1");
						container_descriptor[descriptor_no] = 1;

					}//if
				}//for
			}//if
		}//for
		
		//reset oft table, container_oft (close all)
		if(is_dir_open()) {
			close(0,1);
		}
		for(int i = 0; i< 4; i++) {
			if(container_oft[i] ==1) {
				close(i, 0);
			}//if
		}//for
		System.out.println("Disk initialized");
		//open directory as oft 0
		char[] useless = new char[4];
		int oft_index = open(useless, 1);
		
		
	}

	public void save(String file_name) {
		this.io.print_ldisk(file_name);

		//close all files in oft including dir
		if(is_dir_open()) {
			//close directory in a different way
			close(0,1);
		}//if

		for(int i = 0; i < 4; i++) {
			if(container_oft[i] == 1) {
				close(i,0);
			}//if
		}//ofr
	}

	public void test() {
		char[] new_array = new char[64];
		this.io.read_block(1, new_array );
		System.out.println("Unpack: " + unpack(4, new_array));
		System.out.println("Unpack: " + unpack(8, new_array));
		System.out.println("Unpack: " + unpack(12, new_array));
		System.out.println("Unpack: " + unpack(20, new_array));
		System.out.println("Unpack: " + unpack(24, new_array));
		System.out.println("Unpack: " + unpack(28, new_array));
		System.out.println("Unpack: " + unpack(36, new_array));
		System.out.println("Unpack: " + unpack(40, new_array));
		System.out.println("Unpack: " + unpack(44, new_array));
		System.out.println("Unpack: " + unpack(52, new_array));
		System.out.println("Unpack: " + unpack(56, new_array));
		System.out.println("Unpack: " + unpack(60, new_array));
		

	}




}//file_system