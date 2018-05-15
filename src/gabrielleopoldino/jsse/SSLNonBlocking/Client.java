package gabrielleopoldino.jsse.SSLNonBlocking;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Client{

    private List pendingChanges = new LinkedList();
    private SocketChannel socketChannel;


    private SSLEngine sslEngine;
    private ByteBuffer outAppData;
    private ByteBuffer outNetData;
    private ByteBuffer inAppData;
    private ByteBuffer inNetData;
    private SSLSession sslSession;


    public Client() throws IOException {

        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", EchoServer.PORT));
        socketChannel.configureBlocking(false);


        this.sslEngine = EchoServer.getContext().createSSLEngine("localhost", EchoServer.PORT);
        this.sslEngine.setUseClientMode(true);
        this.sslEngine.setNeedClientAuth(false);

        this.sslSession = this.sslEngine.getSession();
        this.outAppData = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
        this.inAppData = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
        this.outNetData = ByteBuffer.allocate(sslSession.getPacketBufferSize());
        this.inNetData = ByteBuffer.allocate(sslSession.getPacketBufferSize());

        EchoServer.doHandshake(sslEngine, socketChannel, inAppData, inNetData, outAppData, outNetData);
        socketChannel.configureBlocking(true);
    }

    public void send (byte outputData[]) throws IOException {
       /* outAppData.clear();
        outAppData.put(outputData);
        outAppData.flip();

        while(outAppData.hasRemaining())
        {
            outNetData.clear();
            SSLEngineResult res = sslEngine.wrap(outAppData, outNetData);
            switch (res.getStatus()) {
                case OK:
                    outAppData.compact();

                    while (outNetData.hasRemaining()) {
                        int num = socketChannel.write(outNetData);
                        if (num == -1) {
                            EchoServer.whenSocketClosed();
                        }
                    }
                    break;
                case CLOSED:
                    EchoServer.whenSSLClosed();
                    break;
                case BUFFER_OVERFLOW:
                    EchoServer.whenBufferOverflow(sslEngine, outAppData);
                    break;
                case BUFFER_UNDERFLOW:
                    EchoServer.whenBufferUnderflow(sslEngine, outNetData);
                    break;
            }
        }
        throw new IOException("Não deu :/");*/
        outAppData.clear();
        outAppData.put(outputData);
        EchoServer.send(outAppData, outNetData, sslEngine, socketChannel);
    }

    public byte[] read() throws IOException {
        /*SSLEngineResult res;
        int tam = socketChannel.read(inNetData);
        if (tam == -1)
        {
            EchoServer.whenSocketClosed();
        } else if (tam > 0)
        {
            inNetData.flip();
            res = sslEngine.unwrap(inNetData, inAppData);
            switch (res.getStatus())
            {
                case OK:
                    byte[] msg = new byte[res.bytesProduced()];
                    System.arraycopy(inAppData.array(), 0, msg, 0, res.bytesProduced());
                    return msg;
                case CLOSED:
                    EchoServer.whenSSLClosed();
                    break;
                case BUFFER_OVERFLOW:
                    EchoServer.whenBufferOverflow(sslEngine, inAppData);
                    break;
                case BUFFER_UNDERFLOW:
                    EchoServer.whenBufferUnderflow(sslEngine, inNetData);
                    break;
            }
        }
        throw new IOException("Não deu :/");*/
        return EchoServer.read(inAppData, inNetData, sslEngine, socketChannel);
    }

    public void close() throws IOException {
        sslEngine.closeOutbound();
        while (!sslEngine.isOutboundDone())
        {
            outAppData.clear();
            SSLEngineResult res = sslEngine.wrap(outAppData, outNetData);

            switch (res.getStatus())
            {
                case OK:
                    break;
                case CLOSED:
                    EchoServer.whenSSLClosed();
                    break;
                case BUFFER_OVERFLOW:
                    EchoServer.whenBufferOverflow(sslEngine, outAppData);
                    break;
                case BUFFER_UNDERFLOW:
                    EchoServer.whenBufferUnderflow(sslEngine, outNetData);
                    break;
            }

            while (outNetData.hasRemaining())
            {
                int num = socketChannel.write(outNetData);
                if (num == -1)
                {
                    EchoServer.whenSocketClosed();
                }
                outNetData.compact();
            }
        }
        socketChannel.close();
    }

    public static void main(String args[]) {

        try{
            Client client = new Client();
            System.out.println("Connected");

            Scanner s = new Scanner(System.in);

            while (true)
            {
                String msg = s.nextLine();
                client.send(msg.getBytes(StandardCharsets.UTF_8));
                System.out.println("Sending: "+ msg);

                msg = new String(client.read(), StandardCharsets.UTF_8);
                System.out.println("Received: "+ msg);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //JSSE
    /*
    private void whenSocketClosed()
    {

    }

    private void whenSSLClosed()
    {

    }

    private void whenBufferOverflow()
    {
        if (this.sslEngine.getSession().getApplicationBufferSize() > inAppData.capacity())
        {
            // enlarge the peer application data buffer
        }
        else
        {
            //compact or clear the buffer
        }
        //retry the operation
    }

    private void whenBufferUnderflow()
    {
        if(sslEngine.getSession().getPacketBufferSize() > inNetData.capacity())
        {
            //enlarge the peer network packet buffer
        }
        else
        {
            //compact or clear the buffer
        }
        //obtain more inboud network data and the retry the operation
    }

    private void doHandshake() throws IOException {
        sslEngine.beginHandshake();
        SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();

        while (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
        {
            switch (hs)
            {
                case NEED_UNWRAP:
                    if (socketChannel.read(inNetData) < 0)
                    {
                        EchoServer.whenSocketClosed();
                    }

                    inNetData.flip();
                    SSLEngineResult res = sslEngine.unwrap(inNetData, inAppData);
                    inNetData.compact();
                    hs = res.getHandshakeStatus();

                    switch (res.getStatus())
                    {
                        case OK:
                            //q q eu faço???
                            break;
                        case CLOSED:
                            EchoServer.whenSSLClosed();
                            break;
                        case BUFFER_OVERFLOW:
                            EchoServer.whenBufferOverflow(sslEngine, inAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            EchoServer.whenBufferUnderflow(sslEngine, inNetData);
                            break;
                    }
                    break;
                case NEED_WRAP:
                    outNetData.clear();

                    res = sslEngine.wrap(outAppData, outNetData);
                    hs = res.getHandshakeStatus();

                    switch (res.getStatus())
                    {
                        case OK:
                            outNetData.flip();
                            while (outNetData.hasRemaining())
                            {
                                if (socketChannel.write(outNetData) < 0)
                                {
                                    EchoServer.whenSocketClosed();
                                }
                            }
                            break;
                        case CLOSED:
                            EchoServer.whenSSLClosed();
                            break;
                        case BUFFER_OVERFLOW:
                            EchoServer.whenBufferOverflow(sslEngine, outAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            EchoServer.whenBufferUnderflow(sslEngine, outNetData);
                            break;

                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null)
                    {
                        new Thread(task).start();
                    }
                    break;
            }
        }
    }*/

}