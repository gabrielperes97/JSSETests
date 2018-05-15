package gabrielleopoldino.jsse.DTLSNonBlocking;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UdpServer implements Runnable {

    private ByteBuffer inData;
    private ByteBuffer outData;
    private DatagramChannel channel;
    private Map<SocketAddress, Client> clients;
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
                    Client client = clients.get(addr);
                    if (client == null)
                    {
                        client = new Client(addr);
                        client.start();
                        clients.put(addr, client);
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
        return clients.get(address).getDataToReceive();
    }

    private class Client extends Thread
    {

        private SocketAddress address;
        private BlockingQueue<byte []> in;
        private SSLSession sslSession;
        private SSLEngine sslEngine;
        private Buffers buffers;
        private SSLManager sslManager;



        public Client(SocketAddress address) throws IOException {
            this.address = address;
            this.in = new LinkedBlockingQueue<>();
            this.sslEngine = UdpCommon.getContext().createSSLEngine();
            this.sslEngine.setNeedClientAuth(false);
            this.sslEngine.setUseClientMode(false);

            this.sslSession = this.sslEngine.getSession();

            this.buffers = new Buffers(this.sslSession.getApplicationBufferSize(), this.sslSession.getPacketBufferSize());

            SSLManager.IO io = new SSLManager.IO() {
                @Override
                public int write(ByteBuffer data) {
                    //data.flip();
                    byte[] outData = Arrays.copyOf(data.array(), data.position());
                    UdpServer.this.send(address, outData);
                    return outData.length;
                }

                @Override
                public int read(ByteBuffer data) {
                    try {
                        byte b[] = UdpServer.this.receive(address);
                        data.put(b);
                        data.flip();
                        return b.length;

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    return -1;
                }
            };
            this.sslManager = new SSLManager(io, sslEngine, buffers);
        }


        @Override
        public void run() {
            try {
                this.sslManager.doHandshake();
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (true)
            {
                try {
                    byte msg[];
                    String str;
                    if ((msg = receive()).length > 0)
                    {
                        str = new String(msg, StandardCharsets.UTF_8);
                        System.out.println("Echo: "+str);

                        send(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                    this.interrupt();
                }
            }
        }

        public byte[] getDataToReceive() throws InterruptedException {
            return in.take();
        }

        public void putDataToReceive(byte data[]) throws InterruptedException {
            in.put(data);
        }



        public void send(byte data[]) throws IOException {
            sslManager.send(data);
        }

        public byte[] receive() throws IOException {
            return sslManager.receive();
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
