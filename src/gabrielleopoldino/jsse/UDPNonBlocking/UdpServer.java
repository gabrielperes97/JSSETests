package gabrielleopoldino.jsse.UDPNonBlocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UdpServer implements Runnable {

    private ByteBuffer inData;
    private ByteBuffer outData;
    private DatagramChannel channel;
    private Map<SocketAddress, ClientReceivedData> clients;
    private Queue<ClientSendData> sendQueue;


    public UdpServer() throws IOException {
        this.inData = ByteBuffer.allocate(65535);
        this.outData = ByteBuffer.allocate(65535);
        this.channel = DatagramChannel.open();
        this.channel.bind(new InetSocketAddress(UdpCommon.PORT));
        this.channel.configureBlocking(false);

        clients = new HashMap<>();
        sendQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void run() {
        while (true)
        {
            try {
                inData.clear();
                SocketAddress addr = channel.receive(inData);
                if (addr != null)
                {
                    ClientReceivedData client = clients.get(addr);
                    if (client == null)
                    {
                        client = new ClientReceivedData(addr);
                        clients.put(addr, client);
                        new Client(addr).start();
                        System.out.println("Novo cliente registrado");
                    }
                    inData.flip();
                    byte b[] = new byte[inData.remaining()];
                    System.arraycopy(inData.array(), 0, b, 0, inData.remaining());
                    client.putDataToReceive(b);
                }
                ClientSendData dataToSend = sendQueue.poll();
                if (dataToSend != null)
                {
                    outData.clear();
                    outData.put(dataToSend.getData());
                    outData.flip();
                    channel.send(outData, dataToSend.getAddr());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void send(SocketAddress address, byte[] message)
    {
        sendQueue.offer(new ClientSendData(address, message));
    }

    public byte[] receive(SocketAddress address) throws InterruptedException {
        return clients.get(address).getDatatoReceive();
    }

    private class Client extends Thread
    {

        private SocketAddress address;

        public Client(SocketAddress address)
        {
            this.address = address;
        }


        @Override
        public void run() {
            while (true)
            {
                try {
                    byte msg[];
                    String str;
                    if ((msg = receive(this.address)).length > 0)
                    {
                        str = new String(msg, StandardCharsets.UTF_8);
                        System.out.println("Echo: "+str);

                        send(address, msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                    this.interrupt();
                }
            }
        }
    }

    public static void main(String args[])
    {
        try {
            new Thread(new UdpServer()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




}
