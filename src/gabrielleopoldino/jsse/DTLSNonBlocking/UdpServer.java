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



        public Client(SocketAddress address)
        {
            this.address = address;
            this.in = new LinkedBlockingQueue<>();
            this.sslEngine = UdpCommon.getContext().createSSLEngine();
            this.sslEngine.setNeedClientAuth(false);
            this.sslEngine.setUseClientMode(false);

            this.sslSession = this.sslEngine.getSession();

            this.buffers = new Buffers(this.sslSession.getApplicationBufferSize(), this.sslSession.getPacketBufferSize());
        }


        @Override
        public void run() {
            try {
                doHandshake();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Erro durante handshake");
                this.interrupt();
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

        public void send(byte data[]) throws SSLException {
            buffers.outAppData.clear();
            buffers.outAppData.put(data);
            buffers.outAppData.flip();

            buffers.outNetData.clear();

            while(buffers.outAppData.hasRemaining())
            {
                SSLEngineResult res = sslEngine.wrap(buffers.outAppData, buffers.outNetData);

                switch (res.getStatus())
                {
                    case OK:
                        byte[] outData = new byte[buffers.outNetData.remaining()];
                        System.arraycopy(buffers.outNetData.array(), 0, outData,0, buffers.outNetData.remaining());
                        UdpServer.this.send(this.address, outData);
                        break;
                }
            }
        }

        public byte[] receive() throws IOException, InterruptedException {
            buffers.inAppData.clear();
            buffers.inNetData.clear();
            SSLEngineResult res;

            byte inData[] = UdpServer.this.receive(this.address);

            if (inData.length > 0)
            {
                buffers.inNetData.put(inData);
                buffers.inNetData.flip();
                res = sslEngine.unwrap(buffers.inNetData, buffers.inAppData);
                buffers.inNetData.compact();
                switch (res.getStatus())
                {
                    case OK:
                        byte[] msg = new byte[res.bytesProduced()];
                        System.arraycopy(buffers.inAppData.array(), 0, msg, 0, res.bytesProduced());
                        return msg;
                    case CLOSED:
                        UdpCommon.whenSSLClosed();
                        break;
                    case BUFFER_OVERFLOW:
                        UdpCommon.whenBufferOverflow(sslEngine, buffers.inAppData);
                        break;
                    case BUFFER_UNDERFLOW:
                        UdpCommon.whenBufferUnderflow(sslEngine, buffers.inNetData);
                        break;
                }
            }
            return new byte[0];
        }

        public void doHandshake() throws IOException, InterruptedException {
            sslEngine.beginHandshake();
            SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();

            while (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
            {
                System.out.println(hs.name());
                switch (hs)
                {
                    case NEED_UNWRAP:
                        buffers.inNetData.clear();
                    case NEED_UNWRAP_AGAIN:
                        byte[] inData = UdpServer.this.receive(this.address);
                        buffers.inNetData.put(inData);
                        buffers.inNetData.flip();

                        SSLEngineResult res = sslEngine.unwrap(buffers.inNetData, buffers.inAppData);
                        buffers.inNetData.compact();
                        hs = res.getHandshakeStatus();

                        switch (res.getStatus())
                        {
                            case OK:
                                //q q eu faço???
                                break;
                            case CLOSED:
                                UdpCommon.whenSSLClosed();
                                break;
                            case BUFFER_OVERFLOW:
                                UdpCommon.whenBufferOverflow(sslEngine, buffers.inAppData);
                                break;
                            case BUFFER_UNDERFLOW:
                                UdpCommon.whenBufferUnderflow(sslEngine, buffers.inNetData);
                                break;
                        }
                        break;
                    case NEED_WRAP:
                        buffers.outNetData.clear();

                        res = sslEngine.wrap(buffers.outAppData, buffers.outNetData);
                        hs = res.getHandshakeStatus();

                        switch (res.getStatus())
                        {
                            case OK:
                                //buffers.outNetData.flip();
                                byte[] outData = new byte[buffers.outNetData.remaining()];
                                System.arraycopy(buffers.outNetData.array(), 0, outData,0, buffers.outNetData.remaining());
                                UdpServer.this.send(this.address, outData);
                                break;
                            case CLOSED:
                                UdpCommon.whenSSLClosed();
                                break;
                            case BUFFER_OVERFLOW:
                                UdpCommon.whenBufferOverflow(sslEngine, buffers.outAppData);
                                break;
                            case BUFFER_UNDERFLOW:
                                UdpCommon.whenBufferUnderflow(sslEngine, buffers.outNetData);
                                break;

                        }
                        break;
                    case NEED_TASK:
                        Runnable task;
                        while ((task = sslEngine.getDelegatedTask()) != null)
                        {
                            //new Thread(task).start();
                            task.run();
                        }
                        hs = sslEngine.getHandshakeStatus();
                        break;
                    default:
                        hs = sslEngine.getHandshakeStatus();
                        break;
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
