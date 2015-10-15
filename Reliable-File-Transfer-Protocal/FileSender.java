import com.sun.javafx.geom.AreaOp;

import javax.xml.crypto.Data;
import java.io.FileInputStream;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

public class FileSender {
    private static String[] args;
	//total length of header: 20bytes
	//the long field
	private static long checksum;
	//the int field
	private static int ack_num;
	private static int sequence;
	private static int received_ack;
	private static int content_length;
	//the short field
	//the bit field
	private static short ack;
	private static short fin;
	private static short syn;
	private static short title;
	private static short end;

	//other necessary data structures for fast retransmission
    private static Map<Integer, byte[]> map;
	private static Map<Integer, Integer> mapoftimes;
	//status of the sender
	private static int status;//0: sent syn 1: receive syn 2: sent ack and get connected 4: sent fin 5: receive fin
	//shared resources
	private static String filename;
	private static String dst_filename;
	private static FileInputStream fis;
	private static DatagramSocket sk;
	private static DatagramPacket pkt;
	private static short PKT_SIZE;
	private static InetSocketAddress addr;
	private static byte[] file_array;
	private static byte[] data;
	private static ByteBuffer b;
	private static ByteBuffer bb;
	private static int end_program;

    private static int end_sending;


	public static void main(String[] args) throws Exception{
		if (args.length != 4) {
			System.err.println("Usage: FileSender <host> <port> <src_file_name> <dest_file_name>");
			System.exit(-1);
		}
		end_program =0;//to be finished
		map = new HashMap<Integer, byte[]>();
		mapoftimes = new HashMap<Integer, Integer>();
		int port = Integer.parseInt(args[1]);
		addr = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		filename = args[2];
		dst_filename = args[3];
		fis = new FileInputStream(filename);
		sk = new DatagramSocket();
		PKT_SIZE = 800;
		data = new byte[PKT_SIZE+24];
		file_array = new byte[PKT_SIZE];
		b = ByteBuffer.wrap(data);
		bb = ByteBuffer.wrap(file_array);
		status = 0;
		SendFileName();
        end_sending = 0;
		while(end_program!=1) {
            if(end_sending == 0)
			    Send();
			Receive();
            System.out.println("end_program: "+end_program);
		}

        System.out.println("end_program: "+end_program);


	}


	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	public static void StartConnection(){
	}
	public static void Send(){
		CRC32 crc = new CRC32();
		int count = 0;
		try {


			while (count<10&&end_program!=1) {
				b.clear();
				bb.clear();
				//System.out.println("file_array size:"+file_array.length+" PKT_SIZE: "+PKT_SIZE);
				if((content_length = fis.read(file_array, 0, PKT_SIZE)) != -1) {
					//System.out.println("package sent:"+count);
					b.clear();
					// reserve space for checksum
					b.putLong(0);
					b.putInt(sequence);//sequence of the file excl. header
					b.putInt(0);
					int window_and_bitnumber = ack << 22 | syn << 19 | fin << 18 | title << 17 | end << 16 ;
					b.putInt(window_and_bitnumber);
					b.putInt(content_length);
					b.put(file_array,0,content_length);
					System.out.println(bytesToHex(file_array));
					crc.reset();
					crc.update(data, 8, data.length - 8);
					long chksum = crc.getValue();
					b.rewind();
					b.putLong(chksum);
					byte[]mapbyte = data.clone();
					b.rewind();
					b.getLong();
					System.out.println("actual sequence: "+b.getInt()+" the number in byte: "+sequence);
					map.put(sequence, mapbyte);
					System.out.println("Sequence: "+sequence);
					pkt = new DatagramPacket(data, data.length, addr);
					sk.send(pkt);
					sequence += content_length;
					count++;
				}
				else{
                    end_sending = 1;
					content_length = 0;
					end = 1;
					b.clear();
					b.putLong(0);
					b.putInt(sequence);
					b.putInt(0);
					int window_and_bitnumber = ack << 20 | syn << 19 | fin << 18 | title << 17 | end << 16;
					b.putInt(window_and_bitnumber);
					b.putInt(content_length);
					crc.reset();
					crc.update(data, 8, data.length - 8);
					long chksum = crc.getValue();
					b.rewind();
					b.putLong(chksum);
					System.out.println();
					byte[]mapbyte = data.clone();
					b.rewind();
					b.getLong();
					System.out.println("actual sequence: "+b.getInt()+" the number in byte: "+sequence);
					map.put(sequence, mapbyte);
					sequence +=  content_length;
					System.out.println("end sequence: "+sequence);
					pkt = new DatagramPacket(data, data.length, addr);
					sk.send(pkt);
					count++;
					break;
				}


			}
		}catch (Exception e){
			System.out.println("cannot send file");
		}
	}
	public static void SendFileName()throws Exception{
		sequence = ack_num = 0;
		ack = fin = syn = end = 0;
		title = 1;
		CRC32 crc = new CRC32();
		content_length = dst_filename.length();
		b.putLong(0);
		b.putInt(sequence);//sequence of the file excl. header
		b.putInt(0);
		int window_and_bitnumber = ack<<22|syn<<19|fin<<18|title<<17|end<<16;
		b.putInt(window_and_bitnumber);
		b.putInt(content_length);
		b.put(dst_filename.getBytes());

		long chksum = crc.getValue();
		b.rewind();
		b.putLong(chksum);
		//System.out.println("title-content-length"+content_length);
		pkt = new DatagramPacket(data, data.length, addr);
		b.rewind();
		b.getLong();
		System.out.println("actual sequence: "+b.getInt()+" the number in byte: "+sequence);
		byte[]mapbyte = data.clone();
		map.put(sequence, mapbyte);

		//System.out.println("title-data-length"+data.length);
		// Debug output
		//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
		sk.send(pkt);
		//System.out.println("title package prepared");
		title = 0;
		sequence += content_length;
	}
	public static void Receive() {//all parameters shouled be newed so that reduce confusion
		int rack = received_ack;
        try {
			int count = 0;
			sk.setSoTimeout(3);
			byte[] reply = new byte[100];
			ByteBuffer br = ByteBuffer.wrap(reply);
			CRC32 crc = new CRC32();
			DatagramPacket dp = new DatagramPacket(reply, reply.length);
			try {
				while (count<11) {
					count++;
					br.rewind();
					sk.receive(dp);
					crc.reset();
					crc.update(reply, 8, reply.length - 8);
					long chksum1 = crc.getValue();
					long chksum2 = br.getLong();
					if (chksum1 == chksum2) {
                        rack = br.getInt();//this is for sequence, leave me alone
						rack = br.getInt();
						received_ack = rack;
						int window_and_bits = br.getInt();
						int rend = (window_and_bits & 0x10000) >> 16;
						if (rend == 1) {
							end_program = 1;
							return;
						}
						System.out.println("ack: " + rack);
						if (mapoftimes.containsKey(rack)) {
							int times = mapoftimes.get(rack);
							System.out.println("times = " + times);
							if (times >= 3) {
								if (map.containsKey(rack)) {
									byte[] redata = map.get(rack);
									//System.out.println(bytesToHex(redata));
									DatagramPacket repkt = new DatagramPacket(redata, redata.length, addr);
									sk.send(repkt);
								} else
									System.out.println("cannot re-find the packet");
							} else {
								mapoftimes.remove(rack, times);
								mapoftimes.put(rack, times + 1);
							}
						} else {
							mapoftimes.put(rack, 1);
						}
					}
				}
			} catch (Exception e) {
				System.out.println("cannot receive packet");
			}
		} catch (Exception e){
            if (map.containsKey(rack)) {
                byte[] redata = map.get(rack);
                //System.out.println(bytesToHex(redata));
                DatagramPacket repkt = new DatagramPacket(redata, redata.length, addr);
                try{sk.send(repkt);}catch (Exception ex){System.out.println("resend fail");}
            } else
                System.out.println("cannot re-find the packet");
			System.out.println("time out"+ received_ack);
		}
	}
	public static void EndConnection(){

	}

}
