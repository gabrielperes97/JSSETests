package gabrielleopoldino.jsse.SSLNonBlocking;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;

public class EchoServer implements Runnable {

    public static final int PORT = 6600;
    private ServerSocketChannel serverChannel;
    private List threads = new LinkedList();

    public EchoServer() throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(PORT));

    }


    @Override
    public void run() {
        while (true)
        {
            try {
                SocketChannel channel = serverChannel.accept();
                System.out.println("Client Connected");
                Thread t = new EchoWorker(channel);
                System.out.println("Client authenticated");
                t.start();
                threads.add(t);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void send (ByteBuffer outAppData, ByteBuffer outNetData, SSLEngine sslEngine, SocketChannel socketChannel) throws IOException {
        outAppData.flip();

        outNetData.clear();

        while(outAppData.hasRemaining())
        {
            SSLEngineResult res = sslEngine.wrap(outAppData, outNetData);

            switch (res.getStatus())
            {
                case OK:
                    outNetData.flip();
                    while (outNetData.hasRemaining())
                    {
                        if (socketChannel.write(outNetData) < 0)
                        {
                            whenSocketClosed();
                        }
                    }

            }
        }

    }

    public static byte[] read(ByteBuffer inAppData, ByteBuffer inNetData, SSLEngine sslEngine, SocketChannel socketChannel) throws IOException {
        inAppData.clear();
        inNetData.clear();
        SSLEngineResult res;
        int tam = socketChannel.read(inNetData);
        if (tam < 0)
        {
            whenSocketClosed();
        } else if (tam > 0)
        {
            inNetData.flip();
            res = sslEngine.unwrap(inNetData, inAppData);
            inNetData.compact();
            switch (res.getStatus())
            {
                case OK:
                    byte[] msg = new byte[res.bytesProduced()];
                    System.arraycopy(inAppData.array(), 0, msg, 0, res.bytesProduced());
                    return msg;
                case CLOSED:
                    whenSSLClosed();
                    break;
                case BUFFER_OVERFLOW:
                    whenBufferOverflow(sslEngine, inAppData);
                    break;
                case BUFFER_UNDERFLOW:
                    whenBufferUnderflow(sslEngine, inNetData);
                    break;
            }
        }
        return new byte[0];
    }

    public static void main (String args[])
    {
        try {
            new Thread(new EchoServer()).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //JSSE
    public static SSLContext getContext()
    {
        SSLContext context = null;
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream("foobar"), "foobar".toCharArray());

            KeyManagerFactory keyFact = KeyManagerFactory.getInstance("SunX509");
            keyFact.init(keyStore, "foobar".toCharArray());

            TrustManagerFactory trustFact = TrustManagerFactory.getInstance("SunX509");
            trustFact.init(keyStore);

            context = SSLContext.getInstance("TLSv1.2");

            context.init(keyFact.getKeyManagers(), trustFact.getTrustManagers(), null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return context;
    }

    public static void whenSocketClosed()
    {
        System.out.println("When socket Closed");
        System.exit(1);
    }

    public static void whenSSLClosed()
    {
        System.out.println("When SSL closed");
    }

    public static void whenBufferOverflow(SSLEngine sslEngine, ByteBuffer appData)
    {
        if (sslEngine.getSession().getApplicationBufferSize() > appData.capacity())
        {
            // enlarge the peer application data buffer
            System.out.println("WhenBufferOverflow");
        }
        else
        {
            //compact or clear the buffer
        }
        //retry the operation
    }

    public static void whenBufferUnderflow(SSLEngine sslEngine, ByteBuffer netData)
    {

        if(sslEngine.getSession().getPacketBufferSize() > netData.capacity())
        {
            //enlarge the peer network packet buffer
            System.out.println("When Buffer Underflow");
        }
        else
        {
            //compact or clear the buffer

        }
        //obtain more inboud network data and the retry the operation
    }

    public static void doHandshake(SSLEngine sslEngine, SocketChannel socketChannel, ByteBuffer inAppData, ByteBuffer inNetData, ByteBuffer outAppData, ByteBuffer outNetData) throws IOException {
        sslEngine.beginHandshake();
        SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();

        while (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
        {
            switch (hs)
            {
                case NEED_UNWRAP:
                    if (socketChannel.read(inNetData) < 0)
                    {
                        whenSocketClosed();
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
                            whenSSLClosed();
                            break;
                        case BUFFER_OVERFLOW:
                            whenBufferOverflow(sslEngine, inAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            whenBufferUnderflow(sslEngine, inNetData);
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
                                    whenSocketClosed();
                                }
                            }
                            break;
                        case CLOSED:
                            whenSSLClosed();
                            break;
                        case BUFFER_OVERFLOW:
                            whenBufferOverflow(sslEngine, outAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            whenBufferUnderflow(sslEngine, outNetData);
                            break;

                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null)
                    {
                        new Thread(task).start();
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
