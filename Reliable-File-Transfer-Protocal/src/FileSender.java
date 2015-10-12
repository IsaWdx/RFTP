import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.*;
import java.security.spec.ECField;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

public class FileSender {

	//total length of header: 22bytes
	//the long field
	private static long checksum;
	//the int field
	private static int ack_num;
	private static int sequence;

	private static int content_length;
	//the short field
	private static short window;
	private static Set<Integer> receive_ack_num;
	//the bit field
	private static short ack;
	private static short fin;
	private static short syn;
	private static short title;
	private static short end;
	//other necessary data structures for fast retransmission
    private static Map<Integer, byte[]> map;
	private static Set<Integer> ack_set;
	//status of the sender
	private static int status;//0: sent syn 1: receive syn 2: sent ack and get connected 4: sent fin 5: receive fin
	//shared resources
	private static String filename;
	private static FileInputStream fis;
	private static DatagramSocket sk;
	private static DatagramPacket pkt;
	private static short PKT_SIZE;
	private static short WD_SIZE;
	private static InetSocketAddress addr;
	private static byte[] file_array;
	private static byte[] data;
	private static ByteBuffer b;
	public static void main(String[] args) throws Exception 
	{
		if (args.length != 3) {
			System.err.println("Usage: FileSender <host> <port> <file_name>");
			System.exit(-1);
		}
		addr = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		filename = args[2];
		fis = new FileInputStream(filename);
		sk = new DatagramSocket();
		WD_SIZE = (short)(5*PKT_SIZE);
		PKT_SIZE = 978;
		data = new byte[PKT_SIZE+22];
		file_array = new byte[PKT_SIZE];
		b = ByteBuffer.wrap(data);
		status = 0;

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
        status = 2;
	}
	public static void SendFile(){
		CRC32 crc = new CRC32();

		//some initialization of header
		sequence = ack_num = 0;
		window = WD_SIZE;
		receive_ack_num.clear();
		ack = fin = syn = end = 0;
		title = 1;
		int count = 0;
		try {
			content_length = filename.length();
			b.putLong(0);
			b.putInt(sequence);//sequence of the file excl. header
			b.putInt(0);
			int window_and_bitnumber = ack<<22|syn<<19|fin<<18|title<<17|end<<16|(window&0xffff);
			b.putInt(window_and_bitnumber);
			b.putInt(content_length);
			sequence += content_length;
			file_array = filename.getBytes();
			b.put(file_array);
			crc.reset();
			crc.update(data, 8, data.length - 8);
			long chksum = crc.getValue();
			b.rewind();
			b.putLong(chksum);

			pkt = new DatagramPacket(data, data.length, addr);
			// Debug output
			//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
			sk.send(pkt);
			title = 0;
			sequence += 22+filename.length();
			while (true) {
				if((content_length = fis.read(file_array, 0, PKT_SIZE)) != -1) {
					b.clear();
					// reserve space for checksum
					b.putLong(0);
					b.putInt(sequence);//sequence of the file excl. header
					b.putInt(0);
					window_and_bitnumber = ack << 22 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);
					b.putInt(window_and_bitnumber);
					b.putInt(content_length);
					sequence += content_length;
					crc.reset();
					crc.update(data, 8, data.length - 8);
					chksum = crc.getValue();
					b.rewind();
					b.putLong(chksum);

					pkt = new DatagramPacket(data, data.length, addr);
					// Debug output
					//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
					sk.send(pkt);
					map.put(sequence, data);//to be cleared once received acknum = sequence+1
					count++;
				}
				else{
					end = 1;
					b.clear();
					// reserve space for checksum
					b.putLong(0);
					b.putInt(sequence);//sequence of the file excl. header
					b.putInt(0);
					window_and_bitnumber = ack << 20 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);
					b.putInt(window_and_bitnumber);
					b.putInt(content_length);
					sequence += 22 + content_length;
					crc.reset();
					crc.update(data, 8, data.length - 8);
					chksum = crc.getValue();
					b.rewind();
					b.putLong(chksum);

					pkt = new DatagramPacket(data, data.length, addr);
					// Debug output
					//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
					sk.send(pkt);
					map.put(sequence, data);//to be cleared once received acknum = sequence+1
					count++;
				}
				System.out.println("checksum:"+chksum);
				System.out.println("packet:"+count);
			}
		}catch (Exception e){
			System.out.println("cannot send file");
		}
	}
	public static void EndConnection(){

	}

}
