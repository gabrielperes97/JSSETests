package gabrielleopoldino.jsse.UDPNonBlocking;

import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientReceivedData {

    private SocketAddress address;
    private BlockingQueue<byte []> in;


    public ClientReceivedData(SocketAddress socketAddress)
    {
        this.address = socketAddress;
        this.in = new LinkedBlockingQueue<>();
    }

    public SocketAddress getAddress() {
        return address;
    }

    public byte[] getDatatoReceive() throws InterruptedException {
        return in.take();
    }

    public void putDataToReceive(byte data[]) throws InterruptedException {
        in.put(data);
    }
}
