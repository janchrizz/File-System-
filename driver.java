import java.util.*;
import java.lang.*;
import java.io.*;

class Driver {
    public static void main(String[] args) {

        File_System fs = new File_System();
    	Scanner reader = new Scanner(System.in);
        BufferedReader fin = null;
        PrintStream originalOut = System.out;

        System.out.print("Enter type: ");

        int type = reader.nextInt();
        reader.nextLine();

        if(type == 2) {
            System.out.print("Enter filename: ");
            String test_file = reader.next();

            try {
                fin = new BufferedReader(new FileReader(test_file));
            } 
            catch(IOException e) {
                System.out.print(e);
            }

            System.out.print("Enter output filename: ");
            String out_file = reader.next();

           

            try {
                PrintStream fout = new PrintStream(out_file);
                System.setOut(fout);
                
            } 
            catch(IOException e) {
                System.out.print(e);
            }
        }
        
        int status = 1;
        while(status==1) {

            String str = "";

            if(type == 2 ) {

                try {
                    if((str=fin.readLine()) != null) {
                        str = str;
                    }
                    else {
                        break;
                    }
                } 
                catch(IOException e)  {
                    System.out.print(e);
                }

            }//if read from txt
            else {

                System.out.print("Enter Command:"); 
                str = reader.nextLine();

            }//read user input

            String[] tokens = str.split(" ");

            switch(tokens[0]) {
                case "cr":
                    if (tokens.length != 2) {
                        System.out.println("Invalid arguments for Create");
                        break;
                    }
                    fs.create(tokens[1].toCharArray());
                    break;
                case "de":
                    if (tokens.length != 2) {
                        System.out.println("Invalid arguments for Destroy");
                        break;
                    }
                    fs.destroy(tokens[1].toCharArray());
                    break;
                case "op":
                    if (tokens.length == 2) {
                        int oft_index = fs.open(tokens[1].toCharArray(),0);
                        System.out.println((oft_index==-1)?"": "OFT " + oft_index + " allocated");
                        break;
                    }
                    int oft_index = fs.open(tokens[1].toCharArray(),1);
                    System.out.println((oft_index==-1)?"Fail": "Directory opened");
                    break;

                case "cl":
                    if (tokens.length != 2) {
                        System.out.println("Invalid arguments for Close");
                        break;
                    }
                    fs.close(Integer.parseInt(tokens[1]), 0);
                    break;
                case "rd":
                    if (tokens.length != 3) {
                        System.out.println("Invalid arguments for Read");
                        break;
                    }
                    int count = Integer.parseInt(tokens[2]);
                    char[] read_array = new char[count];
                    fs.read(Integer.parseInt(tokens[1]), read_array, count);
                    break;
                case "wr":
                    if (tokens.length != 4) {
                        System.out.println("Invalid arguments for Write");
                        break;
                    }
                    int counter = Integer.parseInt(tokens[3]);
                    char[] write_char = tokens[2].toCharArray();
                    fs.write(Integer.parseInt(tokens[1]), write_char[0], counter);
                    break;
                case "sk":
                    if (tokens.length != 3) {
                        System.out.println("Invalid arguments for Seek");
                        break;
                    }
                    fs.lseek(Integer.parseInt(tokens[1]),Integer.parseInt(tokens[2]));
                    break;
                case "dr":
                    if (tokens.length != 1) {
                        System.out.println("Invalid arguments for Directory");
                        break;
                    }
                    fs.list_directory();
                    break;
                case "in":
                    if (tokens.length > 2) {
                        System.out.println("Invalid arguments for Initialize");
                        break;
                    }
                    if(tokens.length == 2) {
                        fs.init(tokens[1]);
                        break;
                    }
                    else {
                        fs.reset();
                        break;
                    }
                    
                case "sv":
                    if (tokens.length != 2) {
                        System.out.println("Invalid arguments for Save");
                        break;
                    }
                    fs.save(tokens[1]);
                    break;
                case "pr":
                    fs.print_directory();
                    fs.test();
                    break;
                case "ts":
                    fs.test();
                    break;
                case "exit":
                    status = 0;
                    break;
                default:
                    System.out.println("Not a valid command");
                  
            }//switch
        }//while
        System.setOut(originalOut);

	
    }
}