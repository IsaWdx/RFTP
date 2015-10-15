/**
 * Created by lenovo on 2015/10/15.
 */
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
    //Total length of header: 20bytes
    private static long checksum;
    private static int sequence;
    private static int status;
    private static int content_length;
    //Received packets
    private static long received_checksum;
    private static int received_ack;
    private static int received_status;
    //Retransmission
    private static Map<Integer, byte[]> map;
    private static Map<Integer, Integer> times;
    //Shared Rescources
    private static String filename;
    private static String dst_filename;
    private static FileInputStream fis;
    private static DatagramSocket sk;
    private static DatagramPacket pkt;
    private static DatagramPacket received_pkt;
    private static short PKT_SIZE;
    private static InetSocketAddress addr;
    private static byte[] file_array;//exc. header
    private static byte[] data;//inc. header
    private static byte[] reply;
    private static ByteBuffer b_file_array;//exc. header
    private static ByteBuffer b_data;//inc. header
    private static ByteBuffer b_reply;
    private static int end_program;
    private static int end_sending;
    private static int count_round;
    private static CRC32 crc;

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: FileSender <host> <port> <src_file_name> <dest_file_name>");
            System.exit(-1);
        }
        Init(args);
        try {
            Start();
            if (end_program == 0)
                Continue();
        } catch (Exception e) {
            System.out.println("Exception in main!");
        } finally {
            System.out.println("Done transmission!");
        }
    }
    public static void Init(String[] args){
        checksum = 0;
        sequence = 0;
        status = 0;
        content_length = 0;
        map = new HashMap<Integer, byte[]>();
        times = new HashMap<Integer, Integer>();
        filename = args[2];
        dst_filename = args[3];
        PKT_SIZE = 800;
        data = new byte[PKT_SIZE + 20];
        file_array = new byte[PKT_SIZE];
        reply = new byte[16];
        b_data = ByteBuffer.wrap(data);
        b_file_array = ByteBuffer.wrap(file_array);
        b_reply = ByteBuffer.wrap(reply);
        addr = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        end_program = 0;
        end_sending = 0;
        count_round = 1;//corresponding to window size
        crc = new CRC32();
        try{

            fis = new FileInputStream(filename);
            sk = new DatagramSocket();
        }
        catch (Exception e){
            System.out.println("Exception opening file or datagramsocket!");
        }
    }
    public static void Start(){
        try {
            content_length = dst_filename.length();
            b_data.putLong(0);
            b_data.putInt(sequence);
            b_data.putInt(status);
            b_data.putInt(content_length);
            b_data.put(dst_filename.getBytes());
            crc.reset();
            crc.update(data, 8, data.length - 8);
            b_data.rewind();
            b_data.putLong(crc.getValue());
            //byte[]mapbyte = data.clone();
            //map.put(sequence, mapbyte);
            sequence += content_length;
            pkt = new DatagramPacket(data, data.length, addr);
            received_pkt = new DatagramPacket(reply, reply.length);
            while (status == 0) {
                try {
                    sk.send(pkt);
                    sk.setSoTimeout(1);
                    sk.receive(received_pkt);
                } catch (Exception e) {
                    System.out.println("Starter time out!");
                    continue;
                }
                if (received_pkt.getLength() < 16) {
                    System.out.println("Pkt too short");
                    continue;
                }
                crc.reset();
                crc.update(reply, 8, reply.length - 8);
                long chksum1 = crc.getValue();
                received_checksum = b_reply.getLong();
                if (chksum1 == received_checksum) {
                    received_checksum = b_reply.getInt();//this is for sequence, leave me alone
                    received_ack = b_reply.getInt();
                    received_status = b_reply.getInt();
                    if (received_status == 1) {
                        status = 1;
                    }
                }

            }
        }
        catch (Exception e){
            System.out.println("Exception starting connection!");
        }
        System.out.println("Connection started!");
    }
    public static void Continue(){
        while(end_program == 0) {

            if(received_ack + 4000>sequence&&end_sending == 0 ) {
                    Send();
            }
            else
            Receive();

        }
    }
    public static void Send() {
        int count = 0;
        try {
            while (count < count_round) {
                b_data.clear();
                b_file_array.clear();
                if ((content_length = fis.read(file_array, 0, PKT_SIZE)) != -1) {
                    System.out.println("package sent: " + count + " sequence: " + sequence);
                    // reserve space for checksum
                    b_data.putLong(0);
                    b_data.putInt(sequence);//sequence of the file excl. header

                    System.out.print("data sequence: " + sequence);
                    b_data.putInt(1);
                    b_data.putInt(content_length);
                    b_data.put(file_array, 0, content_length);
                    //System.out.println(bytesToHex(file_array));
                    crc.reset();
                    crc.update(data, 8, data.length - 8);
                    checksum = crc.getValue();
                    b_data.rewind();
                    b_data.putLong(checksum);
                    byte[] mapbyte = data.clone();
                    map.put(sequence, mapbyte);
                    System.out.println("map sequence: "+sequence);
                    pkt = new DatagramPacket(data, data.length, addr);
                    sk.send(pkt);
                    sequence += content_length;
                    count++;
                } else {
                    end_sending = 1;
                    content_length = 0;
                    status = 2;
                    b_data.putLong(0);
                    b_data.putInt(sequence);//sequence of the file excl. header
                    b_data.putInt(status);
                    b_data.putInt(content_length);
                    b_data.put(file_array, 0, content_length);
                    //System.out.println(bytesToHex(file_array));
                    crc.reset();
                    crc.update(data, 8, data.length - 8);
                    checksum = crc.getValue();
                    b_data.rewind();
                    b_data.putLong(checksum);
                    byte[] mapbyte = data.clone();
                    map.put(sequence, mapbyte);
                    pkt = new DatagramPacket(data, data.length, addr);
                    sk.send(pkt);
                    System.out.println("End sequence: " + sequence);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Exception sendint file");
        }
    }
    public static void Receive() {//all parameters shouled be newed so that reduce confusion
        int count = 0;
        received_pkt = new DatagramPacket(reply, reply.length);
        try {
            while (count < count_round) {
                b_reply.clear();
                count++;
                try {
                    sk.setSoTimeout(2);
                    sk.receive(received_pkt);
                } catch (Exception e) {//time out 重新发包
                    System.out.println("cannot receive packet");
                    if (map.containsKey(received_ack)) {
                        byte[] newdata = map.get(received_ack);
                        //System.out.println(bytesToHex(redata));
                        DatagramPacket repkt = new DatagramPacket(newdata, newdata.length, addr);
                        sk.send(repkt);
                    } else
                        System.out.println("cannot re-find the packet");
                }
                if (received_pkt.getLength() < 16) {
                    System.out.println("Pkt too short");
                    continue;
                }
                crc.reset();
                crc.update(reply, 8, reply.length - 8);
                received_checksum = crc.getValue();
                long chksum2 = b_reply.getLong();
                if (received_checksum == chksum2) {
                    received_ack = b_reply.getInt();
                    received_status = b_reply.getInt();
                    System.out.println(received_status);
                    if (received_status == 2) {
                        status = 2;
                        end_program = 1;
                        return;
                    }
                    System.out.println("Received ack: " + received_ack);
                    if (times.containsKey(received_ack)) {
                        int time = times.get(received_ack);
                        System.out.println("times = " + time);
                        if (time >= 3) {
                            if (map.containsKey(received_ack)) {
                                byte[] newdata = map.get(received_ack);
                                //System.out.println(bytesToHex(redata));
                                DatagramPacket repkt = new DatagramPacket(newdata, newdata.length, addr);
                                sk.send(repkt);
                            } else
                                System.out.println("cannot re-find the packet");
                        } else {
                            times.remove(received_ack, time);
                            times.put(received_ack, time + 1);
                        }
                    } else {
                        times.put(received_ack, 1);
                   }
                }
            }
        }
        catch (Exception e) {
            //if (map.containsKey(rack)) {
            //    byte[] redata = map.get(rack);
            //    //System.out.println(bytesToHex(redata));
            //    DatagramPacket repkt = new DatagramPacket(redata, redata.length, addr);
            //    try{sk.send(repkt);}catch (Exception ex){System.out.println("resend fail");}
            //} else
            //    System.out.println("cannot re-find the packet");
            System.out.println("Exception sending/resending!");
        }
    }


}

