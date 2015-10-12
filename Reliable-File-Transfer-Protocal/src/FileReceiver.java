import javafx.util.Pair;

import java.io.FileOutputStream;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

public class FileReceiver {
	//total length of header: 22bytes
	//the long field
	private static long checksum;
	//the int field
	private static int ack_num;
	private static int sequence;
	//the short field
	private static short length;
	private static int window;
	public static int content_length;
	private static Set<Integer> receive_ack_num;
	//the bit field
	private static int ack;
	private static int fin;
	private static int syn;
	private static int title ;
	private static int end;

	//for buffering the received
	private static Map<Integer, Integer>emptyarea;
	private static byte[] windowbyte;
	private static ByteBuffer bb;

    //shared resources
	private static int PKT_SIZE = 980;
	private static int WD_SIZE = PKT_SIZE*5;
	private static byte[] reply;
	private static String filename;
	private static FileOutputStream fos;
	public static void main(String[] args) throws Exception 
	{
		emptyarea.add(new Pair<Integer,Integer>(0,WD_SIZE));
		if (args.length != 1) {
			System.err.println("Usage: FileReceiver <port>");
			System.exit(-1);
		}
		//initialization
		windowbyte = new byte[WD_SIZE];
		bb = ByteBuffer.wrap(windowbyte);
		int port = Integer.parseInt(args[0]);
		DatagramSocket sk = new DatagramSocket(port);
		byte[] data = new byte[1500];
		DatagramPacket pkt = new DatagramPacket(data, data.length);
		ByteBuffer b = ByteBuffer.wrap(data);
		CRC32 crc = new CRC32();
		while(true)
		{
			pkt.setLength(data.length);
			sk.receive(pkt);
			if (pkt.getLength() < 8)
			{
				System.out.println("Pkt too short");
				continue;
			}
			b.rewind();
			long chksum = b.getLong();
			crc.reset();
			crc.update(data, 8, pkt.getLength()-8);
			// Debug output
			// System.out.println("Received CRC:" + crc.getValue() + " Data:" + bytesToHex(data, pkt.getLength()));
			if (crc.getValue() != chksum)
			{
				System.out.println("Pkt corrupt");
			}
			else
			{
				sequence = b.getInt();
				ack_num = b.getInt();
				int window_and_bits = b.getInt();
				end = (window_and_bits&0x10000)>>16;
				title = (window_and_bits&0x20000)>>17;
				fin = (window_and_bits&0x40000)>>18;
				syn = (window_and_bits&0x80000)>>19;
				ack = (window_and_bits&0x100000)>>20;
				window = (window_and_bits&0xffff);
                content_length = b.getInt();
				if(title == 1){
					byte[] name = Arrays.copyOfRange(data, 22, content_length);
					filename = name.toString();
					fos = new FileOutputStream(filename);
				}
				else {
					returnResponse(DatagramPacket pkt);
				}
				DatagramPacket ack = new DatagramPacket(new byte[0], 0, 0,
						pkt.getSocketAddress());
				sk.send(ack);
			}	
		}
	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes, int len) {
	    char[] hexChars = new char[len * 2];
	    for ( int j = 0; j < len; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	public static void returnResponse(DatagramPacket pkt, byte[]data){
		

	}

}
