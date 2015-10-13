/**
 * Created by lenovo on 2015/10/13.
 */
public class FileSender {
    public static void main(String[] args) {
        FileSenderThread fst = new FileSenderThread(args);

        Thread thread1 = new Thread(fst, "Init");
        thread1.start();
        try {
            thread1.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Thread thread2 = new Thread(fst, "Sender");
        thread2.start();
        Thread thread3 = new Thread(fst, "Receiver");
        thread3.start();
        try {
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
