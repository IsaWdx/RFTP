import javafx.util.Pair;

import java.io.FileOutputStream;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

public class FileReceiver {
	//Total length of header: 20bytes
	private static long checksum;
	private static int sequence;
	private static int status;
	private static int content_length;

	//Receiver reply: 16bytes
	private static long reply_checksum;
	private static int reply_ack_num;
	private static int reply_status;//if is 2 then start ending process

	//Buffer
	private static int start_sequence;//maximum offset  = sequence + content_length - start_sequence
	private static Map<Integer, Integer>window_buffer;//sequence/content-length
	private static byte[] window;
	private static ByteBuffer b_window;

	//Ending
	private static int end_sequence;//change reply_status = 2 when reply_ack_num == end_sequence

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
		WD_SIZE = PKT_SIZE*1024;
		end_sequence = 0;
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
		while(true) {

			pkt.setLength(data.length);
			sk.receive(pkt);
			if (pkt.getLength() < 24) {
				System.out.println("Pkt too short");
				continue;
			}
			b.rewind();
			long chksum = b.getLong();
			crc.reset();
			crc.update(data, 8, pkt.getLength() - 8);
			// Debug output
			// System.out.println("Received CRC:" + crc.getValue() + " Data:" + bytesToHex(data, pkt.getLength()));
			if (crc.getValue() != chksum) {
				System.out.println("Pkt corrupt");
			} else {
				sequence = b.getInt();
				int tmp = b.getInt();
				int window_and_bits = b.getInt();
				end = (window_and_bits & 0x10000) >> 16;
				title = (window_and_bits & 0x20000) >> 17;
				content_length = b.getInt();


				if (title == 1) {
					byte[] name = Arrays.copyOfRange(data, 24, content_length + 24);
					System.out.println(new String(name));
					System.out.println("content-length" + content_length);
					filename = new String(name);
					fos = new FileOutputStream(filename);
					start_sequence = content_length;
					ack_num = content_length;
				} else {

					System.out.println("received sequence" + sequence);
					System.out.println("current ack = " + ack_num);
					//System.out.println("sequence minus start sequence" + (sequence - start_sequence));
					if (fos == null) continue;
					if (window_buffer.containsKey(sequence)||ack_num>sequence) {//discard duplicate
						;
					} else if (end != 1 && (sequence == ack_num) && (sequence - start_sequence + content_length <= WD_SIZE)) {//put in sequentially
						byte[] newbyte = Arrays.copyOfRange(data, 24, content_length + 24);
						ack_num += content_length;
						while (window_buffer.containsKey(ack_num)) {
							ack_num += window_buffer.get(ack_num);
						}//if there are bytes occupied behind
						window_buffer.put(sequence, content_length);
						System.out.println(bytesToHex(newbyte,newbyte.length));
						System.out.println("Case1");
					} else if (end != 1 && (sequence == ack_num) && (sequence - start_sequence + content_length > WD_SIZE)) {//output + put in sequentially + update ack and sequence and buffer
						fos.write(windowbyte, 0, sequence - start_sequence);
						bb.clear();
						start_sequence = ack_num;
						ack_num += content_length;
						window_buffer.clear();
						window_buffer.put(sequence, content_length);
						byte[] newbyte = Arrays.copyOfRange(data, 24, content_length + 24);
						System.arraycopy(newbyte, 0, windowbyte, sequence - start_sequence, content_length);
						System.out.println(bytesToHex(newbyte,newbyte.length));
						System.out.println("Case2");
					} else if (end != 1 && (sequence != ack_num) && (sequence - start_sequence + content_length < WD_SIZE)) {//put in jump
						byte[] newbyte = Arrays.copyOfRange(data, 24, content_length + 24);
						System.arraycopy(newbyte, 0, windowbyte, sequence - start_sequence, content_length);
						window_buffer.put(sequence, content_length);
						System.out.println("Case3");
					} else if (end != 1 && (sequence != ack_num) && (sequence - start_sequence + content_length > WD_SIZE)) {//discard future packets
						System.out.println("Case4");
					}
					if (end == 1) {
						end_sequence = sequence;
					}
					if (ack_num == end_sequence) {
						fos.write(windowbyte, 0, sequence - start_sequence);
						bb.clear();
						window_buffer.clear();
						fos.close();
						endResponse(pkt,  sk);
						break;
					}
				}

			}

			returnResponse(pkt,  sk);
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
	public static void returnResponse(DatagramPacket pkt,  DatagramSocket sk) {
		reply = new byte[100];
		ByteBuffer b = ByteBuffer.wrap(reply);
		b.clear();
		b.putLong(0);
		b.putInt(sequence);//useless
		System.out.println("the returned ack = " + ack_num);
		b.putInt(ack_num);
		int window_and_bitnumber = ack << 24 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);//useless
		b.putInt(window_and_bitnumber);
		b.putInt(0);
		CRC32 crc = new CRC32();
		crc.reset();
		crc.update(reply, 8, reply.length - 8);
		long chksum = crc.getValue();
		b.rewind();
		b.putLong(chksum);
		DatagramPacket ack = new DatagramPacket(reply, reply.length, pkt.getSocketAddress());
		try {
			sk.send(ack);
			System.out.println("sent ack packet");
		} catch (Exception e) {
			System.out.println("cannot send response packet");
		}

	}
	public static void endResponse(DatagramPacket pkt,  DatagramSocket sk) {
		reply = new byte[100];
		ByteBuffer b = ByteBuffer.wrap(reply);
		b.clear();
		b.putLong(0);
		b.putInt(sequence);
		b.putInt(ack_num);
		end = 1;
		int window_and_bitnumber = ack << 24 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);//useless
		b.putInt(window_and_bitnumber);
		b.putInt(0);
		CRC32 crc = new CRC32();
		crc.reset();
		crc.update(reply, 8, reply.length - 8);
		long chksum = crc.getValue();
		b.rewind();
		b.putLong(chksum);

		DatagramPacket ack = new DatagramPacket(reply, reply.length, pkt.getSocketAddress());
		try {
			while(true) {
				sk.send(ack);
			}
		} catch (Exception e) {
			System.out.println("cannot send response packet");
		}

	}

}
