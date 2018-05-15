package gabrielleopoldino.jsse.TCPNonBlocking;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

public class EchoWorker implements Runnable {
    private List queue = new LinkedList();

    public void processData (EchoServer server, SocketChannel socket, byte[] data, int count)
    {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        synchronized (queue){
            queue.add(new ServerDataEvent(server, socket, dataCopy));
            queue.notify();
        }
    }

    @Override
    public void run() {
        ServerDataEvent dataEvent;
        while (true)
        {
            synchronized (queue){
                while (queue.isEmpty())
                {
                    try{
                        queue.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                dataEvent = (ServerDataEvent) queue.remove(0);
            }
            dataEvent.server.send(dataEvent.socket, dataEvent.data);
        }
    }
}