import com.sun.javafx.geom.AreaOp;
import javafx.util.Pair;

import java.io.FileOutputStream;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;

/**
 * Created by lenovo on 2015/10/15.
 */
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
    private static Map<Integer, Integer> window_buffer;//sequence/content-length
    private static byte[] window;
    private static ByteBuffer b_window;

    //Ending
    private static int end_sequence;//change reply_status = 2 when reply_ack_num == end_sequence

    //shared resources
    private static int PKT_SIZE;
    private static int WD_SIZE;
    private static byte[] reply;
    private static ByteBuffer b_reply;
    private static String filename;
    private static FileOutputStream fos;
    private static DatagramSocket sk;
    private static byte[] data;
    private static ByteBuffer b_data;
    private static DatagramPacket pkt;
    private static DatagramPacket reply_pkt;
    private static CRC32 crc;

    public static void main(String[] args) {
        try {
            Init(args);
            Start();
            Continue();
            End();
        } catch (Exception e) {
            System.out.println("Exception in main!");
        } finally {
            System.out.println("After sending 10000 finish pkt!");
        }
    }

    public static void Init(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: FileReceiver <port>");
            System.exit(-1);
        }
        PKT_SIZE = 800;
        WD_SIZE = 5 * 800;
        int port = Integer.parseInt(args[0]);
        data = new byte[PKT_SIZE + 20];
        b_data = ByteBuffer.wrap(data);
        //shared resources
        crc = new CRC32();
        reply_ack_num = 0;
        reply_status = 0;//if is 2 then start ending process
        //Buffer
        start_sequence = 0;//maximum offset  = sequence + content_length - start_sequence
        window_buffer = new HashMap<Integer, Integer>();//sequence/content-length
        window = new byte[WD_SIZE];
        b_window = ByteBuffer.wrap(window);
        //Ending
        end_sequence = -1;//change reply_status = 2 when reply_ack_num == end_sequence
        reply = new byte[16];
        b_reply = ByteBuffer.wrap(reply);
        try {
            sk = new DatagramSocket(port);
        } catch (Exception e) {
            System.out.println("Exception opening datagramsocket!");
        }
    }

    public static void Start() {
        pkt = new DatagramPacket(data, data.length);
        while (reply_status == 0) {
            try {
                sk.receive(pkt);
            } catch (Exception e) {
               // System.out.println("Starter time out!");
                continue;
            }
            if (pkt.getLength() < 20) {
                System.out.println("Pkt too short");
                continue;
            }
            crc.reset();
            crc.update(data, 8, data.length - 8);
            checksum = crc.getValue();
            long chksum2 = b_data.getLong();
            if (checksum == chksum2) {
                sequence = b_data.getInt();//this is for sequence, leave me alone
                status = b_data.getInt();
                content_length = b_data.getInt();
                byte[] name = Arrays.copyOfRange(data, 20, content_length + 20);
                filename = new String(name);
                try {
                    fos = new FileOutputStream(filename);
                    System.out.println("time"+ System.currentTimeMillis());
                } catch (Exception e) {
                    System.out.println("Exception Opening file!");
                }
                start_sequence = content_length;
                reply_ack_num = content_length;
                reply_status = 1;
                Response();
            }
        }
    }
    public static void Continue() {
        try {
            while (reply_ack_num != end_sequence) {
                pkt.setLength(data.length);
                b_data.clear();

                sk.receive(pkt);
                if (pkt.getLength() < 20) {
                    System.out.println("Pkt too short");
                    continue;
                }
                b_data.rewind();
                checksum = b_data.getLong();
                crc.reset();
                crc.update(data, 8, pkt.getLength() - 8);
                if (crc.getValue() != checksum) {
                    System.out.println("Pkt corrupt");
                } else {
                    sequence = b_data.getInt();
                    status = b_data.getInt();
                    content_length = b_data.getInt();
                    if (status == 2) {
                        end_sequence = sequence + content_length;
                    } else {
                        if (reply_ack_num > sequence || window_buffer.containsKey(sequence)) {
                            System.out.println("Case0: Discard duplicate");
                            ;//discard duplicate
                        } else if ((sequence == reply_ack_num) && (sequence - start_sequence + content_length <= WD_SIZE)) {
                            reply_ack_num += content_length;
                            System.arraycopy(data, 20, window, sequence - start_sequence, content_length);
                            while (window_buffer.containsKey(reply_ack_num)) {
                                reply_ack_num += window_buffer.get(reply_ack_num);
                            }
                            window_buffer.put(sequence, content_length);
                            System.out.println("Case1");
                        } else if ((sequence == reply_ack_num) && (sequence - start_sequence + content_length > WD_SIZE)) {
                            fos.write(window, 0, sequence - start_sequence);
                            start_sequence = reply_ack_num;
                            reply_ack_num += content_length;
                            b_window.clear();
                            window_buffer.clear();
                            System.arraycopy(data, 20, window, sequence - start_sequence, content_length);
                            window_buffer.put(sequence, content_length);
                            System.out.println("Case2");
                        } else if ((sequence != reply_ack_num) && (sequence - start_sequence + content_length <= WD_SIZE)) {
                            window_buffer.put(sequence, content_length);
                            System.arraycopy(data, 20, window, sequence - start_sequence, content_length);
                            System.out.println("Case3");
                        } else {
                            System.out.println("Case4");
                        }
                    }
                }
                Response();
            }
            reply_status = 2;
            fos.write(window, 0, reply_ack_num - start_sequence);
            fos.close();
            System.out.println("time"+ System.currentTimeMillis());
        }
        catch (Exception e) {
            System.out.println("Exception receiving file!");
        }
    }


    private static void Response(){
        b_reply.clear();
        b_reply.putLong(0);
        b_reply.putInt(reply_ack_num);//useless
        System.out.println("the returned ack = " + reply_ack_num);
        b_reply.putInt(reply_status);
        crc.reset();
        crc.update(reply, 8, reply.length - 8);
        long chksum = crc.getValue();
        b_reply.rewind();
        b_reply.putLong(chksum);
        reply_pkt = new DatagramPacket(reply, reply.length, pkt.getSocketAddress());
        try {
            sk.send(reply_pkt);
            System.out.println("response packet: "+reply_ack_num);
        } catch (Exception e) {
            System.out.println("cannot send response packet");
        }
    }
    public static void End()
    {
        Response();
        int counter = 100;
        while(counter!=0){
            try {
                counter--;
                sk.send(reply_pkt);
                System.out.println("response packet: "+reply_ack_num);
            } catch (Exception e) {
                System.out.println("cannot send response packet");
            }

        }
    }
}







