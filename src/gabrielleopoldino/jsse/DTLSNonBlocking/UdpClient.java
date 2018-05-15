package gabrielleopoldino.jsse.DTLSNonBlocking;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UdpClient {

    private DatagramChannel channel;
    private Buffers buffers;
    private SSLEngine sslEngine;
    private SSLSession sslSession;

    public UdpClient() throws IOException {
        this.channel = DatagramChannel.open();
        this.channel.connect(new InetSocketAddress(UdpCommon.HOST, UdpCommon.PORT));
        this.channel.configureBlocking(false);

        this.sslEngine = UdpCommon.getContext().createSSLEngine("localhost", UdpCommon.PORT);
        this.sslEngine.setUseClientMode(true);
        this.sslEngine.setNeedClientAuth(false);

        this.sslSession = this.sslEngine.getSession();
        this.buffers = new Buffers(this.sslSession.getApplicationBufferSize(), this.sslSession.getPacketBufferSize());

        doHandshake();
        System.out.println("Autenticado");
        this.channel.configureBlocking(true);
    }

    public void send(byte[] data) throws IOException {
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
                    buffers.outNetData.flip();
                    while (buffers.outNetData.hasRemaining())
                    {
                        if (channel.write(buffers.outNetData) < 0)
                        {
                            UdpCommon.whenSocketClosed();
                        }
                    }
                    break;
            }
        }

    }

    public byte[] receive() throws IOException {
        buffers.inAppData.clear();
        buffers.inNetData.clear();
        SSLEngineResult res;
        int tam = channel.read(buffers.inNetData);
        if (tam < 0)
        {
            UdpCommon.whenSocketClosed();
        } else if (tam > 0)
        {
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

    public static void main (String args[])
    {
        try{
            UdpClient client = new UdpClient();
            System.out.println("Connected");

            Scanner s = new Scanner(System.in);

            while (true)
            {
                String msg = s.nextLine();
                client.send(msg.getBytes(StandardCharsets.UTF_8));
                System.out.println("Sending: "+ msg);

                msg = new String(client.receive(), StandardCharsets.UTF_8);
                System.out.println("Received: "+ msg);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doHandshake() throws IOException {
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
                    if (this.channel.read(buffers.inNetData) < 0)
                    {
                        UdpCommon.whenSocketClosed();
                    }
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
                            buffers.outNetData.flip();
                            while (buffers.outNetData.hasRemaining())
                            {
                                if (this.channel.write(buffers.outNetData) < 0)
                                {
                                    UdpCommon.whenSocketClosed();
                                }
                            }
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
