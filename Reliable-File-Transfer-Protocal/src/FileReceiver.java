import javafx.util.Pair;

import java.io.FileOutputStream;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

public class FileReceiver {
	//total length of header: 24bytes
	//the long field
	private static long checksum;
	//the int field
	private static int ack_num;
	private static int sequence;
	private static int start_sequence;
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
	private static int end_sequence;

	//for buffering the received
	private static Map<Integer, Integer>window_buffer;//sequence/content-length
	private static byte[] windowbyte;
	private static ByteBuffer bb;

    //shared resources
	private static int PKT_SIZE ;
	private static int WD_SIZE ;
	private static byte[] reply;
	private static String filename;
	private static FileOutputStream fos;
	public static void main(String[] args) throws Exception 
	{
		window_buffer = new HashMap<Integer, Integer>();
		PKT_SIZE = 800;
		WD_SIZE = PKT_SIZE*5;
		end_sequence = -1;
		start_sequence = 0;
		ack_num = 0;
		if (args.length != 1) {
			System.err.println("Usage: FileReceiver <port>");
			System.exit(-1);
		}
		//initialization
		windowbyte = new byte[WD_SIZE];
		bb = ByteBuffer.wrap(windowbyte);
		int port = Integer.parseInt(args[0]);
		DatagramSocket sk = new DatagramSocket(port);
		byte[] data = new byte[PKT_SIZE+24];
		DatagramPacket pkt = new DatagramPacket(data, data.length);
		ByteBuffer b = ByteBuffer.wrap(data);
		CRC32 crc = new CRC32();
		while(true)
		{
			pkt.setLength(data.length);
			sk.receive(pkt);
			if (pkt.getLength() < 24)
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
			else {
				sequence = b.getInt();
				int tmp = b.getInt();
				int window_and_bits = b.getInt();
				end = (window_and_bits & 0x10000) >> 16;
				title = (window_and_bits & 0x20000) >> 17;
				fin = (window_and_bits & 0x40000) >> 18;
				syn = (window_and_bits & 0x80000) >> 19;
				//ack = (window_and_bits&0x100000)>>20;
				window = (window_and_bits & 0xffff);
				content_length = b.getInt();


				if (title == 1) {
					byte[] name = Arrays.copyOfRange(data, 24, content_length+24);
					System.out.println(new String(name));
					System.out.println("content-length"+content_length);
					filename = new String(name);
					fos = new FileOutputStream(filename);
					start_sequence = content_length;
					ack_num = content_length;
				} else {


					if(fos == null)continue;
					if (window_buffer.containsKey(sequence)) {//discard duplicate
						;
					} else if (end!=1&&(sequence == ack_num) && (sequence - start_sequence + content_length <= WD_SIZE)) {//put in sequentially
						byte[]newbyte = Arrays.copyOfRange(data, 24, content_length + 24);
						System.arraycopy(newbyte, 0, windowbyte, sequence - start_sequence, content_length);
						System.out.println("sequence"+sequence);
						System.out.println("ack_NUM"+ack_num);
						System.out.println("content_length"+content_length);
						System.out.println("sequence minus startsequence" + (sequence - start_sequence));
						System.out.println("WD_SIZE" + WD_SIZE);
						ack_num += content_length;
						while (window_buffer.containsKey(ack_num)) {
							ack_num += window_buffer.get(ack_num);
						}//if there are bytes occupied behind
						window_buffer.put(sequence, content_length);
						System.out.println("Case1" );
					} else if (end!=1&&(sequence == ack_num) &&( sequence - start_sequence + content_length > WD_SIZE)) {//output + put in sequentially + update ack and sequence and buffer
						System.out.println("there is an output");
						System.out.println("sequence"+sequence);
						System.out.println("ack_NUM"+ack_num);
						System.out.println("content_length"+content_length);
						System.out.println("sequence minus startsequence" + (sequence - start_sequence));
						System.out.println("WD_SIZE" + WD_SIZE);

						fos.write(windowbyte, 0, sequence - start_sequence);
						bb.clear();
						start_sequence = ack_num;
						ack_num += content_length;
						window_buffer.clear();
						window_buffer.put(sequence, content_length);

						byte[]newbyte = Arrays.copyOfRange(data, 24, content_length + 24);
						System.arraycopy(newbyte, 0, windowbyte, sequence - start_sequence, content_length);
						System.out.println("Case2" );
					} else if (end!=1&&(sequence != ack_num) && (sequence - start_sequence + content_length < WD_SIZE)) {//put in jump
						System.out.println("sequence"+sequence);
						System.out.println("ack_NUM"+ack_num);
						System.out.println("content_length"+content_length);
						System.out.println("sequence minus startsequence" + (sequence - start_sequence));
						System.out.println("WD_SIZE" + WD_SIZE);

						byte[]newbyte = Arrays.copyOfRange(data, 24, content_length + 24);
						System.arraycopy(newbyte, 0, windowbyte, sequence - start_sequence, content_length);

						window_buffer.put(sequence, content_length);
						System.out.println("Case3" );
					} else if (end!=1&&(sequence != ack_num) &&( sequence - start_sequence + content_length > WD_SIZE)) {//discard future packets
						System.out.println("sequence"+sequence);
						System.out.println("ack_NUM"+ack_num);
						System.out.println("content_length"+content_length);
						System.out.println("sequence minus startsequence" + (sequence - start_sequence));
						System.out.println("WD_SIZE" + WD_SIZE);
						System.out.println("Case4" );
					}
					if (end == 1) {
						end_sequence = sequence;
					}
					if (ack_num == end_sequence) {
						fos.write(windowbyte, 0, sequence - start_sequence);
						bb.clear();
						window_buffer.clear();
						fos.close();
						break;
					}
				}
			//	returnResponse(pkt, data, sk);
			}
			//endResponse(pkt, data, sk);
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
	public static void returnResponse(DatagramPacket pkt, byte[]data, DatagramSocket sk){
		ByteBuffer b = ByteBuffer.wrap(data);
		b.clear();
		b.putLong(0);
		b.putInt(sequence);//useless
		b.putInt(ack_num);
		int window_and_bitnumber = ack << 24 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);//useless
		b.putInt(window_and_bitnumber);
		b.putInt(0);
		CRC32 crc = new CRC32();
		crc.reset();
		crc.update(data, 8, data.length - 8);
		long chksum = crc.getValue();
		b.rewind();
		b.putLong(chksum);
		DatagramPacket ack = new DatagramPacket(data, data.length,				pkt.getSocketAddress());
		try{
		sk.send(ack);
		}
		catch (Exception e){
			System.out.print("cannot send response packet");
		}

	}
	public static void endResponse(DatagramPacket pkt, byte[]data, DatagramSocket sk){
		ByteBuffer b = ByteBuffer.wrap(data);
		b.clear();
		b.putLong(0);
		b.putInt(sequence);//useless
		b.putInt(ack_num);
		end = 1;
		int window_and_bitnumber = ack << 24 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);//useless
		b.putInt(window_and_bitnumber);
		b.putInt(0);
		CRC32 crc = new CRC32();
		crc.reset();
		crc.update(data, 8, data.length - 8);
		long chksum = crc.getValue();
		b.rewind();
		b.putLong(chksum);
		DatagramPacket ack = new DatagramPacket(data, data.length,				pkt.getSocketAddress());
		try{
			sk.send(ack);
		}
		catch (Exception e){
			System.out.print("cannot send end packet");
		}

	}

}
