package gabrielleopoldino.jsse.DTLSNonBlocking;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;

public class UdpCommon {

    public static final String HOST = "localhost";
    public static final int PORT = 6523;

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

            context = SSLContext.getInstance("DTLSv1.2");

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
        int netSize = sslEngine.getSession().getPacketBufferSize();
        if(sslEngine.getSession().getPacketBufferSize() > netData.capacity())
        {
            //enlarge the peer network packet buffer
            ByteBuffer b = ByteBuffer.allocate(netSize);
            netData.flip();
            b.put(netData);
            netData = b;
            System.out.println("When Buffer Underflow");
        }
        else
        {
            //compact or clear the buffer

        }
        //obtain more inboud network data and the retry the operation
    }





}
