import com.sun.javafx.geom.AreaOp;

import java.io.FileInputStream;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

public class FileSenderThread implements Runnable {
	public FileSenderThread(String args[]){
		this.args = args;
	}
	public void run(){
		try {
			if (Thread.currentThread().getName() == "Init")
				init();
			else if (Thread.currentThread().getName() == "Sender")
				Send();
			else if(Thread.currentThread().getName() == "Receiver"){
				Receive();
			}
		}
		catch (Exception e)
		{
			System.out.println("fail to run a thread");
		}
	}
    private static String[] args;
	//total length of header: 24bytes
	//the long field
	private static long checksum;
	//the int field
	private static int ack_num;
	private static int sequence;

	private static int content_length;
	//the short field
	private static short window;
	//the bit field
	private static short ack;
	private static short fin;
	private static short syn;
	private static short title;
	private static short end;
	//other necessary data structures for fast retransmission
    private static Map<Integer, DatagramPacket> map;
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
	private static short WD_SIZE;
	private static InetSocketAddress addr;
	private static byte[] file_array;
	private static byte[] data;
	private static ByteBuffer b;
	private static ByteBuffer bb;
	private static int end_program;
	public static void init()throws Exception{
		if (args.length != 4) {
			System.err.println("Usage: FileSender <host> <port> <src_file_name> <dest_file_name>");
			System.exit(-1);
		}
		end_program =0;//to be finished
		map = new HashMap<Integer, DatagramPacket>();
		mapoftimes = new HashMap<Integer, Integer>();
		addr = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		filename = args[2];
		dst_filename = args[3];
		fis = new FileInputStream(filename);
		System.out.println(fis==null);
		sk = new DatagramSocket();
		WD_SIZE = (short)(5*PKT_SIZE);
		PKT_SIZE = 800;
		data = new byte[PKT_SIZE+24];
		file_array = new byte[PKT_SIZE];
		b = ByteBuffer.wrap(data);
		bb = ByteBuffer.wrap(file_array);
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
	}
	public static void Send(){
		CRC32 crc = new CRC32();
		sequence = ack_num = 0;
		window = WD_SIZE;
		ack = fin = syn = end = 0;
		title = 1;
		int count = 0;
		try {
            SendFileName();

			while (true) {
				b.clear();
				bb.clear();

				System.out.println("file_array size:"+file_array.length+" PKT_SIZE: "+PKT_SIZE);
				if((content_length = fis.read(file_array, 0, PKT_SIZE)) != -1) {
					System.out.println("package sent:"+count);
					b.clear();
					// reserve space for checksum
					b.putLong(0);
					b.putInt(sequence);//sequence of the file excl. header
					b.putInt(0);
					int window_and_bitnumber = ack << 22 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);
					b.putInt(window_and_bitnumber);
					b.putInt(content_length);
					b.put(file_array,0,content_length);
					sequence += content_length;
					crc.reset();
					crc.update(data, 8, data.length - 8);
					long chksum = crc.getValue();
					b.rewind();
					b.putLong(chksum);

					pkt = new DatagramPacket(data, data.length, addr);
					// Debug output
					//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
					System.out.println("pktsize"+pkt.getLength());
					sk.send(pkt);

					System.out.println("sent"+count);
					map.put(sequence, pkt);//to be cleared once received acknum = sequence+1

					count++;

				}
				else{
					end = 1;
					b.clear();
					// reserve space for checksum
					b.putLong(0);
					b.putInt(sequence);//sequence of the file excl. header
					b.putInt(0);
					int window_and_bitnumber = ack << 20 | syn << 19 | fin << 18 | title << 17 | end << 16 | (window & 0xffff);
					b.putInt(window_and_bitnumber);
					b.putInt(content_length);
					sequence += 24 + content_length;
					crc.reset();
					crc.update(data, 8, data.length - 8);
					long chksum = crc.getValue();
					b.rewind();
					b.putLong(chksum);

					pkt = new DatagramPacket(data, data.length, addr);
					// Debug output
					//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
					sk.send(pkt);

					map.put(sequence, pkt);//to be cleared once received acknum = sequence+1
					count++;
					break;
				}
				//System.out.println("checksum:"+chksum);
				//System.out.println("packet:"+count);

			}
		}catch (Exception e){
			System.out.println("cannot send file");
		}
	}
	public static void SendFileName()throws Exception{
		CRC32 crc = new CRC32();
		content_length = dst_filename.length();
		b.putLong(0);
		b.putInt(sequence);//sequence of the file excl. header
		b.putInt(0);
		int window_and_bitnumber = ack<<22|syn<<19|fin<<18|title<<17|end<<16|(window&0xffff);
		b.putInt(window_and_bitnumber);
		b.putInt(content_length);
		sequence += content_length;
		b.put(dst_filename.getBytes());
		crc.reset();
		crc.update(data, 8, data.length - 8);
		long chksum = crc.getValue();
		b.rewind();
		b.putLong(chksum);
		System.out.println("title-content-length"+content_length);
		pkt = new DatagramPacket(data, data.length, addr);
		map.put(sequence, pkt);
		System.out.println("title-data-length"+data.length);
		// Debug output
		//System.out.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data));
		sk.send(pkt);
		System.out.println("title package prepared");
		title = 0;
	}
	public static void Receive(){//all parameters shouled be newed so that reduce confusion
		byte[] reply = new byte[100];
		ByteBuffer br = ByteBuffer.wrap(reply);
		DatagramPacket dp = new DatagramPacket(reply,reply.length, addr);
		try{
        while(true){
			sk.receive(dp);
			//private static Map<Integer, DatagramPacket> map;
			//private static Map<Integer, Integer> mapoftimes;
			int rsequence = br.getInt();
			int rtmp = br.getInt();
			int window_and_bits = br.getInt();
			int rend = (window_and_bits & 0x10000) >> 16;
			int rtitle = (window_and_bits & 0x20000) >> 17;
			int rfin = (window_and_bits & 0x40000) >> 18;
			int rsyn = (window_and_bits & 0x80000) >> 19;
			int rack = (window_and_bits&0x100000)>>20;
			int rwindow = (window_and_bits & 0xffff);
			int rcontent_length = br.getInt();//use end
			if (rend == 1){
				end_program = 1;
				return;
			}

			if(mapoftimes.containsKey(rack)){
				int times = mapoftimes.get(rack);
				if (times>= 3) {
					if(map.containsKey(rack))
						sk.send(map.get(rack));
				}
				else {
					mapoftimes.remove(rack);
					mapoftimes.put(rack, times+1);
				}
				//clear acked map and mapof times
				Iterator iter = mapoftimes.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry<Integer, Integer> entry = (Map.Entry) iter.next();
					int key = entry.getKey();
					if (key<rack){
						map.remove(key);
						mapoftimes.remove(key);
					}
				}
			}
			else{
				mapoftimes.put(rack,1);
			}


		}
		} catch (Exception e){
			System.out.println("cannot receive packet");
		}
	}
	public static void EndConnection(){

	}

}
